package com.cider.bot;

import org.web3j.crypto.Hash;

public class main {

  public static void main(String[] args) {
    System.out.println("GearSold:"+Hash.sha3String("GearSold(address,uint256,uint256)"));
    System.out.println("GearBought:"+Hash.sha3String("GearBought(address,uint256)"));
    System.out.println("LPClaimed:"+Hash.sha3String("LPClaimed(address,uint256)"));
  }
}
