package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.Peer;
import java.nio.channels.SocketChannel;
import java.io.IOException;
import com.vanillasource.scan.client.network.Message;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.io.UncheckedIOException;

public final class NioPeer implements NioHandler, Peer {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPeer.class);
   private final NioSelector selector;
   private final NioSelectorKey key;
   private final SocketChannel channel;
   private final Peer initiator;

   public NioPeer(NioSelector selector, SocketChannel channel, Peer initiator) {
      this.selector = selector;
      this.channel = channel;
      this.initiator = initiator;

      this.key = selector.register(channel, this);
      key.enableConnect();
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
   public void handleReadable(NioSelectorKey key) throws IOException {
      // TODO
   }

   @Override
   public void handleWritable(NioSelectorKey key) throws IOException {
      // TODO
   }

   @Override
   public Message create() {
      return null; // TODO
   }

   @Override
   public void close() {
      try {
         key.cancel();
         initiator.close();
         channel.close();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }
}
