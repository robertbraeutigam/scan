/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import com.vanillasource.scan.client.network.frame.FramePeer;
import java.util.concurrent.CompletableFuture;
import com.vanillasource.scan.client.network.Peer;
import com.vanillasource.scan.client.network.Message;
import java.nio.ByteBuffer;

public final class FramePeerToPeer implements Peer {
   private final FramePeer framePeer;
   private final MessageIds messageIds;

   public FramePeerToPeer(MessageIds messageIds, FramePeer framePeer) {
      this.messageIds = messageIds;
      this.framePeer = framePeer;
   }
 
   @Override
   public Message create() {
      return new Message() {
         private CompletableFuture<Integer> idFuture = CompletableFuture.completedFuture(-1);

         @Override
         public synchronized CompletableFuture<Void> recieve(ByteBuffer buffer) {
            return idFuture.thenCompose(id -> {
               if (id == -1) {
                  idFuture = messageIds.reserveId();
                  return recieve(buffer);
               } else {
                  return framePeer.messageIntermediateFrame(id, buffer);
               }
            });
         }

         @Override
         public synchronized CompletableFuture<Void> endWith(ByteBuffer buffer) {
            return idFuture.thenCompose(id -> {
               if (id == -1) {
                  return framePeer.messageSingleFrame(buffer);
               } else {
                  return framePeer.messageLastFrame(id, buffer);
               }
            });
         }
      };
   }

   @Override
   public CompletableFuture<Void> close() {
      return framePeer.closeConnection();
   }
}

