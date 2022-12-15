package com.cider.bot.config.discord;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "discord", ignoreUnknownFields = false)
@Setter
@Getter
public class DiscordProperties {
  private Boolean enable = false;

  private String token;

  private Boolean enableProxy = true;

  private Proxy proxy = new Proxy();

  private Map<NotificationType, String> textChannelMap = new HashMap<>();

  @Getter
  @Setter
  public static class Proxy {

    private String host = "127.0.0.1";

    private int port = 7890;
  }

  public static enum NotificationType {
    CIDER
  }
}
