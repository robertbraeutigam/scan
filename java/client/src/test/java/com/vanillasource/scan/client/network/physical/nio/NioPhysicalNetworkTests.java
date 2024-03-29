package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import com.vanillasource.scan.client.network.physical.PhysicalPeer;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import static org.testng.Assert.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import org.testng.annotations.AfterMethod;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.net.InetAddress;
import org.mockito.ArgumentCaptor;

@Test
public class NioPhysicalNetworkTests {
   private static final Logger LOGGER = LoggerFactory.getLogger(NioPhysicalNetworkTests.class);
   private PhysicalPeer initiator;
   private PhysicalPeer acceptingPeer;
   private PhysicalNetwork network;
   private PhysicalNetworkListener listener;

   public void testMessageSentIsReceived() {
      network.sendMulticast(ByteBuffer.wrap(new byte[] { 1, 2, 3 })).join();

      assertMulticastReceive(new byte[] { 1, 2, 3 });
   }

   public void testReceiverDoesNotGetCalledUntilFinished() {
      when(listener.receiveMulticast(any(), any())).thenReturn(new CompletableFuture<>());

      LOGGER.debug("sending multicast messages...");
      network.sendMulticast(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
      network.sendMulticast(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));

      LOGGER.debug("verifying...");
      verify(listener, timeout(100).times(1)).receiveMulticast(any(), any());
   }

   public void testCanReceiveMulticastInQuickSuccession() throws InterruptedException {
      CompletableFuture<Void> receiveMessage1 = CompletableFuture.completedFuture(null);
      when(listener.receiveMulticast(any(), any()))
         .thenReturn(receiveMessage1)
         .thenReturn(new CompletableFuture<>());

      network.sendMulticast(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
      Thread.sleep(20);
      receiveMessage1.completedFuture(null);
      network.sendMulticast(ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 }));

      verify(listener, timeout(100).times(2)).receiveMulticast(any(), any());
   }

   public void testCanReceiveMultipleMulticastPackets() {
      network.sendMulticast(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
      assertMulticastReceive(new byte[] { 1, 2, 3 });

      network.sendMulticast(ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 }));
      assertMulticastReceive(new byte[] { 4, 5, 6, 7 });
   }

   public void testOpenedPeersAreClosedWhenNetworkCloses() throws Exception {
      network.openConnection(InetAddress.getLocalHost(), initiator);

      network.close();

      verify(initiator, atLeastOnce()).close();
   }

   public void testOpenConnectionArrivesInListener() throws Exception {
      network.openConnection(InetAddress.getLocalHost(), initiator);

      verify(listener, timeout(100)).receiveConnection(any(), any());
   }

   public void testInitiatorCanSendToResponder() throws Exception {
      PhysicalPeer initiatorsResponderView = network.openConnection(InetAddress.getLocalHost(), initiator).join();

      initiatorsResponderView.receive(ByteBuffer.wrap(new byte[] { 1, 2 ,3 }));

      assertResponderReceive(new byte[] { 1, 2, 3 });
   }

   public void testInitiatorCanSendTwiceToResponder() throws Exception {
      PhysicalPeer initiatorsResponderView = network.openConnection(InetAddress.getLocalHost(), initiator).join();

      // First send
      initiatorsResponderView.receive(ByteBuffer.wrap(new byte[] { 1, 2 ,3 }));
      assertResponderReceive(new byte[] { 1, 2, 3 });
      
      // Second send
      initiatorsResponderView.receive(ByteBuffer.wrap(new byte[] { 4, 5 ,6, 7 }));
      assertResponderReceive(new byte[] { 4, 5, 6, 7 });
   }

   private void assertResponderReceive(byte[] data) {
      reset(acceptingPeer);
      when(acceptingPeer.close()).thenReturn(CompletableFuture.completedFuture(null));

      CompletableFuture<Void> responderReadFuture = new CompletableFuture<>();
      when(acceptingPeer.receive(any())).thenReturn(responderReadFuture);

      ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
      verify(acceptingPeer, timeout(100).atLeastOnce()).receive(bufferCaptor.capture());
      ByteBuffer receivedBuffer = bufferCaptor.getValue();
      LOGGER.debug("received buffer {}", receivedBuffer);
      assertEquals(receivedBuffer.remaining(), data.length);
      for (int i=0; i<data.length; i++) {
          assertEquals(receivedBuffer.get(), data[i]);
      }

      responderReadFuture.complete(null);
   }

   private void assertMulticastReceive(byte[] data) {
      reset(listener);

      CompletableFuture<Void> listenerFuture = new CompletableFuture<>();
      when(listener.receiveMulticast(any(), any())).thenReturn(listenerFuture);

      ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
      verify(listener, timeout(100).atLeastOnce()).receiveMulticast(any(), bufferCaptor.capture());
      ByteBuffer receivedBuffer = bufferCaptor.getValue();
      LOGGER.debug("received buffer {}", receivedBuffer);
      assertEquals(receivedBuffer.remaining(), data.length);
      for (int i=0; i<data.length; i++) {
          assertEquals(receivedBuffer.get(), data[i]);
      }

      listenerFuture.complete(null);
   }

   @BeforeMethod
   protected void setUp() throws IOException {
      listener = mock(PhysicalNetworkListener.class);
      acceptingPeer = mock(PhysicalPeer.class);
      when(acceptingPeer.close()).thenReturn(CompletableFuture.completedFuture(null));
      when(listener.receiveConnection(any(), any())).thenReturn(CompletableFuture.completedFuture(acceptingPeer));
      LOGGER.trace("starting nio physical network...");
      network = NioPhysicalNetwork.startWith(listener);
      LOGGER.trace("started nio physical network...");
      initiator = mock(PhysicalPeer.class);
      when(initiator.close()).thenReturn(CompletableFuture.completedFuture(null));
   }

   @AfterMethod
   protected void tearDown() {
      network.close();
   }
}
