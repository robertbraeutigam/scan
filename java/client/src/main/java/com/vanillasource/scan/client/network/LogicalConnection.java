/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

public interface LogicalConnection extends MessageReceiver {
   /**
    *Close this logical connection.
    * @return A future that completes when all pending data is sent
    * to the network.
    */
   CompletableFuture<Void> close();
}

