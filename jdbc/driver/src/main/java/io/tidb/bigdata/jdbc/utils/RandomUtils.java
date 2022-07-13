package io.tidb.bigdata.jdbc.utils;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class RandomUtils {

  public static int randomValue(int max) {
    Random random = ThreadLocalRandom.current();
    return random.nextInt(max);
  }
}
