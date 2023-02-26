/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

/**
 * A single frame.
 */
public interface Frame extends AutoCloseable {
   /**
    * @return True, iff end of the frame has been reached. All subsequent operations
    * will be ignored and true will be returned.
    */
   CompletableFuture<Boolean> receive(ByteBuffer buffer);

   @Override
   void close();
}


