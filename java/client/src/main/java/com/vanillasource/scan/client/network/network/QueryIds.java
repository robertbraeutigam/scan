package com.vanillasource.scan.client.network.network;

import com.vanillasource.scan.client.network.data.VariableLengthInteger;
import com.vanillasource.util.Lock;
import com.vanillasource.util.TimeSource;

public final class QueryIds {
   private static final long RESET_TIME_MILLIS = 20L*1000L;
   private final TimeSource timeSource;
   private VariableLengthInteger nextId = VariableLengthInteger.createLong(0);
   private final Lock lock = new Lock();
   private long lastIdTime = 0;

   public QueryIds(TimeSource timeSource) {
      this.timeSource = timeSource;
   }

   public VariableLengthInteger nextQueryId() {
      return lock.synchronize(() -> {
         if (lastIdTime + RESET_TIME_MILLIS <= timeSource.currentTimeMillis()) {
            nextId = VariableLengthInteger.createLong(1);
         } else {
            nextId = nextId.increase().orElseGet(() -> {
               timeSource.sleep(RESET_TIME_MILLIS);
               return VariableLengthInteger.createLong(1);
            });
         }
         lastIdTime = timeSource.currentTimeMillis();
         return nextId.decrease().orElse(VariableLengthInteger.createLong(0));
      });
   }
}
