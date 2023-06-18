/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class QueryIds {
   private final ScheduledExecutorService scheduler;
   private CompletableFuture<Void> resetNotify;
   private Future<Void> resetTimer;
   private int nextId;

   public QueryIds(ScheduledExecutorService scheduler) {
      this.scheduler = scheduler;
      reset();
   }

   public synchronized CompletableFuture<Integer> nextQueryId() {
      int id = nextId++;
      if (id <= 255) {
         resetTimer.cancel(false);
         resetTimer = scheduler.schedule(this::resetAndNotify, 20, TimeUnit.SECONDS);
         return CompletableFuture.completedFuture(id);
      } else {
         return resetNotify.thenCompose(ignore -> nextQueryId());
      }
   }

   private synchronized Void resetAndNotify() {
      CompletableFuture<Void> oldResetNotify = resetNotify;
      reset();
      // Do  this after the system reset, because this will already trigger nextQueryId() calls
      // on this thread.
      oldResetNotify.complete(null);
      return null;
   }

   private synchronized void reset() {
      nextId = 0;
      resetNotify = new CompletableFuture<>();
      resetTimer = CompletableFuture.completedFuture(null);
   }
}
