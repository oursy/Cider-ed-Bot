package com.cider.bot.config.w3j;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(value = Web3jProperties.class)
public class W3jApiConfiguration {

  @Bean
  WebSocketClient webSocketRefreshClient(Web3jProperties web3jProperties) {
    return new WebSocketClient(web3jProperties);
  }

  @Bean
  HttpServiceClient httpServiceClient(Web3jProperties web3jProperties) {
    return new HttpServiceClient(web3jProperties);
  }
}
