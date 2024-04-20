package com.vanillasource.scan.client.network.network;

import com.vanillasource.scan.client.network.Message;
import com.vanillasource.scan.client.network.Peer;
import com.vanillasource.scan.client.network.data.VariableLengthInteger;
import com.vanillasource.scan.client.network.frame.FramePeer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public final class PeerToFramePeer implements FramePeer {
   private final Peer peer;
   private final Map<VariableLengthInteger, Message> messages = new HashMap<>();

   public PeerToFramePeer(Peer peer) {
      this.peer = peer;
   }

   @Override
   public void initiateHandshake(String protocolName, ByteBuffer handshake) {
      // User is not interested in this callback
   }

   @Override
   public void continueHandshake(ByteBuffer handshake) {
      // User is not interested in this callback
   }

   @Override
   public void closeConnection() {
      // User is not interested in this callback
   }

   @Override
   public void renegotiate() {
      // User is not interested in this callback
   }

   @Override
   public void ignoredFrame(int frameCode) {
      // User is not interested in this callback
   }

   @Override
   public void keepAlive() {
      // User is not interested in this callback
   }

   @Override
   public void messageIntermediateFrame(VariableLengthInteger messageId, ByteBuffer payload) {
      messages
              .computeIfAbsent(messageId, id -> peer.create())
              .recieve(payload);
   }

   @Override
   public void messageLastFrame(VariableLengthInteger messageId, ByteBuffer payload) {
      messages.compute(messageId, (id, message) -> {
         if (message == null) {
            peer.receive(payload);
         } else {
            message.endWith(payload);
         }
         return null;
      });
   }

   @Override
   public void messageSingleFrame(ByteBuffer payload) {
      peer.receive(payload);
   }
}
