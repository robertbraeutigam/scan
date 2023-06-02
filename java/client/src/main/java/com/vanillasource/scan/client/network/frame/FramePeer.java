/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import java.util.concurrent.CompletableFuture;

public interface FramePeer {
   CompletableFuture<Void> initiateHandshake(String protocolName, byte[] handshake);

   CompletableFuture<Void> continueHandshake(byte[] handshake);

   CompletableFuture<Void> closeConnection();

   CompletableFuture<Void> messageIntermediateFrame(int messageId, byte[] payload);

   CompletableFuture<Void> messageLastFrame(int messageId, byte[] payload);

   CompletableFuture<Void> messageSingleFrame(byte[] payload);

   CompletableFuture<Void> renegotiate();
}

