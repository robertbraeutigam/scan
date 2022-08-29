package com.vanillasource.scan.client.network.physical.nio;

import java.nio.channels.Selector;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.io.UncheckedIOException;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class NioSelector implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioSelector.class);
   private final Selector selector;
   private final CompletableFuture<Void> closed = new CompletableFuture<>();
   private final JobQueue queue = new JobQueue();
   private final AtomicBoolean selecting = new AtomicBoolean(false);
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
      if (selecting.get()) {
         CompletableFuture<T> future = queue.enqueue(supplier);
         selector.wakeup();
         return future;
      } else {
         return CompletableFuture.completedFuture(supplier.get());
      }
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
            if (LOGGER.isTraceEnabled()) {
               LOGGER.trace("selecting on {}", selector.keys().stream().map(key -> key.attachment().toString()+":"+key.interestOps()).collect(Collectors.toList()));
            }
            selecting.set(true);
            int changedKeys = selector.select(1000); // 1 sec
            selecting.set(false);
            LOGGER.trace("selected {} keys", changedKeys);
            Iterator<SelectionKey> keysIterator = selector.selectedKeys().iterator();
            // Handle keys
            while (keysIterator.hasNext()) {
               SelectionKey key = keysIterator.next();
               NioHandler handler = (NioHandler) key.attachment();
               NioSelectorKey nioKey = new NioSelectorKey(this, key);
               LOGGER.trace("read ops {}, calling handler", key.readyOps());
               if (key.isValid() && key.isConnectable()) {
                  handler.handleConnectable(nioKey);
               }
               if (key.isValid() && key.isReadable()) {
                  handler.handleReadable(nioKey);
               }
               if (key.isValid() && key.isWritable()) {
                  handler.handleWritable(nioKey);
               }
               if (key.isValid() && key.isAcceptable()) {
                  handler.handleAccept(nioKey);
               }
               keysIterator.remove();
            }
            // Execute jobs
            queue.executeAll();
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
            LOGGER.debug("selector closed", exception);
            try {
               selector.close();
            } catch (IOException e) {
               LOGGER.warn("selector couldn't be closed", e);
            }
         })
      .join();
   }

}
