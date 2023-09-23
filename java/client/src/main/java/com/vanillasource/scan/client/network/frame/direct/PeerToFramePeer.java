package com.vanillasource.scan.client.network.frame.direct;

import com.vanillasource.scan.client.network.Peer;
import com.vanillasource.scan.client.network.frame.FramePeer;

import java.nio.ByteBuffer;

public final class PeerToFramePeer implements FramePeer {
   private final Peer peer;

   public PeerToFramePeer(Peer peer) {
      this.peer = peer;
   }

   @Override
   public void initiateHandshake(String protocolName, ByteBuffer handshake) {
      // TODO
   }

   @Override
   public void continueHandshake(ByteBuffer handshake) {
      // TODO
   }

   @Override
   public void closeConnection() {
      // TODO
   }

   @Override
   public void renegotiate() {
      // TODO
   }

   @Override
   public void ignoredFrame(int frameCode) {
      // TODO
   }

   @Override
   public void messageIntermediateFrame(int messageId, ByteBuffer payload) {
      // TODO
   }

   @Override
   public void messageLastFrame(int messageId, ByteBuffer payload) {
      // TODO
   }

   @Override
   public void messageSingleFrame(ByteBuffer payload) {
      // TODO
   }
}
