package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import com.vanillasource.scan.client.network.Peer;
import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.NetworkInterface;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.channels.SelectionKey;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.net.SocketException;
import java.util.LinkedList;
import java.nio.channels.SocketChannel;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public final class NioPhysicalNetwork implements PhysicalNetwork, NioHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPhysicalNetwork.class);
   private static final int SCAN_PORT = 11372;
   private static final InetSocketAddress MULTICAST_ADDRESS = new InetSocketAddress("239.255.255.244", SCAN_PORT);
   private final NioSelector selector;
   private final NioSelectorKey multicastKey;
   private final DatagramChannel multicastChannel;
   private final PhysicalNetworkListener listener;
   private final List<Peer> peers = new ArrayList<>();
   private final Queue<OutgoingPacket> sendQueue = new LinkedList<>();
   private final ByteBuffer datagramIncomingBuffer = ByteBuffer.allocateDirect(65535);

   private NioPhysicalNetwork(NioSelector selector, DatagramChannel multicastChannel, PhysicalNetworkListener listener) {
      this.selector = selector;
      this.multicastKey = selector.register(multicastChannel, this);
      this.multicastKey.enableRead();
      this.multicastChannel = multicastChannel;
      this.listener = listener;
   }

   /**
    * Constructs the physical network, connects all channels to appropriate ports,
    * joins appropriate multicast groups.
    */
   public static NioPhysicalNetwork startWith(PhysicalNetworkListener listener) throws IOException {
      NioSelector selector = NioSelector.create();

      InetAddress multicastGroup = MULTICAST_ADDRESS.getAddress();
      NetworkInterface networkInterface = findMulticastInterface();
      DatagramChannel multicastChannel = DatagramChannel.open(StandardProtocolFamily.INET)
         .setOption(StandardSocketOptions.SO_REUSEADDR, true)
         .bind(new InetSocketAddress(MULTICAST_ADDRESS.getPort()))
         .setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
      multicastChannel.join(multicastGroup, networkInterface);
      multicastChannel.configureBlocking(false);

      return new NioPhysicalNetwork(selector, multicastChannel, listener);
   }

   private static NetworkInterface findMulticastInterface() throws SocketException {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
         NetworkInterface networkInterface = interfaces.nextElement();
         if (!networkInterface.isLoopback() && networkInterface.isUp() && networkInterface.supportsMulticast()) {
            return networkInterface;
         }
      }
      return null;
   }

   @Override
   public void handleConnectable(NioSelectorKey key) throws IOException {
      // Not used
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
   public void handleReadable(NioSelectorKey key) throws IOException {
      SocketAddress address = multicastChannel.receive(datagramIncomingBuffer);
      if (address instanceof InetSocketAddress) {
         LOGGER.trace("reading, disable read");
         key.disableRead();
         listener.receiveMulticast(((InetSocketAddress) address).getAddress(), datagramIncomingBuffer)
            .whenComplete((result, exception) -> {
               LOGGER.trace("reading finished, enable read");
               key.enableRead();
               if (exception != null) {
                  selector.closeExceptionally(exception);
               }
            });
      } else {
         LOGGER.warn("address from multicast was not inet, but {}", address.getClass());
      }
   }

   @Override
   public CompletableFuture<Void> sendMulticast(ByteBuffer packet) {
      CompletableFuture<Void> completion = new CompletableFuture<>();
      LOGGER.trace("adding packet to be written...");
      selector.onSelectionThread(() -> {
         sendQueue.add(new OutgoingPacket(packet, completion));
         multicastKey.enableWrite();
      });
      return completion;
   }

   @Override
   public CompletableFuture<Peer> openConnection(InetAddress address, Peer initiator) {
      try {
         SocketChannel channel = SocketChannel.open();
         channel.configureBlocking(false);
         channel.connect(new InetSocketAddress(address, SCAN_PORT));
         Peer peer = new NioPeer(selector, channel, initiator);
         synchronized (peers) {
            peers.add(peer);
         }
         return CompletableFuture.completedFuture(peer.afterClose(() -> {
            synchronized (peers) {
               peers.remove(peer);
            }
         }));
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   @Override
   public void close() {
      synchronized (peers) {
         peers.forEach(Peer::close);
      }
      selector.close();
      try {
         multicastChannel.close();
      } catch (IOException e) {
         LOGGER.warn("multicast channel couldn't be closed", e);
      }
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
            multicastChannel.send(buffer, MULTICAST_ADDRESS);
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

   private interface IOHandler {
      public void handle(SelectionKey key) throws IOException;
   }
}

