package com.vanillasource.scan.client.network.network;

import com.vanillasource.scan.client.network.Message;
import com.vanillasource.scan.client.network.Peer;
import com.vanillasource.scan.client.network.data.VariableLengthInteger;
import com.vanillasource.scan.client.network.frame.FramePeer;

import java.nio.ByteBuffer;

public final class FramePeerToPeer implements Peer {
   private final FramePeer framePeer;
   private final MessageIds messageIds;

   public FramePeerToPeer(MessageIds messageIds, FramePeer framePeer) {
      this.messageIds = messageIds;
      this.framePeer = framePeer;
   }

   @Override
   public Message create() {
      return new Message() {
         private VariableLengthInteger messageId = null;

         @Override
         public void recieve(ByteBuffer buffer) {
            if (messageId == null) {
               messageId = messageIds.reserveId();
            }
            framePeer.messageIntermediateFrame(messageId, buffer);
         }

         @Override
         public void endWith(ByteBuffer buffer) {
            if (messageId == null) {
               framePeer.messageSingleFrame(buffer);
            } else {
               framePeer.messageLastFrame(messageId, buffer);
               messageIds.releaseId(messageId);
            }
         }
      };
   }

   @Override
   public void close() {
      framePeer.closeConnection();
   }
}

