package com.vanillasource.util;

public interface TimeSource {
   long currentTimeMillis();

   void sleep(long millis);
}
