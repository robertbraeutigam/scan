package com.vanillasource.scan.client.network.physical.nio;

import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.Iterator;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public final class JobQueue {
   private static final Logger LOGGER = LoggerFactory.getLogger(JobQueue.class);
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
      LOGGER.trace("executing {} selector jobs...", jobs.size());
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
