/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

public interface Message {
   /**
    * Receive the given bytes to this message.
    * @return A future that completes when bytes are processed,
    * i.e. when the buffer is ready to be used again.
    */
   CompletableFuture<Void> recieve(ByteBuffer buffer);

   /**
    * Close this message with the last part supplied as argument.
    * @return A future that completes when all pending bytes are sent
    * to the network.
    */
   CompletableFuture<Void> endWith(ByteBuffer buffer);

   /**
    * Close this message, there will be no more parts of this message.
    * @return A future that completes when all pending bytes are processed.
    */
   default CompletableFuture<Void> end() {
      return endWith(ByteBuffer.wrap(new byte[] {}));
   }
}

