package com.cider.bot.config.w3j.websocket.client;

import com.cider.bot.config.w3j.websocket.ReceivedMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

  private final WebSocketClientHandshaker handshaker;

  private final ReceivedMessage receivedMessage;

  private ChannelPromise handshakeFuture;

  public WebSocketClientHandler(
      WebSocketClientHandshaker handshaker, ReceivedMessage receivedMessage) {
    this.handshaker = handshaker;
    this.receivedMessage = receivedMessage;
  }

  public WebSocketClientHandshaker getHandshaker() {
    return handshaker;
  }

  public ChannelFuture handshakeFuture() {
    return handshakeFuture;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    handshakeFuture = ctx.newPromise();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    handshaker.handshake(ctx.channel());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    log.info("WebSocket Client disconnected!");
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

    if (evt instanceof IdleStateEvent) {
      final IdleState idleState = ((IdleStateEvent) evt).state();
      if (idleState == IdleState.READER_IDLE) {
        log.warn(
            "Channel RemoteAddress:{} . Idle ReaderTimeout. Remove Channel:{}",
            ctx.channel().remoteAddress(),
            ctx.channel().id());
        log.warn("Close Channel...");
        ctx.close();
      } else if (idleState == IdleState.WRITER_IDLE) {
        ctx.writeAndFlush(new PingWebSocketFrame());
        log.debug("Send Ping !");
      }
    }
    super.userEventTriggered(ctx, evt);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    Channel ch = ctx.channel();
    if (!handshaker.isHandshakeComplete()) {
      try {
        handshaker.finishHandshake(ch, (FullHttpResponse) msg);
        log.info("WebSocket Client connected!");
        handshakeFuture.setSuccess();
      } catch (WebSocketHandshakeException e) {
        log.error("WebSocket Client failed to connect");
        handshakeFuture.setFailure(e);
      }
      return;
    }
    if (msg instanceof FullHttpResponse response) {
      throw new IllegalStateException(
          "Unexpected FullHttpResponse (getStatus="
              + response.status()
              + ", content="
              + response.content().toString(CharsetUtil.UTF_8)
              + ')');
    }
    WebSocketFrame frame = (WebSocketFrame) msg;
    if (frame instanceof TextWebSocketFrame textFrame) {
      log.debug("WebSocket Client received message: " + textFrame.text());
      try {
        receivedMessage.received(textFrame.text());
      } catch (Exception e) {
        log.warn("Received Message Error", e);
      }
    } else if (frame instanceof PongWebSocketFrame) {
      log.info(
          "WebSocket Client received pong,address:{}", ctx.channel().remoteAddress().toString());
    } else if (frame instanceof CloseWebSocketFrame) {
      log.info("WebSocket Client received closing");
      ch.close();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    cause.printStackTrace();
    if (!handshakeFuture.isDone()) {
      handshakeFuture.setFailure(cause);
    }
    ctx.close();
  }
}
