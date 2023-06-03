package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.physical.PhysicalPeer;
import java.nio.channels.SocketChannel;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A peer that represents one side of a physical connection.
 */
public final class NioPeer implements NioHandler, PhysicalPeer {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPeer.class);
   private final NioSelector selector;
   private final NioSelectorKey key;
   private final SocketChannel channel;
   private final ByteBuffer incomingBuffer = ByteBuffer.allocateDirect(65535);
   private final Queue<OutgoingPacket> sendQueue = new LinkedList<>();
   private PhysicalPeer otherPeer = PhysicalPeer.UNCONNECTED;

   public NioPeer(NioSelector selector, SocketChannel channel) {
      this.selector = selector;
      this.channel = channel;

      this.key = selector.register(channel, this);
      if (!channel.isConnected()) {
         key.enableConnect();
      }
   }

   public NioPeer installPeer(PhysicalPeer otherPeer) {
      this.otherPeer = otherPeer;
      key.enableRead();
      return this;
   }

   @Override
   public String toString() {
      return "Peer("+channel+")";
   }

   @Override
   public void handleConnectable(NioSelectorKey key) throws IOException {
      try {
         boolean connected = channel.finishConnect();
         if (!connected) {
            LOGGER.warn("channel reported not connectable");
            close()
               .whenComplete((ignore, exception) -> LOGGER.error("error while closing peer in background", exception));
         } else {
            LOGGER.debug("connected incoming connection");
            key.disableConnect();
         }
      } catch (IOException e) {
         LOGGER.warn("channel could not be connected", e);
         close()
            .whenComplete((ignore, exception) -> LOGGER.error("error while closing peer in background", exception));
      }
   }

   @Override
   public void handleAccept(NioSelectorKey key) throws IOException {
      // Not used.
   }

   @Override
   public void handleReadable(NioSelectorKey key) throws IOException {
      LOGGER.trace("reading, disable read");
      key.disableRead();
      channel.read(incomingBuffer);
      incomingBuffer.flip();
      otherPeer.receive(incomingBuffer)
         .whenComplete((result, exception) -> {
            LOGGER.trace("reading finished, enable read");
            incomingBuffer.clear();
            key.enableRead();
            if (exception != null) {
               LOGGER.warn("handling reading resulted in exception", exception);
            }
         });
   }

   @Override
   public void handleWritable(NioSelectorKey key) throws IOException {
      OutgoingPacket packet = sendQueue.peek();
      if (packet!=null && packet.send()) {
         LOGGER.trace("data written");
         sendQueue.remove();
      }
      if (sendQueue.isEmpty()) {
         LOGGER.trace("send queue empty, disable write");
         key.disableWrite();
      }
   }

   @Override
   public CompletableFuture<Void> receive(ByteBuffer message) {
      CompletableFuture<Void> completion = new CompletableFuture<>();
      LOGGER.trace("adding packet to be written...");
      selector.onSelectionThread(() -> {
         sendQueue.add(new OutgoingPacket(message, completion));
         key.enableWrite();
      });
      return completion;
   }

   @Override
   public CompletableFuture<Void> close() {
      CompletableFuture<Void> closeFuture = otherPeer.close();
      LOGGER.debug("other peer ("+otherPeer+") close returning: "+closeFuture);
      return closeFuture
         .thenCompose(ignore -> selector.onSelectionThread(() -> {
            key.cancel();
            try {
               channel.close();
            } catch (IOException e) {
               throw new UncheckedIOException(e);
            }
            return null;
         }));
   }

   private final class OutgoingPacket {
      private final ByteBuffer buffer;
      private final CompletableFuture<Void> completion;

      public OutgoingPacket(ByteBuffer buffer, CompletableFuture<Void> completion) {
         this.buffer = buffer;
         this.completion = completion;
      }

      public boolean send() {
         try {
            channel.write(buffer);
            if (buffer.remaining() == 0) {
               completion.complete(null);
               return true;
            } else {
               return false;
            }
         } catch (IOException e) {
            completion.completeExceptionally(e);
            return true;
         }
      }
   }
}
