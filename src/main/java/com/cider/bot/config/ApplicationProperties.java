package com.cider.bot.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "application", ignoreUnknownFields = false)
@Getter
@Setter
public class ApplicationProperties {

  private final Cider cider = new Cider();

  @Data
  public static class Cider {
    private String contract;
    private String gearSold;
    private String gearBought;
    private String lpClaimed;
  }
}
