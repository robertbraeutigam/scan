/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.function.Consumer;

public interface WildcardQueryIssuer extends Consumer<byte[]>, AutoCloseable {
   /**
    * Called to provide an address that has been detected. There is
    * no guarantee whether this address was already detected previously
    * or not.
    */
   @Override
   void accept(byte[] address);

   /**
    * Called to indicate that no more addresses will be supplied. Issuer
    * may free up resources at this point.
    */
   @Override
   default void close() {
   }
}
