/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

/**
 * A peer that is connected through a logical connection.
 */
public interface Peer {
   /**
    * Called to create an message.
    */
   Message create();

   /**
    * Send a message through this logical connection.
    * @return A future that completes when the message is sent to
    * the network.
    */
   default CompletableFuture<Void> receive(ByteBuffer message) {
      return create().endWith(message);
   }

   /**
    * Close this logical connection.
    * @return A future that completes when all pending data is sent
    * to the network.
    */
   CompletableFuture<Void> close();
}

