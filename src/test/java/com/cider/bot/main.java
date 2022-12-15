package com.cider.bot;

import java.math.BigDecimal;
import org.web3j.crypto.Hash;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

public class main {

  public static void main(String[] args) {
    String address = "0x96F00FC78b2848941d718A5B293A11be9aee4AC1";
    System.out.println(address.substring(0,5));
    final BigDecimal bigDecimal = Convert.fromWei("27229087458", Unit.GWEI);
    System.out.println(bigDecimal.toPlainString());
    System.out.println("GearSold:"+Hash.sha3String("GearSold(address,uint256,uint256)"));
    System.out.println("GearBought:"+Hash.sha3String("GearBought(address,uint256)"));
    System.out.println("LPClaimed:"+Hash.sha3String("LPClaimed(address,uint256)"));
  }
}
