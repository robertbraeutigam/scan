package com.vanillasource.scan.client.network.frame.direct;

import com.vanillasource.scan.client.network.data.VariableLengthInteger;
import com.vanillasource.util.Synchronized;

import java.util.BitSet;

/**
 * Tracks ids with a bitmask. This is for optimal usage of message ids.
 */
public final class BitMaskMessageIds implements MessageIds {
   private final BitSet bits;
   private final VariableLengthInteger startId;
   private final int range;

   public BitMaskMessageIds(VariableLengthInteger startId, VariableLengthInteger endId) {
      this.startId = startId;
      this.range = endId.subtract(startId)
              .flatMap(VariableLengthInteger::intValue)
              .orElseThrow(() -> new IllegalArgumentException("Not a valid message id range for a bitmask: "+startId+" - "+endId));
      this.bits = new BitSet(range + 1);
   }

   @Override
   public synchronized VariableLengthInteger reserveId() {
      int nextId = new Synchronized(this)
              .waitForCondition(
                      id -> id >= 0 && id <= range,
                      () -> bits.nextClearBit(0));
      bits.set(nextId);
      return startId.add(VariableLengthInteger.createLong(nextId)).orElseThrow();
   }

   @Override
   public synchronized void releaseId(VariableLengthInteger id) {
      bits.clear(id.subtract(startId).flatMap(VariableLengthInteger::intValue).orElseThrow());
      notify();
   }
}

