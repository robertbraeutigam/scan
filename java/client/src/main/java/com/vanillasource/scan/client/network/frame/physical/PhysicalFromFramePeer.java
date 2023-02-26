/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.physical;

import com.vanillasource.scan.client.network.physical.PhysicalPeer;
import com.vanillasource.scan.client.network.frame.FramePeer;
import com.vanillasource.scan.client.network.frame.Frame;
import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.util.Optional;

public final class PhysicalFromFramePeer implements PhysicalPeer {
   private final FramePeer peer;
   private final ByteBuffer header = ByteBuffer.allocate(32+32+2);
   private int frameHeader = 0;
   private CompletableFuture<Frame> activeFrame = null;

   public PhysicalFromFramePeer(FramePeer peer) {
      this.peer = peer;
   }

   @Override
   public synchronized CompletableFuture<Void> receive(ByteBuffer message) {
      // Nothing to read then quit
      if (message.remaining() == 0) {
         return CompletableFuture.completedFuture(null);
      }
      // If there is no active frame, try to read the header
      if (activeFrame == null) {
         if (frameHeader == 0) {
            frameHeader = message.get() & 0xFF;
         }
         int headerLength = 2 + 32*((frameHeader>>6)&1) + 32*((frameHeader>>7)&1);
         if (header.position() < headerLength) {
            // Header is not yet complete, try to read, but not too much
            byte[] tmp = new byte[Math.min(message.remaining(), headerLength-header.position())];
            message.get(tmp);
            header.put(tmp);
         }
         if (header.position() == headerLength) {
            // Header is complete, so allocate frame
            Optional<byte[]> sourcePeer = Optional.empty();
            if ((frameHeader & (1<<6)) > 0) {
               byte[] sourcePeerAddress = new byte[32];
               header.get(sourcePeerAddress);
               sourcePeer = Optional.of(sourcePeerAddress);
            }
            Optional<byte[]> targetPeer = Optional.empty();
            if ((frameHeader & (1<<7)) > 0) {
               byte[] targetPeerAddress = new byte[32];
               header.get(targetPeerAddress);
               targetPeer = Optional.of(targetPeerAddress);
            }
            activeFrame = peer.receive(frameHeader, sourcePeer, targetPeer, header.getShort() & 0xFFFF);
            // Reset header stuff
            frameHeader = 0;
            header.reset();
         }
      }
      // At this point, there is an active frame, so read message until
      // its fully read, at which point reset reading
      if (activeFrame != null) {
         return activeFrame
            .thenCompose(frame -> frame.receive(message))
            .thenAccept(done -> {
               if (done) {
                  closeFrame();
               }
            });
      }
      // This point means the message is read entirely, there was no active frame yet
      return CompletableFuture.completedFuture(null);
   }

   private synchronized void closeFrame() {
      activeFrame = null;
   }

   @Override
   public void close() {
      peer.close();
   }
}
