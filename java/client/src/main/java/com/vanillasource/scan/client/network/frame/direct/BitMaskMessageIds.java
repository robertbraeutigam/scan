package com.vanillasource.scan.client.network.frame.direct;

import com.vanillasource.util.Synchronized;

import java.util.BitSet;

/**
 * Tracks ids with a bitmask. This is for optimal usage of message ids.
 */
public final class BitMaskMessageIds implements MessageIds {
   private final BitSet bits;
   private final int startId;
   private final int endId;

   public BitMaskMessageIds(int startId, int endId) {
      this.startId = startId;
      this.endId = endId;
      this.bits = new BitSet(endId - startId + 1);
   }

   @Override
   public synchronized int reserveId() {
      int nextId = new Synchronized(this)
              .waitForCondition(
                      id -> id >= 0 && id <= (endId - startId),
                      () -> bits.nextClearBit(0));
      bits.set(nextId);
      return nextId + startId;
   }

   @Override
   public synchronized void releaseId(int id) {
      bits.clear(id - startId);
      notify();
   }
}

