package com.cider.bot.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MessageConvertUtils {

  private static final Map<Integer, Integer> COLOR_MAP =
      new HashMap<>() {
        {
          put(1, 8985591);
          put(2, 1823479);
          put(3, 8985591);
          put(4, 16222491);
          put(5, 16291896);
          put(6, 164426);
          put(7, 4129410);
          put(8, 159874);
          put(9, 10678782);
          put(10, 88423);
          put(11, 1402625);
          put(12, 6750519);
          put(13, 5046569);
          put(14, 5065473);
          put(15, 15591171);
        }
      };

  public static int colorRandom() {
    Random rand = new Random();
    final ArrayList<Integer> colors = new ArrayList<>(COLOR_MAP.values());
    return colors.get(rand.nextInt(colors.size()));
  }
}
