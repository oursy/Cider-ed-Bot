package com.cider.bot;

import com.cider.bot.config.discord.JdaComponent;
import com.cider.bot.config.listener.event.GearBoughtEvent;
import com.cider.bot.config.listener.event.GearSoldEvent;
import com.cider.bot.config.listener.event.LPClaimedEvent;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

@SpringBootTest
class BotApplicationTests {

  @Autowired JdaComponent jdaComponent;

  @Autowired ApplicationEventPublisher applicationEventPublisher;

  @Test
  void contextLoads() throws InterruptedException {
    applicationEventPublisher.publishEvent(
        new GearSoldEvent(
            "0x7801e861bbeeedca100b44aa00ec742c65212ef8a069760b6c6c0a7b4b111839",
            "0xaf1bff74708098db603e48aaebec1bbae03dcf11",
            new BigDecimal(1000),
            new BigDecimal(100)));

    applicationEventPublisher.publishEvent(
        new GearBoughtEvent(
            "0x7801e861bbeeedca100b44aa00ec742c65212ef8a069760b6c6c0a7b4b111839",
            "0xaf1bff74708098db603e48aaebec1bbae03dcf11",
            new BigDecimal("1111")));

    applicationEventPublisher.publishEvent(
        new LPClaimedEvent(
            "0x7801e861bbeeedca100b44aa00ec742c65212ef8a069760b6c6c0a7b4b111839",
            "0xaf1bff74708098db603e48aaebec1bbae03dcf11",
            new BigDecimal("1111")));
    TimeUnit.SECONDS.sleep(1);
  }
}
