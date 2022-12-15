package com.cider.bot.config.w3j;

import com.cider.bot.config.w3j.websocket.NettyWebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;

@Slf4j
public class WebSocketClient implements SmartLifecycle, InitializingBean {

  private final Web3jProperties web3jProperties;

  private Web3j web3j;

  public WebSocketClient(Web3jProperties web3jProperties) {
    this.web3jProperties = web3jProperties;
  }

  private Web3jService buildWebSocketService() throws Throwable {
    return new NettyWebSocketService(
        web3jProperties.getWsUrl());
  }

  public Web3j web3j() {
    return this.web3j;
  }

  private void buildWeb3j() throws Throwable {
    this.web3j = Web3j.build(buildWebSocketService());
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    try {
      buildWeb3j();
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void start() {
    try {
      afterPropertiesSet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    if (isRunning()) {
      web3j.shutdown();
    }
  }

  @Override
  public boolean isRunning() {
    return web3j != null;
  }
}
