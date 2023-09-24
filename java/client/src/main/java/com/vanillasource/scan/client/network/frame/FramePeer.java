package com.vanillasource.scan.client.network.frame;

import com.vanillasource.scan.client.network.data.VariableLengthInteger;

import java.nio.ByteBuffer;

public interface FramePeer {
   void initiateHandshake(String protocolName, ByteBuffer handshake);

   void continueHandshake(ByteBuffer handshake);

   void closeConnection();

   void renegotiate();

   void ignoredFrame(int frameCode);

   void messageIntermediateFrame(VariableLengthInteger messageId, ByteBuffer payload);

   void messageLastFrame(VariableLengthInteger messageId, ByteBuffer payload);

   void messageSingleFrame(ByteBuffer payload);
}

