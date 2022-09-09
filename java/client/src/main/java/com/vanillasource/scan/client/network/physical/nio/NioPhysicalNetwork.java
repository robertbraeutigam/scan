package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import com.vanillasource.scan.client.network.physical.PhysicalPeer;
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
import java.net.SocketAddress;
import java.util.Enumeration;
import java.net.SocketException;
import java.util.LinkedList;
import java.nio.channels.SocketChannel;
import java.io.UncheckedIOException;
import java.nio.channels.ServerSocketChannel;
import java.util.Deque;
import java.util.ArrayDeque;

public final class NioPhysicalNetwork implements PhysicalNetwork, NioHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPhysicalNetwork.class);
   private static final int SCAN_PORT = 11372;
   private static final InetSocketAddress MULTICAST_ADDRESS = new InetSocketAddress("239.255.255.244", SCAN_PORT);
   private final NioSelector selector;
   private final NioSelectorKey multicastKey;
   private final DatagramChannel multicastChannel;
   private final ServerSocketChannel serverChannel;
   private final NioSelectorKey serverChannelKey;
   private final PhysicalNetworkListener listener;
   private final Deque<PhysicalPeer> peers = new ArrayDeque<>();
   private final Queue<OutgoingPacket> sendQueue = new LinkedList<>();
   private final ByteBuffer datagramIncomingBuffer = ByteBuffer.allocateDirect(65535);

   private NioPhysicalNetwork(NioSelector selector, ServerSocketChannel serverChannel, DatagramChannel multicastChannel, PhysicalNetworkListener listener) {
      this.selector = selector;
      this.multicastChannel = multicastChannel;
      this.multicastKey = selector.register(multicastChannel, this);
      this.multicastKey.enableRead();
      this.serverChannel = serverChannel;
      this.serverChannelKey = selector.register(serverChannel, this);
      this.serverChannelKey.enableAccept();
      this.listener = listener;
   }

   @Override
   public String toString() {
      return "Network";
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

      ServerSocketChannel serverChannel = ServerSocketChannel.open();
      serverChannel.configureBlocking(false);
      serverChannel.bind(new InetSocketAddress((InetAddress)null, SCAN_PORT));

      return new NioPhysicalNetwork(selector, serverChannel, multicastChannel, listener);
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
   public void handleAccept(NioSelectorKey key) throws IOException {
      LOGGER.trace("accepting connection...");
      SocketChannel channel = serverChannel.accept();
      channel.configureBlocking(false);
      NioPeer nioPeer = new NioPeer(selector, channel);
      PhysicalPeer peer = nioPeer.afterClose(p -> {
         synchronized (peers) {
            peers.remove(p);
         }
      });
      synchronized (peers) {
         peers.add(peer);
      }
      try {
         LOGGER.debug("accepted connection from {}", channel.getRemoteAddress());
         listener
            .receiveConnection(((InetSocketAddress)channel.getRemoteAddress()).getAddress(), peer)
            .whenComplete((result, exception) -> {
               if (result != null) {
                  LOGGER.debug("installing other side to accepted connection");
                  nioPeer.installPeer(result);
               }
               if (exception != null) {
                  LOGGER.warn("accepting connection returned error", exception);
               }
            });
         LOGGER.trace("accepted handled.");
      } catch (IOException e) {
         LOGGER.warn("could not accept the connection properly, ignoring", e);
         peer.close();
      }
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
         datagramIncomingBuffer.flip();
         listener.receiveMulticast(((InetSocketAddress) address).getAddress(), datagramIncomingBuffer)
            .whenComplete((result, exception) -> {
               LOGGER.trace("reading finished, enable read");
               datagramIncomingBuffer.clear();
               key.enableRead();
               if (exception != null) {
                  LOGGER.warn("handling multicast resulted in exception", exception);
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
   public CompletableFuture<PhysicalPeer> openConnection(InetAddress address, PhysicalPeer initiator) {
      try {
         SocketChannel channel = SocketChannel.open();
         channel.configureBlocking(false);
         channel.connect(new InetSocketAddress(address, SCAN_PORT));
         PhysicalPeer peer = new NioPeer(selector, channel)
            .installPeer(initiator)
            .afterClose(p -> {
               synchronized (peers) {
                  peers.remove(p);
               }
         });
         synchronized (peers) {
            peers.add(peer);
         }
         return CompletableFuture.completedFuture(peer);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   @Override
   public void close() {
      synchronized (peers) {
         while (!peers.isEmpty()) {
            peers.removeLast().close();
         }
      }
      selector.close();
      try {
         multicastChannel.close();
      } catch (IOException e) {
         LOGGER.warn("multicast channel couldn't be closed", e);
      }
      try {
         serverChannel.close();
      } catch (IOException e) {
         LOGGER.warn("server channel couldn't be closed", e);
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
}

