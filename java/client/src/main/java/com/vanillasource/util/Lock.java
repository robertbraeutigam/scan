package com.vanillasource.util;

import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class Lock {
   private final ReentrantLock lock = new ReentrantLock();

   public <T> T synchronize(Supplier<T> logic) {
      lock.lock();
      try {
         return logic.get();
      } finally {
         lock.unlock();
      }
   }

   public void synchronize(Runnable logic) {
      synchronize(() -> {
         logic.run();
         return null;
      });
   }

   public <T> BlockingSupplier<T> blockingSupplier(Supplier<Optional<T>> blockingSupplier) {
      return new BlockingSupplier<T>() {
         private final Condition itemMaybeAvailable = lock.newCondition();

         @Override
         public void notifyTry() {
            synchronize(itemMaybeAvailable::signalAll);
         }

         @Override
         public T get() {
            return synchronize(() -> {
               Optional<T> result = blockingSupplier.get();
               while (result.isEmpty()) {
                  try {
                     itemMaybeAvailable.await();
                  } catch (InterruptedException e) {
                     throw new RuntimeException(e);
                  }
                  result = blockingSupplier.get();
               }
               return result.orElseThrow();
            });
         }
      };
   }
}
