package com.cider.bot.config.w3j;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient.Builder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Slf4j
public class HttpServiceClient implements SmartLifecycle, InitializingBean {

  private final Map<String, HttpService> httpServiceMap = new ConcurrentHashMap<>();

  private static final String DEFAULT_CLIENT = "default";

  private final Web3jProperties.Proxy proxy;

  private final String url;

  private Web3j web3j;

  public HttpServiceClient(Web3jProperties web3jProperties) {
    this.proxy = web3jProperties.getProxy();
    this.url = web3jProperties.getHttpUrl();
    final HttpService httpService = newWebServiceClient(web3jProperties.getHttpUrl());
    setDefaultClient(httpService);
  }

  public HttpServiceClient(String url, Web3jProperties.Proxy proxy) {
    this.url = url;
    this.proxy = proxy;
    final HttpService httpService = newWebServiceClient(url);
    setDefaultClient(httpService);
  }

  private HttpService getDefaultHttpServiceClient() {
    final HttpService httpService = httpServiceMap.get(DEFAULT_CLIENT);
    if (httpService == null) {
      throw new IllegalArgumentException("HttpService Not Fund!");
    }
    return httpService;
  }

  private void setDefaultClient(HttpService httpService) {
    httpServiceMap.put(DEFAULT_CLIENT, httpService);
  }

  private HttpService newWebServiceClient(String url) {
    log.info("NewWebServiceClient Success!");
    final Builder okHttpClientBuilder = HttpService.getOkHttpClientBuilder();
    if (proxy.getEnable()) {
      log.info("Load Proxy :{}", proxy);
      okHttpClientBuilder.proxySelector(
          ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
    }
    return new HttpService(url, okHttpClientBuilder.build());
  }

  @Override
  public void start() {
    try {
      afterPropertiesSet();
    } catch (Exception e) {
      log.error("afterPropertiesSet Error", e);
      throw new RuntimeException(e);
    }
  }

  public Web3j web3j() {
    return this.web3j;
  }

  @Override
  public void stop() {
    if (web3j != null) {
      web3j.shutdown();
      web3j = null;
    }
  }

  @Override
  public boolean isRunning() {
    return web3j != null;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    initWeb3jBuild();
  }

  private void initWeb3jBuild() {
    this.web3j = Web3j.build(this.getDefaultHttpServiceClient());
  }
}
