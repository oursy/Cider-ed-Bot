package com.cider.bot.config.w3j;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "w3j", ignoreUnknownFields = false)
@Getter
@Setter
public class Web3jProperties {

  private String wsUrl;

  private String httpUrl;

  private Proxy proxy = new Proxy();

  @Getter
  @Setter
  @ToString
  public static class Proxy {

    private Boolean enable = true;

    private String host = "127.0.0.1";

    private int port = 7890;
  }
}
