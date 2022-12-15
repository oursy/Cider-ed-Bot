package com.cider.bot.config.w3j.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.subjects.BehaviorSubject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSubscribe;
import org.web3j.protocol.websocket.WebSocketSubscription;
import org.web3j.protocol.websocket.events.Notification;

@Slf4j
public record DefaultReceivedMessage(
    ObjectMapper objectMapper, WebSocketRequestProcess webSocketRequestProcess)
    implements ReceivedMessage {

  @Override
  public void received(String text) {
    try {
      onWebSocketMessage(text);
    } catch (IOException e) {
      log.error("onWebSocketMessage Exception", e);
    }
  }

  private JsonNode parseToTree(String replyStr) throws IOException {
    try {
      return objectMapper.readTree(replyStr);
    } catch (IOException e) {
      throw new IOException("Failed to parse incoming WebSocket message", e);
    }
  }

  private boolean isReply(JsonNode replyJson) {
    return replyJson.has("id");
  }

  @SuppressWarnings("rawtypes")
  private WebSocketRequest getAndRemoveRequest(long id) throws IOException {
    if (!webSocketRequestProcess.requestForId().containsKey(id)) {
      throw new IOException(String.format("Received reply for unexpected request id: %d", id));
    }
    WebSocketRequest request = webSocketRequestProcess.requestForId().get(id);
    webSocketRequestProcess.requestForId().remove(id);
    return request;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void processRequestReply(String replyStr, JsonNode replyJson) throws IOException {
    long replyId = getReplyId(replyJson);
    WebSocketRequest request = getAndRemoveRequest(replyId);
    try {
      Object reply = objectMapper.convertValue(replyJson, request.getResponseType());
      // Instead of sending a reply to a caller asynchronously we need to process it here
      // to avoid race conditions we need to modify state of this class.
      if (reply instanceof EthSubscribe) {
        processSubscriptionResponse(replyId, (EthSubscribe) reply);
      }

      sendReplyToListener(request, reply);
    } catch (IllegalArgumentException e) {
      sendExceptionToListener(replyStr, request, e);
    }
  }

  private long getReplyId(JsonNode replyJson) throws IOException {
    JsonNode idField = replyJson.get("id");
    if (idField == null) {
      throw new IOException("'id' field is missing in the reply");
    }

    if (!idField.isIntegralNumber()) {
      if (idField.isTextual()) {
        try {
          return Long.parseLong(idField.asText());
        } catch (NumberFormatException e) {
          throw new IOException(
              String.format(
                  "Found Textual 'id' that cannot be casted to long. Input : '%s'",
                  idField.asText()));
        }
      } else {
        throw new IOException(
            String.format("'id' expected to be long, but it is: '%s'", idField.asText()));
      }
    }

    return idField.longValue();
  }

  void onWebSocketMessage(String messageStr) throws IOException {
    JsonNode replyJson = parseToTree(messageStr);
    if (isReply(replyJson)) {
      processRequestReply(messageStr, replyJson);
    } else if (isBatchReply(replyJson)) {
      processBatchRequestReply(messageStr, (ArrayNode) replyJson);
    } else if (isSubscriptionEvent(replyJson)) {
      processSubscriptionEvent(messageStr, replyJson);
    } else {
      throw new IOException("Unknown message type");
    }
  }

  @SuppressWarnings("rawtypes")
  private void processSubscriptionEvent(String replyStr, JsonNode replyJson) {
    log.debug("Processing event: {}", replyStr);
    String subscriptionId = extractSubscriptionId(replyJson);
    WebSocketSubscription subscription =
        webSocketRequestProcess.subscriptionForId().get(subscriptionId);
    if (subscription != null) {
      sendEventToSubscriber(replyJson, subscription);
    } else {
      log.warn("No subscriber for WebSocket event with subscription id {}", subscriptionId);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void sendEventToSubscriber(JsonNode replyJson, WebSocketSubscription subscription) {
    Object event = objectMapper.convertValue(replyJson, subscription.getResponseType());
    subscription.getSubject().onNext(event);
  }

  private String extractSubscriptionId(JsonNode replyJson) {
    return replyJson.get("params").get("subscription").asText();
  }

  private boolean isBatchReply(JsonNode replyJson) {
    return replyJson.isArray();
  }

  private void processBatchRequestReply(String replyStr, ArrayNode replyJson) throws IOException {
    long replyId = getReplyId(replyJson.get(0));
    WebSocketRequests webSocketRequests = (WebSocketRequests) getAndRemoveRequest(replyId);
    try {
      // rollback request id of first batch elt
      ((ObjectNode) replyJson.get(0)).put("id", webSocketRequests.getOriginId());

      List<Request<?, ? extends Response<?>>> requests = webSocketRequests.getRequests();
      List<Response<?>> responses = new ArrayList<>(replyJson.size());

      for (int i = 0; i < replyJson.size(); i++) {
        Response<?> response =
            objectMapper.treeToValue(replyJson.get(i), requests.get(i).getResponseType());
        responses.add(response);
      }

      sendReplyToListener(webSocketRequests, new BatchResponse(requests, responses));
    } catch (IllegalArgumentException e) {
      sendExceptionToListener(replyStr, webSocketRequests, e);
    }
  }

  @SuppressWarnings("rawtypes")
  private void sendExceptionToListener(
      String replyStr, WebSocketRequest request, IllegalArgumentException e) {
    request
        .getOnReply()
        .completeExceptionally(
            new IOException(
                String.format(
                    "Failed to parse '%s' as type %s", replyStr, request.getResponseType()),
                e));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void sendReplyToListener(WebSocketRequest request, Object reply) {
    request.getOnReply().complete(reply);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void processSubscriptionResponse(long replyId, EthSubscribe reply) throws IOException {
    WebSocketSubscription subscription =
        webSocketRequestProcess.subscriptionRequestForId().get(replyId);
    processSubscriptionResponse(reply, subscription.getSubject(), subscription.getResponseType());
  }

  private <T extends Notification<?>> void processSubscriptionResponse(
      EthSubscribe subscriptionReply, BehaviorSubject<T> subject, Class<T> responseType) {
    if (!subscriptionReply.hasError()) {
      establishSubscription(subject, responseType, subscriptionReply);
    } else {
      reportSubscriptionError(subject, subscriptionReply);
    }
  }

  private <T extends Notification<?>> void reportSubscriptionError(
      BehaviorSubject<T> subject, EthSubscribe subscriptionReply) {
    Response.Error error = subscriptionReply.getError();
    log.error("Subscription request returned error: {}", error.getMessage());
    subject.onError(
        new IOException(
            String.format("Subscription request failed with error: %s", error.getMessage())));
  }

  private <T extends Notification<?>> void establishSubscription(
      BehaviorSubject<T> subject, Class<T> responseType, EthSubscribe subscriptionReply) {
    log.debug("Subscribed to RPC events with id {}", subscriptionReply.getSubscriptionId());
    webSocketRequestProcess
        .subscriptionForId()
        .put(
            subscriptionReply.getSubscriptionId(),
            new WebSocketSubscription<>(subject, responseType));
  }

  private boolean isSubscriptionEvent(JsonNode replyJson) {
    return replyJson.has("method");
  }
}
