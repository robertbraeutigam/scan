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
    * Send the given bytes as part of this message.
    * @return A future that completes when bytes are sent to network,
    * i.e. when the buffer is ready to be used again.
    */
   CompletableFuture<Void> send(ByteBuffer buffer);

   /**
    * Close this message with the last part supplied as argument.
    * @return A future that completes when all pending bytes are sent
    * to the network.
    */
   CompletableFuture<Void> closeWith(ByteBuffer buffer);

   /**
    * Close this message, there will be no more parts of this message sent.
    * @return A future that completes when all pending bytes are sent
    * to the network.
    */
   default CompletableFuture<Void> close() {
      return closeWith(ByteBuffer.wrap(new byte[] {}));
   }
}

