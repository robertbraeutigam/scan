/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import com.vanillasource.scan.client.network.frame.FramePeer;
import java.util.concurrent.CompletableFuture;
import com.vanillasource.scan.client.network.Peer;
import java.nio.ByteBuffer;

public final class PeerToFramePeer implements FramePeer {
   private final Peer peer;

   public PeerToFramePeer(Peer peer) {
      this.peer = peer;
   }

   @Override
   public CompletableFuture<Void> initiateHandshake(String protocolName, ByteBuffer handshake) {
      return null; // TODO
   }

   @Override
   public CompletableFuture<Void> continueHandshake(ByteBuffer handshake) {
      return null; // TODO
   }

   @Override
   public CompletableFuture<Void> closeConnection() {
      return null; // TODO
   }

   @Override
   public CompletableFuture<Void> messageIntermediateFrame(int messageId, ByteBuffer payload) {
      return null; // TODO
   }

   @Override
   public CompletableFuture<Void> messageLastFrame(int messageId, ByteBuffer payload) {
      return null; // TODO
   }

   @Override
   public CompletableFuture<Void> messageSingleFrame(ByteBuffer payload) {
      return null; // TODO
   }

   @Override
   public CompletableFuture<Void> renegotiate() {
      return null; // TODO
   }
}
