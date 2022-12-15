package com.cider.bot.config.listener.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class GearSoldEvent {

  private String txId;

  private String seller;

  private BigDecimal amount;

  private BigDecimal shearedAmount;
}
