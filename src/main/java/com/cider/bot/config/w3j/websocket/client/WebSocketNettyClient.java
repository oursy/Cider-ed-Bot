package com.cider.bot.config.w3j.websocket.client;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;

import com.cider.bot.config.w3j.websocket.ReceivedMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.FixedChannelPool.AcquireTimeoutAction;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketNettyClient {

  private final Bootstrap bootstrap = new Bootstrap();

  private final EventLoopGroup workerGroup =
      new NioEventLoopGroup(new DefaultThreadFactory("netty-ws-client"));

  private final FixedChannelPool fixedChannelPool;

  private final ReceivedMessage receivedMessage;

  public WebSocketNettyClient(String wsUrl, ReceivedMessage receivedMessage) throws Exception {
    this.receivedMessage = receivedMessage;
    int port = 443;
    final URI uri = URI.create(wsUrl);
    String host = uri.getHost();
    createBootStrap(host, port);
    // create fixedChannelPool
    final SimpleChannelPoolHandler simpleChannelPoolHandler =
        new SimpleChannelPoolHandler(
            uri,
            SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build());
    this.fixedChannelPool = createFixedChannelPool(simpleChannelPoolHandler);
  }

  private FixedChannelPool createFixedChannelPool(
      SimpleChannelPoolHandler simpleChannelPoolHandler) {
    return new FixedChannelPool(
        bootstrap,
        simpleChannelPoolHandler,
        ChannelHealthChecker.ACTIVE,
        AcquireTimeoutAction.FAIL,
        3000,
        3,
        1024);
  }

  private void createBootStrap(String host, Integer port) {
    bootstrap.group(workerGroup).channel(NioSocketChannel.class).remoteAddress(host, port);
  }

  public void sendMessage(String requestBody) {
    if (requestBody == null) {
      log.error("Send RequestBody Empty!");
      return;
    }
    this.fixedChannelPool
        .acquire()
        .addListener(
            future -> {
              if (future.isSuccess()) {
                final Channel channel = (Channel) future.getNow();
                try {
                  if (!channel.isActive() || !channel.isOpen()) {
                    log.error("Channel :{} not Active ....", channel.id());
                    return;
                  }
                  final WebSocketClientHandler channelHandler =
                      (WebSocketClientHandler) channel.pipeline().get("handler");
                  if (!channelHandler.getHandshaker().isHandshakeComplete()) {
                    log.warn("HandshakeFuture Connect....");
                    channelHandler.handshakeFuture().sync();
                    log.info("HandshakeFuture Connected!");
                  }
                  log.debug("Send Message:{}", requestBody);
                  channel.writeAndFlush(new TextWebSocketFrame(requestBody));
                } catch (Exception e) {
                  e.printStackTrace();
                  log.error("Get Channel Error!");
                } finally {
                  fixedChannelPool.release(channel);
                }
              } else {
                future.cause().printStackTrace();
                log.error("Get fixedChannelPool Error", future.cause());
              }
            });
  }

  @PreDestroy
  public void close() {
    this.fixedChannelPool.close();
    workerGroup.shutdownGracefully();
    log.info("stop WorkerGroup");
  }

  public class SimpleChannelPoolHandler implements ChannelPoolHandler {

    private final URI wsUri;

    private final SslContext sslCtx;

    SimpleChannelPoolHandler(URI wsUri, SslContext sslContext) {
      this.wsUri = wsUri;
      this.sslCtx = sslContext;
    }

    @Override
    public void channelReleased(Channel ch) throws Exception {
      log.debug("Now:{}. channelReleased! Channel INFO:{}", System.currentTimeMillis(), ch);
    }

    @Override
    public void channelAcquired(Channel ch) throws Exception {
      log.debug("Now:{}. channelAcquired! Channel INFO:{}", System.currentTimeMillis(), ch);
    }

    @Override
    public void channelCreated(Channel ch) throws Exception {
      log.debug("Now:{}. channelCreated! Channel INFO:{}", System.currentTimeMillis(), ch);
      ChannelPipeline p = ch.pipeline();
      p.addLast(sslCtx.newHandler(ch.alloc(), wsUri.getHost(), 443));
      //      p.addLast(new HttpProxyHandler(new InetSocketAddress("127.0.0.1",7890)));
      p.addLast(new IdleStateHandler(60, 1, 0));
      p.addLast(
          new HttpClientCodec(DEFAULT_MAX_INITIAL_LINE_LENGTH, 65536 * 10, 65536 * 10),
          new HttpObjectAggregator(65536 * 10),
          WebSocketClientCompressionHandler.INSTANCE);
      p.addLast(
          "handler",
          new WebSocketClientHandler(
              WebSocketClientHandshakerFactory.newHandshaker(
                  wsUri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), 65536 * 10),
              receivedMessage));
    }
  }
}
