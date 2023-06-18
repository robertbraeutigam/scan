/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import java.util.concurrent.CompletableFuture;
import java.util.BitSet;

/**
 * Tracks ids with a bitmask. This is for optimal usage of message ids.
 */
public final class BitMaskMessageIds implements MessageIds {
   private final BitSet bits;
   private final int startId;
   private final int endId;
   private CompletableFuture<Void> clearNotify;

   public BitMaskMessageIds(int startId, int endId) {
      this.startId = startId;
      this.endId = endId;
      this.bits = new BitSet(endId-startId+1);
      this.clearNotify = new CompletableFuture<>();
   }

   @Override
   public synchronized CompletableFuture<Integer> reserveId() {
      int nextId = bits.nextClearBit(0);
      if (nextId >= 0) {
         bits.set(nextId);
         return CompletableFuture.completedFuture(nextId + startId);
      } else {
         return clearNotify.thenCompose(ignore -> reserveId());
      }
   }

   @Override
   public synchronized void releaseId(int id) {
      bits.clear(id - startId);
      CompletableFuture<Void> oldClearNotify = clearNotify;
      clearNotify = new CompletableFuture<>();
      oldClearNotify.complete(null);
   }
}

