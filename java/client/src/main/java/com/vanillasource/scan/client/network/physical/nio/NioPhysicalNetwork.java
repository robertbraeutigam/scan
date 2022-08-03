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
import java.util.LinkedList;

public final class NioPhysicalNetwork implements PhysicalNetwork {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPhysicalNetwork.class);
   private static final InetSocketAddress MULTICAST_ADDRESS = new InetSocketAddress("239.255.255.244", 11372);
   private final Selector selector;
   private final DatagramChannel multicastChannel;
   private final PhysicalNetworkListener listener;
   private final Queue<OutgoingPacket> sendQueue = new LinkedList<>();
   private final Queue<Runnable> selectorJobs = new ConcurrentLinkedQueue<>();
   private final CompletableFuture<Void> closed = new CompletableFuture<>();
   private final ByteBuffer datagramIncomingBuffer = ByteBuffer.allocateDirect(65535);
   private final SelectionKey multicastKey;
   private volatile boolean running = true;

   private NioPhysicalNetwork(Selector selector, SelectionKey multicastKey, DatagramChannel multicastChannel, PhysicalNetworkListener listener) {
      this.selector = selector;
      this.multicastKey = multicastKey;
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
      multicastChannel.join(multicastGroup, networkInterface);
      multicastChannel.configureBlocking(false);
      SelectionKey multicastKey = multicastChannel.register(selector, SelectionKey.OP_READ);
      NioPhysicalNetwork network = new NioPhysicalNetwork(selector, multicastKey, multicastChannel, listener);
      multicastKey.attach(new IOHandler() {
         @Override
         public void handle(SelectionKey key) throws IOException {
            network.handleMulticastIO(key);
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

   private void handleMulticastIO(SelectionKey key) throws IOException {
      if (key.isReadable()) {
         SocketAddress address = multicastChannel.receive(datagramIncomingBuffer);
         if (address instanceof InetSocketAddress) {
            LOGGER.trace("reading, removing OP_READ");
            key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
            listener.receiveMulticast(((InetSocketAddress) address).getAddress(), datagramIncomingBuffer)
               .whenComplete((result, exception) -> {
                  LOGGER.trace("reading finished, adding OP_READ");
                  selectorJobs.add(() -> key.interestOps(key.interestOps() | SelectionKey.OP_READ));
                  if (exception != null) {
                     selectorJobs.add(() -> new IllegalStateException("i/o handler async exception", exception));
                  }
                  selector.wakeup();
               });
         } else {
            LOGGER.warn("address from multicast was not inet, but {}", address.getClass());
         }
      }
      if (key.isWritable()) {
         OutgoingPacket packet = sendQueue.peek();
         if (packet!=null && packet.send()) {
            sendQueue.remove();
            if (sendQueue.isEmpty()) {
               LOGGER.trace("send queue empty, removing OP_WRITE");
               key.interestOps(key.interestOps() & (~SelectionKey.OP_WRITE));
            }
         }
      }
   }

   @Override
   public CompletableFuture<Void> sendMulticast(ByteBuffer packet) {
      CompletableFuture<Void> completion = new CompletableFuture<>();
      selectorJobs.add(() -> {
         sendQueue.add(new OutgoingPacket(packet, completion));
         multicastKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
      });
      selector.wakeup();
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
            LOGGER.trace("selecting...");
            int changedKeys = selector.select(1000); // 1 sec
            Iterator<SelectionKey> keysIterator = selector.selectedKeys().iterator();
            // Handle keys
            while (keysIterator.hasNext()) {
               SelectionKey key = keysIterator.next();
               LOGGER.trace("read ops {}, calling handler", key.readyOps());
               ((IOHandler) key.attachment()).handle(key);
               keysIterator.remove();
            }
            // Execute jobs
            LOGGER.trace("executing all selector jobs...");
            Iterator<Runnable> selectorJobsIterator = selectorJobs.iterator();
            while (selectorJobsIterator.hasNext()) {
               Runnable selectorJob = selectorJobsIterator.next();
               selectorJob.run();
               selectorJobsIterator.remove();
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
      public void handle(SelectionKey key) throws IOException;
   }
}

