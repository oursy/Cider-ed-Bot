package com.cider.bot.config.w3j.websocket;

import com.cider.bot.config.w3j.websocket.client.WebSocketNettyClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.BehaviorSubject;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSubscribe;
import org.web3j.protocol.core.methods.response.EthUnsubscribe;
import org.web3j.protocol.websocket.WebSocketSubscription;
import org.web3j.protocol.websocket.events.Notification;

@Slf4j
public class NettyWebSocketService implements Web3jService {

  static final long REQUEST_TIMEOUT = 15;

  static final AtomicLong nextBatchId = new AtomicLong(0);

  private final WebSocketNettyClient webSocketClient;

  private final ScheduledExecutorService executor;

  private final ObjectMapper objectMapper;

  private final WebSocketRequestProcess webSocketRequestProcess;

  private static final WebSocketRequestProcess REQUEST_PROCESS =
      new DefaultWebSocketRequestProcess();

  private static class DefaultWebSocketRequestProcess implements WebSocketRequestProcess {

    // Map of a sent request id to objects necessary to process this request
    private final Map<Long, WebSocketRequest<?>> requestForId = new ConcurrentHashMap<>();
    // Map of a sent subscription request id to objects necessary to process
    // subscription events
    private final Map<Long, WebSocketSubscription<?>> subscriptionRequestForId =
        new ConcurrentHashMap<>();
    // Map of a subscription id to objects necessary to process incoming events
    private final Map<String, WebSocketSubscription<?>> subscriptionForId =
        new ConcurrentHashMap<>();

    @Override
    public Map<Long, WebSocketRequest<?>> requestForId() {
      return requestForId;
    }

    @Override
    public Map<Long, WebSocketSubscription<?>> subscriptionRequestForId() {
      return subscriptionRequestForId;
    }

    @Override
    public Map<String, WebSocketSubscription<?>> subscriptionForId() {
      return subscriptionForId;
    }
  }

  public NettyWebSocketService(String serverUrl) throws Exception {
    this(serverUrl, ObjectMapperFactory.getObjectMapper(false));
  }

  public NettyWebSocketService(String serverUrl, ObjectMapper objectMapper) throws Exception {
    this(
        serverUrl,
        objectMapper,
        REQUEST_PROCESS,
        new DefaultReceivedMessage(objectMapper, REQUEST_PROCESS));
  }

  public NettyWebSocketService(
      String serverUrl, ObjectMapper objectMapper, WebSocketRequestProcess webSocketRequestProcess)
      throws Exception {
    this(
        serverUrl,
        objectMapper,
        webSocketRequestProcess,
        new DefaultReceivedMessage(objectMapper, webSocketRequestProcess));
  }

