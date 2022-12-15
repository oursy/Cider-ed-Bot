package com.cider.bot.config.listener.event;

import com.cider.bot.config.AsyncEventListener;
import com.cider.bot.config.discord.DiscordProperties;
import com.cider.bot.config.discord.DiscordProperties.NotificationType;
import com.cider.bot.config.discord.JdaComponent;
import com.cider.bot.config.listener.MessageConvertUtils;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CiderListener {

  private final JdaComponent jdaComponent;

  private final DiscordProperties discordProperties;

  public static final NumberFormat currencyInstance =
      NumberFormat.getCurrencyInstance(new Builder().setLanguage("en").setRegion("US").build());

  @AsyncEventListener
  public void onGearBoughtEvent(GearBoughtEvent gearBoughtEvent) {
    log.info("onGearBoughtEvent:{}", gearBoughtEvent);
    Thread.startVirtualThread(
        () -> {
          jdaComponent.textChannelSendMessage(
              discordProperties.getTextChannelMap().get(NotificationType.CIDER),
              List.of(
                  new MessageEmbed(
                      getTxIdUrl(gearBoughtEvent.getTxId()),
                      "Gear Bought Event ",
                      null,
                      EmbedType.RICH,
                      OffsetDateTime.now(),
                      MessageConvertUtils.colorRandom(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      List.of(
                          new Field("Buyer", gearBoughtEvent.getBuyer(), false),
                          new Field(
                              "Amount",
                              currencyInstance.format(gearBoughtEvent.getAmount()),
                              false)))));
        });
  }

  @AsyncEventListener
  public void onGearSoldEvent(GearSoldEvent gearSoldEvent) {
    log.info("onGearSoldEvent:{}", gearSoldEvent);
    Thread.startVirtualThread(
        () -> {
          jdaComponent.textChannelSendMessage(
              discordProperties.getTextChannelMap().get(NotificationType.CIDER),
              List.of(
                  new MessageEmbed(
                      getTxIdUrl(gearSoldEvent.getTxId()),
                      "Gear Sold Event",
                      null,
                      EmbedType.RICH,
                      OffsetDateTime.now(),
                      MessageConvertUtils.colorRandom(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      List.of(
                          new Field("Seller", gearSoldEvent.getSeller(), false),
                          new Field(
                              "Amount", currencyInstance.format(gearSoldEvent.getAmount()), false),
                          new Field(
                              "ShearedAmount",
                              currencyInstance.format(gearSoldEvent.getShearedAmount()),
                              false)))));
        });
  }

  @AsyncEventListener
  public void onLPClaimedEvent(LPClaimedEvent lpClaimedEvent) {
    log.info("onLPClaimedEvent:{}", lpClaimedEvent);
    Thread.startVirtualThread(
        () -> {
          jdaComponent.textChannelSendMessage(
              discordProperties.getTextChannelMap().get(NotificationType.CIDER),
              List.of(
                  new MessageEmbed(
                      getTxIdUrl(lpClaimedEvent.getTxId()),
                      "LP Claimed Event",
                      null,
                      EmbedType.RICH,
                      OffsetDateTime.now(),
                      MessageConvertUtils.colorRandom(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      List.of(
                          new Field("Contributor", lpClaimedEvent.getContributor(), false),
                          new Field(
                              "Amount",
                              currencyInstance.format(lpClaimedEvent.getAmount()),
                              false)))));
        });
  }

  private String getTxIdUrl(String txId) {
    return String.format("https://etherscan.io" + "/tx/%s", txId);
  }
}
