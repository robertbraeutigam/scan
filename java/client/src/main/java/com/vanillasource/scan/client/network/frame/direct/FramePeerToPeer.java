package com.vanillasource.scan.client.network.frame.direct;

import com.vanillasource.scan.client.network.Message;
import com.vanillasource.scan.client.network.Peer;
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
         private int messageId = -1;

         @Override
         public synchronized void recieve(ByteBuffer buffer) {
            if (messageId == -1) {
               messageId = messageIds.reserveId();
            }
            framePeer.messageIntermediateFrame(messageId, buffer);
         }

         @Override
         public synchronized void endWith(ByteBuffer buffer) {
            if (messageId != -1) {
               framePeer.messageLastFrame(messageId, buffer);
               messageIds.releaseId(messageId);
            } else {
               framePeer.messageSingleFrame(buffer);
            }
         }
      };
   }

   @Override
   public void close() {
      framePeer.closeConnection();
   }
}

