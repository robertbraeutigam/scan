/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

public interface LogicalConnection {
   /**
    * Create a potentially infitnite message to be sent to the network.
    */
   Message create();

   /**
    * Send a message through this logical connection.
    * @return A future that completes when the message is sent to
    * the network.
    */
   default CompletableFuture<Void> send(ByteBuffer message) {
      return create().closeWith(message);
   }

   /**
    *Close this logical connection.
    * @return A future that completes when all pending data is sent
    * to the network.
    */
   CompletableFuture<Void> close();
}

