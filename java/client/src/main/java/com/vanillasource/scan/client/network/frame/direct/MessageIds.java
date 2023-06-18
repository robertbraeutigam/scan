/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import java.util.concurrent.CompletableFuture;

/**
 * Tracked used / available message ids and try to always return an optimal one.
 * Since message ids are included in the messages as variable length integers,
 * the smaller this id is, the less bytes it occupies in the message. At a minimum
 * it is 1 byte (if the id is less than 128), at most this is 8 bytes.
 */
public interface MessageIds {
   CompletableFuture<Integer> reserveId();

   void releaseId(int id);
}

