/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * A peer that is connected through a logical connection.
 */
public interface Peer {
   /**
    * Called to create an message.
    */
   Message create();

   /**
    * Send a message through this logical connection.
    * @return A future that completes when the message is sent to
    * the network.
    */
   default CompletableFuture<Void> receive(ByteBuffer message) {
      return create().endWith(message);
   }

   /**
    * Close this logical connection.
    * @return A future that completes when all pending data is sent
    * to the network.
    */
   CompletableFuture<Void> close();

   default Peer afterClose(Function<Peer, CompletableFuture<Void>> action) {
      Peer self = this;
      return new Peer() {
         @Override
         public Message create() {
            return self.create();
         }

         @Override
         public CompletableFuture<Void> close() {
            return self.close()
               .thenCompose(ignore -> action.apply(this));
         }
      };
   }

   static Peer UNCONNECTED = new Peer() {
      @Override
      public Message create() {
         throw new IllegalStateException("peer not connected");
      }

      @Override
      public CompletableFuture<Void> close() {
         return CompletableFuture.completedFuture(null);
      }
   };
}

