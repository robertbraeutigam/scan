package com.vanillasource.util;

import java.util.function.Function;
import java.util.function.Supplier;

public class Synchronized {
   private final Object target;

   public Synchronized(Object target) {
      this.target = target;
   }

   public <T> T waitForCondition(Function<T, Boolean> condition, Supplier<T> supplier) {
      synchronized (target) {
         T result = supplier.get();
         while (!condition.apply(result)) {
            try {
               target.wait();
            } catch (InterruptedException e) {
               throw new RuntimeException(e);
            }
            result = supplier.get();
         }
         return result;
      }
   }
}
