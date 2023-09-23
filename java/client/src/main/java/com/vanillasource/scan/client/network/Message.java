package com.vanillasource.scan.client.network;

import java.nio.ByteBuffer;

public interface Message {
   /**
    * Receive the given bytes to this message.
    */
   void recieve(ByteBuffer buffer);

   /**
    * Close this message with the last part supplied as argument.
    * @return A future that completes when all pending bytes are sent
    * to the network.
    */
   void endWith(ByteBuffer buffer);

   /**
    * Close this message, there will be no more parts of this message.
    * @return A future that completes when all pending bytes are processed.
    */
   default void end() {
      endWith(ByteBuffer.wrap(new byte[] {}));
   }
}

