package com.cider.bot.config.listener.event;

import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LPClaimedEvent {

  private String txId;

  private String contributor;

  private BigDecimal amount;
}
