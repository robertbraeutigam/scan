package com.vanillasource.scan.client.network.physical.nio;

import java.nio.channels.Selector;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

public final class NioSelector implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioSelector.class);
   private final Selector selector;
   private final CompletableFuture<Void> closed = new CompletableFuture<>();
   private final Queue<QueueEntry<?>> selectorJobs = new ConcurrentLinkedQueue<>();
   private volatile boolean running = true;

   private NioSelector(Selector selector) {
      this.selector = selector;
   }

   public NioSelectorKey register(AbstractSelectableChannel channel, NioHandler handler) {
      return onSelectionThread(() -> {
         try {
            return new NioSelectorKey(this, channel.register(selector, 0, handler));
         } catch (IOException e) {
            throw new UncheckedIOException(e);
         }
      }).join();
   }

   public void onSelectionThread(Runnable runnable) {
      onSelectionThread(() -> {
         runnable.run();
         return null;
      });
   }

   public <T> CompletableFuture<T> onSelectionThread(Supplier<T> supplier) {
      CompletableFuture<T> future = new CompletableFuture<>();
      selectorJobs.add(new QueueEntry<>(supplier, future));
      selector.wakeup();
      return future;
   }

   /**
    * Constructs a selector.
    */
   public static NioSelector create() throws IOException {
      Selector selector = Selector.open();
      NioSelector nioSelector = new NioSelector(selector);

      Thread thread = new Thread(nioSelector::select, "Nio Select");
      thread.start();

      return nioSelector;
   }

   /**
    * Do the selection.
    */
   private void select() {
      try {
         while (running) {
            LOGGER.trace("selecting...");
            int changedKeys = selector.select(1000); // 1 sec
            Iterator<SelectionKey> keysIterator = selector.selectedKeys().iterator();
            // Handle keys
            while (keysIterator.hasNext()) {
               SelectionKey key = keysIterator.next();
               LOGGER.trace("read ops {}, calling handler", key.readyOps());
               ((NioHandler) key.attachment()).handle(new NioSelectorKey(this, key));
               keysIterator.remove();
            }
            // Execute jobs
            LOGGER.trace("executing all selector jobs...");
            Iterator<QueueEntry<?>> selectorJobsIterator = selectorJobs.iterator();
            while (selectorJobsIterator.hasNext()) {
               QueueEntry<?> selectorJob = selectorJobsIterator.next();
               selectorJob.run();
               selectorJobsIterator.remove();
            }
         }
      } catch (Throwable e) {
         closeExceptionally(e);
      } finally {
         closed.complete(null);
      }
   }

   public void closeExceptionally(Throwable t) {
      closed.completeExceptionally(t);
      close();
   }

   @Override
   public void close() {
      running = false;
      selector.wakeup();
      closed
         .whenComplete((result, exception) -> {
            try {
               selector.close();
            } catch (IOException e) {
               LOGGER.warn("selector couldn't be closed", e);
            }
         })
      .join();
   }

   private static final class QueueEntry<T> {
      private final Supplier<T> supplier;
      private final CompletableFuture<T> future;

      public QueueEntry(Supplier<T> supplier, CompletableFuture<T> future) {
         this.supplier = supplier;
         this.future = future;
      }

      public void run() {
         future.complete(supplier.get());
      }
   }
}
