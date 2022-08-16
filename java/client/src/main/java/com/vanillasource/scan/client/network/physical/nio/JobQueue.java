package com.vanillasource.scan.client.network.physical.nio;

import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.Iterator;

public final class JobQueue {
   private final Queue<Job<?>> jobs = new ConcurrentLinkedQueue<>();

   public void enqueue(Runnable runnable) {
      enqueue(() -> {
         runnable.run();
         return null;
      });
   }

   public <T> CompletableFuture<T> enqueue(Supplier<T> supplier) {
      CompletableFuture<T> future = new CompletableFuture<>();
      jobs.add(new Job<>(supplier, future));
      return future;
   }

   public void executeAll() {
      Iterator<Job<?>> jobsIterator = jobs.iterator();
      while (jobsIterator.hasNext()) {
         jobsIterator.next().run();
         jobsIterator.remove();
      }
   }

   private static final class Job<T> {
      private final Supplier<T> supplier;
      private final CompletableFuture<T> future;

      public Job(Supplier<T> supplier, CompletableFuture<T> future) {
         this.supplier = supplier;
         this.future = future;
      }

      public void run() {
         future.complete(supplier.get());
      }
   }
}
