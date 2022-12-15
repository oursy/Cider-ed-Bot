package com.cider.bot;

import com.cider.bot.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(value = {ApplicationProperties.class})
public class BotApplication {
  public static void main(String[] args) {
    SpringApplication.run(BotApplication.class, args);
  }
}
