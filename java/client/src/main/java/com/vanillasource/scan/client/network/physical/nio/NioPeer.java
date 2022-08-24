package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.physical.PhysicalPeer;
import java.nio.channels.SocketChannel;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;

/**
 * A peer that represents one side of a physical connection.
 */
public final class NioPeer implements NioHandler, PhysicalPeer {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPeer.class);
   private final NioSelector selector;
   private final NioSelectorKey key;
   private final SocketChannel channel;
   private PhysicalPeer otherPeer = PhysicalPeer.UNCONNECTED;

   public NioPeer(NioSelector selector, SocketChannel channel) {
      this.selector = selector;
      this.channel = channel;

      this.key = selector.register(channel, this);
      key.enableConnect();
   }

   public NioPeer installPeer(PhysicalPeer otherPeer) {
      this.otherPeer = otherPeer;
      key.enableRead();
      return this;
   }

   @Override
   public void handleConnectable(NioSelectorKey key) throws IOException {
      try {
         boolean connected = channel.finishConnect();
         LOGGER.debug("connected incoming connection: {}", connected);
         if (!connected) {
            LOGGER.warn("channel reported not connectable");
            close();
         } else {
            key.disableConnect();
            key.enableRead();
         }
      } catch (IOException e) {
         LOGGER.warn("channel could not be connected", e);
         close();
      }
   }

   @Override
   public void handleAccept(NioSelectorKey key) throws IOException {
      // Not used.
   }

   @Override
   public void handleReadable(NioSelectorKey key) throws IOException {
      // TODO
   }

   @Override
   public void handleWritable(NioSelectorKey key) throws IOException {
      // TODO
   }

   @Override
   public CompletableFuture<Void> receive(ByteBuffer message) {
      // TODO
      return null;
   }

   @Override
   public void close() {
      try {
         key.cancel();
         otherPeer.close();
         channel.close();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }
}
