package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import com.vanillasource.scan.client.network.Peer;
import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.nio.channels.Selector;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.NetworkInterface;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.net.SocketException;

public final class NioPhysicalNetwork implements PhysicalNetwork {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPhysicalNetwork.class);
   private static final InetSocketAddress MULTICAST_ADDRESS = new InetSocketAddress("239.255.255.244", 11372);
   private final Selector selector;
   private final DatagramChannel multicastChannel;
   private final PhysicalNetworkListener listener;
   private final Queue<OutgoingPacket> sendQueue = new ConcurrentLinkedQueue<>();
   private final CompletableFuture<Void> closed = new CompletableFuture<>();
   private final ByteBuffer datagramIncomingBuffer = ByteBuffer.allocateDirect(65535);
   private volatile boolean running = true;

   private NioPhysicalNetwork(Selector selector, DatagramChannel multicastChannel, PhysicalNetworkListener listener) {
      this.selector = selector;
      this.multicastChannel = multicastChannel;
      this.listener = listener;
   }

   /**
    * Constructs the physical network, connects all channels to appropriate ports,
    * joins appropriate multicast groups.
    */
   public static NioPhysicalNetwork startWith(PhysicalNetworkListener listener) throws IOException {
      Selector selector = Selector.open();

      InetAddress multicastGroup = MULTICAST_ADDRESS.getAddress();
      NetworkInterface networkInterface = findMulticastInterface();
      DatagramChannel multicastChannel = DatagramChannel.open(StandardProtocolFamily.INET)
         .setOption(StandardSocketOptions.SO_REUSEADDR, true)
         .bind(new InetSocketAddress(MULTICAST_ADDRESS.getPort()))
         .setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
      NioPhysicalNetwork network = new NioPhysicalNetwork(selector, multicastChannel, listener);
      multicastChannel.join(multicastGroup, networkInterface);
      multicastChannel.configureBlocking(false);
      multicastChannel.register(selector, multicastChannel.validOps(), new IOHandler() {
         @Override
         public CompletableFuture<Void> handle(SelectionKey key) {
            return network.handleMulticastIO(key);
         }
      });

      Thread thread = new Thread(network::select, "Scan Network Select");
      thread.start();

      return network;
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

   private CompletableFuture<Void> handleMulticastIO(SelectionKey key) {
      CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
      try {
         if (key.isReadable()) {
            SocketAddress address = multicastChannel.receive(datagramIncomingBuffer);
            if (address instanceof InetSocketAddress) {
               result = CompletableFuture.allOf(result, 
                     listener.receiveMulticast(((InetSocketAddress) address).getAddress(), datagramIncomingBuffer));
            } else {
               LOGGER.warn("address from multicast was not inet, but {}", address.getClass());
            }
         }
         if (key.isWritable()) {
            OutgoingPacket packet = sendQueue.peek();
            if (packet!=null && packet.send()) {
               sendQueue.remove();
            }
         }
      } catch (IOException e) {
         LOGGER.error("error while multicast i/o", e);
      }
      return result;
   }

   @Override
   public CompletableFuture<Void> sendMulticast(ByteBuffer packet) {
      CompletableFuture<Void> completion = new CompletableFuture<>();
      sendQueue.add(new OutgoingPacket(packet, completion));
      return completion;
   }

   @Override
   public CompletableFuture<Peer> openConnection(InetAddress peer, Peer initiator) {
      // TODO
      return null;
   }

   @Override
   public void close() {
      running = false;
      selector.wakeup();
      closed
         .whenComplete((result, exception) -> {
            try {
               selector.close();
            } catch (IOException e) {
               LOGGER.warn("selector couldn't be closed", e);
            }
            try {
               multicastChannel.close();
            } catch (IOException e) {
               LOGGER.warn("multicast channel couldn't be closed", e);
            }
         })
      .join();
   }

   private void select() {
      try {
         while (running) {
            int changedKeys = selector.select(1000); // 1 sec
            Iterator<SelectionKey> keysIterator = selector.selectedKeys().iterator();
            while (keysIterator.hasNext()) {
               SelectionKey key = keysIterator.next();
               int interestOps = key.interestOps();
               try {
                  ((IOHandler) key.attachment()).handle(key)
                     .whenComplete((result, exception) -> {
                        key.interestOps(interestOps);
                        if (exception != null) {
                           LOGGER.warn("i/o handler async exception", exception);
                        }
                     });
               } catch (RuntimeException e) {
                  key.interestOps(interestOps);
                  LOGGER.warn("i/o handler sync exception", e);
               }
               keysIterator.remove();
            }
         }
      } catch (Throwable e) {
         closed.completeExceptionally(e);
         close();
      } finally {
         closed.complete(null);
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
      public CompletableFuture<Void> handle(SelectionKey key);
   }
}

