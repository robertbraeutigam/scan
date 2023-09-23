package com.vanillasource.scan.client.network.physical;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * A peer that is connected through a logical connection and can send any number of bytes.
 */
public interface PhysicalPeer extends AutoCloseable {
   /**
    * Send some bytes through this physical connection.
    * @return A future that completes when the buffer is fully sent.
    */
   void receive(ByteBuffer message);

   /**
    * Close this logical connection.
    * @return A future that completes when all pending data is sent
    * to the network.
    */
   void close();

   default PhysicalPeer afterClose(Consumer<PhysicalPeer> action) {
      PhysicalPeer self = this;
      return new PhysicalPeer() {
         @Override
         public void receive(ByteBuffer message) {
            self.receive(message);
         }

         @Override
         public void close() {
            self.close();
            action.accept(this);
         }
      };
   }

   static PhysicalPeer UNCONNECTED = new PhysicalPeer() {
      @Override
      public void receive(ByteBuffer message) {
         throw new IllegalStateException("peer not connected");
      }

      @Override
      public void close() {
      }
   };
}

