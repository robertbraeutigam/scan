package com.vanillasource.util;

import java.util.function.Supplier;

public interface BlockingSupplier<T> extends Supplier<T> {
   void notifyTry();

   T get();
}