  public NettyWebSocketService(
      String serverUrl,
      ObjectMapper objectMapper,
      WebSocketRequestProcess webSocketRequestProcess,
      ReceivedMessage receivedMessage)
      throws Exception {
    this.objectMapper = objectMapper;
    this.webSocketClient = new WebSocketNettyClient(serverUrl, receivedMessage);
    this.executor = new ScheduledThreadPoolExecutor(1, Thread.ofVirtual().factory());
    this.webSocketRequestProcess = webSocketRequestProcess;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public <T extends Response> T send(Request request, Class<T> responseType) throws IOException {
    try {
      return sendAsync(request, responseType).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted WebSocket request", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new RuntimeException("Unexpected exception", e.getCause());
    }
  }

  @SuppressWarnings("rawtypes")
  @Override
  public <T extends Response> CompletableFuture<T> sendAsync(
      Request request, Class<T> responseType) {
    CompletableFuture<T> result = new CompletableFuture<>();
    long requestId = request.getId();
    webSocketRequestProcess
        .requestForId()
        .put(requestId, new WebSocketRequest<>(result, responseType));
    try {
      sendRequest(request, requestId);
    } catch (IOException e) {
      closeRequest(requestId, e);
    }
    return result;
  }

  @SuppressWarnings("rawtypes")
  private void sendRequest(Request request, long requestId) throws JsonProcessingException {
    String payload = objectMapper.writeValueAsString(request);
    log.debug("Sending request: {}", payload);
    webSocketClient.sendMessage(payload);
    setRequestTimeout(requestId);
  }

  private void setRequestTimeout(long requestId) {
    executor.schedule(
        () ->
            closeRequest(
                requestId,
                new IOException(String.format("Request with id %d timed out", requestId))),
        REQUEST_TIMEOUT,
        TimeUnit.SECONDS);
  }

  void closeRequest(long requestId, Exception e) {
    CompletableFuture<?> result =
        webSocketRequestProcess.requestForId().get(requestId).getOnReply();
    webSocketRequestProcess.requestForId().remove(requestId);
    result.completeExceptionally(e);
  }

  @Override
  public BatchResponse sendBatch(BatchRequest batchRequest) throws IOException {
    try {
      return sendBatchAsync(batchRequest).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted WebSocket batch requests", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }

      throw new RuntimeException("Unexpected exception", e.getCause());
    }
  }

  @Override
  public CompletableFuture<BatchResponse> sendBatchAsync(BatchRequest requests) {
    CompletableFuture<BatchResponse> result = new CompletableFuture<>();

    // replace first batch elements's id to handle response
    long requestId = nextBatchId.getAndIncrement();
    Request<?, ? extends Response<?>> firstRequest = requests.getRequests().get(0);
    long originId = firstRequest.getId();
    requests.getRequests().get(0).setId(requestId);
    webSocketRequestProcess
        .requestForId()
        .put(requestId, new WebSocketRequests(result, requests.getRequests(), originId));

    try {
      sendBatchRequest(requests, requestId);
    } catch (IOException e) {
      closeRequest(requestId, e);
    }

    return result;
  }

  private void sendBatchRequest(BatchRequest request, long requestId)
      throws JsonProcessingException {
    String payload = objectMapper.writeValueAsString(request.getRequests());
    log.debug("Sending batch request: {}", payload);
    webSocketClient.sendMessage(payload);
    setRequestTimeout(requestId);
  }

  @Override
  public <T extends Notification<?>> Flowable<T> subscribe(
      Request request, String unsubscribeMethod, Class<T> responseType) {
    // We can't use usual Observer since we can call "onError"
    // before first client is subscribed and we need to
    // preserve it
    BehaviorSubject<T> subject = BehaviorSubject.create();

    // We need to subscribe synchronously, since if we return
    // an Flowable to a client before we got a reply
    // a client can unsubscribe before we know a subscription
    // id and this can cause a race condition
    subscribeToEventsStream(request, subject, responseType);
    return subject
        .doOnDispose(() -> closeSubscription(subject, unsubscribeMethod))
        .toFlowable(BackpressureStrategy.BUFFER);
  }

  private <T extends Notification<?>> String getSubscriptionId(BehaviorSubject<T> subject) {
    return webSocketRequestProcess.subscriptionForId().entrySet().stream()
        .filter(entry -> entry.getValue().getSubject() == subject)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  private <T extends Notification<?>> void closeSubscription(
      BehaviorSubject<T> subject, String unsubscribeMethod) {
    String subscriptionId = getSubscriptionId(subject);
    if (subscriptionId != null) {
      webSocketRequestProcess.subscriptionForId().remove(subscriptionId);
      unsubscribeFromEventsStream(subscriptionId, unsubscribeMethod);
    } else {
      log.warn("Trying to unsubscribe from a non-existing subscription. Race condition?");
    }
  }

  private Request<String, EthUnsubscribe> unsubscribeRequest(
      String subscriptionId, String unsubscribeMethod) {
    return new Request<>(
        unsubscribeMethod, Collections.singletonList(subscriptionId), this, EthUnsubscribe.class);
  }

  private void unsubscribeFromEventsStream(String subscriptionId, String unsubscribeMethod) {
    sendAsync(unsubscribeRequest(subscriptionId, unsubscribeMethod), EthUnsubscribe.class)
        .thenAccept(
            ethUnsubscribe ->
                log.debug("Successfully unsubscribed from subscription with id {}", subscriptionId))
        .exceptionally(
            throwable -> {
              log.error("Failed to unsubscribe from subscription with id {}", subscriptionId);
              return null;
            });
  }

  @SuppressWarnings("rawtypes")
  private <T extends Notification<?>> void subscribeToEventsStream(
      Request request, BehaviorSubject<T> subject, Class<T> responseType) {
    webSocketRequestProcess
        .subscriptionRequestForId()
        .put(request.getId(), new WebSocketSubscription<>(subject, responseType));
    try {
      send(request, EthSubscribe.class);
    } catch (IOException e) {
      log.error("Failed to subscribe to RPC events with request id {}", request.getId());
      subject.onError(e);
    }
  }

  @Override
  public void close() throws IOException {
    webSocketClient.close();
    executor.shutdown();
  }
}
