/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.physical;

import com.vanillasource.scan.client.network.frame.FramePeer;
import com.vanillasource.scan.client.network.frame.Frame;
import java.util.Optional;
import com.vanillasource.scan.client.network.physical.PhysicalPeer;
import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

public final class FrameFromPhysicalPeer implements FramePeer {
   private final PhysicalPeer peer;
   private final ByteBuffer header = ByteBuffer.allocate(1+32+32+2);
   private boolean frameActive = false;

   public FrameFromPhysicalPeer(PhysicalPeer peer) {
      this.peer = peer;
   }

   @Override
   public synchronized CompletableFuture<Frame> receive(int frameType, Optional<byte[]> sourcePeer, Optional<byte[]> targetPeer, int length) {
      if (frameActive) {
         throw new IllegalStateException("a frame is already active, frames need to be atomic");
      } else {
         frameActive = true;
         header
            .reset()
            .put(calculateHeader(frameType, sourcePeer, targetPeer));
         sourcePeer.map(header::put);
         targetPeer.map(header::put);
         header.putShort((short) (length & 0xFFFF));
         return peer.receive(header)
            .thenApply(ignore -> new LimitedFrame(length));
      }
   }

   private synchronized void closeFrame() {
      frameActive = false;
   }

   private byte calculateHeader(int frameType, Optional<byte[]> sourcePeer, Optional<byte[]> targetPeer) {
      return (byte) ((frameType + sourcePeer.map(b -> 64).orElse(0) + targetPeer.map(b -> 128).orElse(0)) & 0xFF);
   }

   @Override
   public void close() {
      peer.close();
   }

   private final class LimitedFrame implements Frame {
      private final int length;
      private int remainingLength;

      public LimitedFrame(int length) {
         this.length = length;
         this.remainingLength = length;
      }

      @Override
      public CompletableFuture<Boolean> receive(ByteBuffer buffer) {
         int bufferLength = buffer.remaining();
         if (remainingLength > 0) {
            remainingLength -= bufferLength;
            ByteBuffer cookedBuffer = buffer;
            if (remainingLength < 0) {
               // We need to limit the buffer to not write more than length
               cookedBuffer = buffer.slice();
               cookedBuffer.limit(cookedBuffer.limit() + remainingLength);
            }
            return peer.receive(cookedBuffer)
               .thenApply(ignore -> remainingLength > 0);
         } else {
            return CompletableFuture.completedFuture(true);
         }
      }

      @Override
      public void close() {
         closeFrame();
      }
   }
}
