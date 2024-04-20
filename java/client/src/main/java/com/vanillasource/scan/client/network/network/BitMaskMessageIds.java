package com.vanillasource.scan.client.network.network;

import com.vanillasource.scan.client.network.data.VariableLengthInteger;
import com.vanillasource.util.BlockingSupplier;
import com.vanillasource.util.Lock;

import java.util.BitSet;
import java.util.Optional;

/**
 * Tracks ids with a bitmask. This is for optimal usage of message ids.
 */
public final class BitMaskMessageIds implements MessageIds {
   private final BitSet bits;
   private final VariableLengthInteger startId;
   private final int range;
   private final Lock lock;
   private final BlockingSupplier<Integer> nextIdSupplier;

   public BitMaskMessageIds(VariableLengthInteger startId, VariableLengthInteger endId) {
      this.startId = startId;
      this.range = endId.subtract(startId)
              .flatMap(VariableLengthInteger::intValue)
              .orElseThrow(() -> new IllegalArgumentException("Not a valid message id range for a bitmask: "+startId+" - "+endId));
      this.bits = new BitSet(range + 1);
      this.lock = new Lock();
      this.nextIdSupplier = lock.blockingSupplier(() -> {
         int id = bits.nextClearBit(0);
         if (id >= 0 && id <= range) {
            return Optional.of(id);
         }
         return Optional.empty();
      });
   }

   @Override
   public VariableLengthInteger reserveId() {
      return lock.synchronize(() -> {
         int nextId = nextIdSupplier.get();
         bits.set(nextId);
         return startId.add(VariableLengthInteger.createLong(nextId)).orElseThrow();
      });
   }

   @Override
   public void releaseId(VariableLengthInteger id) {
      lock.synchronize(() -> {
         bits.clear(id.subtract(startId).flatMap(VariableLengthInteger::intValue).orElseThrow());
         nextIdSupplier.notifyTry();
      });
   }
}

