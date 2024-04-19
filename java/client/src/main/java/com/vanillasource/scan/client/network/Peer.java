package com.vanillasource.scan.client.network;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * A peer that is connected through a logical connection.
 */
public interface Peer extends AutoCloseable {
   /**
    * Called to create a message.
    */
   Message create();

   /**
    * Send a message through this logical connection.
    */
   default void receive(ByteBuffer message) {
      create().endWith(message);
   }

   /**
    * Close this logical connection.
    */
   void close();

   default Peer afterClose(Consumer<Peer> action) {
      Peer self = this;
      return new Peer() {
         @Override
         public Message create() {
            return self.create();
         }

         @Override
         public void close() {
            self.close();
            action.accept(this);
         }
      };
   }

   Peer UNCONNECTED = new Peer() {
      @Override
      public Message create() {
         throw new IllegalStateException("peer not connected");
      }

      @Override
      public void close() {}
   };
}

