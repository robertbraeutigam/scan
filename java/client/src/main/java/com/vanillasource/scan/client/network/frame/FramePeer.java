package com.vanillasource.scan.client.network.frame;

import java.nio.ByteBuffer;

public interface FramePeer {
   void initiateHandshake(String protocolName, ByteBuffer handshake);

   void continueHandshake(ByteBuffer handshake);

   void closeConnection();

   void renegotiate();

   void ignoredFrame(int frameCode);

   void messageIntermediateFrame(int messageId, ByteBuffer payload);

   void messageLastFrame(int messageId, ByteBuffer payload);

   void messageSingleFrame(ByteBuffer payload);
}

