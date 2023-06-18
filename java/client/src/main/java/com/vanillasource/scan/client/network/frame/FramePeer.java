/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

public interface FramePeer {
   CompletableFuture<Void> initiateHandshake(String protocolName, ByteBuffer handshake);

   CompletableFuture<Void> continueHandshake(ByteBuffer handshake);

   CompletableFuture<Void> closeConnection();

   CompletableFuture<Void> messageIntermediateFrame(int messageId, ByteBuffer payload);

   CompletableFuture<Void> messageLastFrame(int messageId, ByteBuffer payload);

   CompletableFuture<Void> messageSingleFrame(ByteBuffer payload);

   CompletableFuture<Void> renegotiate();
}

