package com.cider.bot.config.discord;

import com.neovisionaries.ws.client.WebSocketFactory;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.internal.utils.IOUtil;
import okhttp3.OkHttpClient.Builder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Slf4j
@EnableConfigurationProperties(value = {DiscordProperties.class})
@ConditionalOnProperty(name = "discord.enable", havingValue = "true")
public class DiscordConfiguration {

  private final DiscordProperties discordProperties;

  public DiscordConfiguration(DiscordProperties discordProperties) {
    this.discordProperties = discordProperties;
  }

  @Bean
  JDA jda() {
    final JDA jda = jda(discordProperties);
    return jda;
  }

  @Bean
  HttpClient httpClient(DiscordProperties discordProperties) {
    final HttpClient.Builder builder =
        HttpClient.newBuilder()
            .version(Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(Redirect.NEVER);
    if (discordProperties.getEnableProxy()) {
      builder.proxy(
          ProxySelector.of(
              new InetSocketAddress(
                  discordProperties.getProxy().getHost(), discordProperties.getProxy().getPort())));
    }
    return builder.build();
  }

  void commandListUpdateAction(JDA jda) {
    CommandListUpdateAction commands = jda.updateCommands();
    commands.queue();
    log.info("Update Command success!");
  }

  private JDA jda(DiscordProperties discordProperties) {
    final Builder newHttpClientBuilder = IOUtil.newHttpClientBuilder();
    WebSocketFactory socketFactory = new WebSocketFactory();
    if (discordProperties.getEnableProxy()) {
      newHttpClientBuilder.proxySelector(
          ProxySelector.of(
              new InetSocketAddress(
                  discordProperties.getProxy().getHost(), discordProperties.getProxy().getPort())));
      socketFactory.getProxySettings().setHost(discordProperties.getProxy().getHost());
      socketFactory.getProxySettings().setPort(discordProperties.getProxy().getPort());
    }
    JDA jda =
        JDABuilder.createLight(
                discordProperties.getToken(),
                EnumSet.noneOf(GatewayIntent.class)) // slash commands don't need any intents
            .setHttpClientBuilder(newHttpClientBuilder)
            .setWebsocketFactory(socketFactory)
            //            .addEventListeners(slashBotAndExclamationListener)
            .build();
    log.info("Start JDA complete！");
    return jda;
  }
}
