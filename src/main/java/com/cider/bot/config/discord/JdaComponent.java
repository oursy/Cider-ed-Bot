package com.cider.bot.config.discord;

import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JdaComponent {

  private final JDA jda;

  public JdaComponent(JDA jda) {
    this.jda = jda;
  }

  public void removeTextChannelId(String channelId) {
    final TextChannel textChannelById = jda.getTextChannelById(channelId);
    if (textChannelById == null) {
      return;
    }
    textChannelById.delete().queue();
    log.info("delete channel:{} success!", channelId);
  }

  public void textChannelSendMessage(String channelId, Collection<? extends MessageEmbed> embeds) {
    final TextChannel textChannel = getTextChannel(channelId);
    if (textChannel == null) {
      log.warn("channelId:{} not fund ! Failed to send ", channelId);
      return;
    }
    if (embeds.isEmpty()) {
      log.info("Embeds is empty!");
      return;
    }
    textChannel
        .sendMessageEmbeds(embeds)
        .queue(message -> log.info("ChannelId :{}, send  message:{} success!", channelId, message));
  }

  private TextChannel getTextChannel(String textChannelId) {
    return jda.getTextChannelById(textChannelId);
  }
}
