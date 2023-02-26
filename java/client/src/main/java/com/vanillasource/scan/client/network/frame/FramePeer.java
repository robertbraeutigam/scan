/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import java.util.function.Consumer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A peer that is connected through the network and is capable of recieving network frames.
 */
public interface FramePeer {
   /**
    * Send some bytes through this physical connection.
    * @param frameType The frame type, which is the 6 bits of the first byte of all frames.
    * @param sourcePeer The identification of the source peer.
    * @param targetPeer The identification of the target peer.
    * @param length The length of the coming frame.
    * @return A future that completes when the buffer is fully sent.
    */
   CompletableFuture<Frame> receive(int frameType, Optional<byte[]> sourcePeer, Optional<byte[]> targetPeer, int length);

   /**
    * Close this logical connection.
    * @return A future that completes when all pending data is sent
    * to the network.
    */
   void close();

   default FramePeer afterClose(Consumer<FramePeer> action) {
      FramePeer self = this;
      return new FramePeer() {
         @Override
         public CompletableFuture<Frame> receive(int frameType, Optional<byte[]> sourcePeer, Optional<byte[]> targetPeer, int length) {
            return self.receive(frameType, sourcePeer, targetPeer, length);
         }

         @Override
         public void close() {
            self.close();
            action.accept(this);
         }
      };
   }
}

