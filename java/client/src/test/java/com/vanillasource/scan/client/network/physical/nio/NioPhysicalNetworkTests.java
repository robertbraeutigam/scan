package com.vanillasource.scan.client.network.physical.nio;

import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import static org.mockito.Mockito.*;
import java.io.IOException;
import org.testng.annotations.AfterMethod;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@Test
public class NioPhysicalNetworkTests {
   private PhysicalNetwork network1;
   private PhysicalNetworkListener listener1;
   private PhysicalNetwork network2;
   private PhysicalNetworkListener listener2;

   public void testMessageSentIsReceived() {
      network1.sendMulticast(ByteBuffer.wrap(new byte[] { 1, 2, 3 })).join();

      verify(listener1, timeout(200)).receiveMulticast(any(), any());
      verify(listener2, timeout(200)).receiveMulticast(any(), any());
   }

   @BeforeMethod
   protected void setUp() throws IOException {
      listener1 = mock(PhysicalNetworkListener.class);
      when(listener1.receiveMulticast(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
      network1 = NioPhysicalNetwork.startWith(listener1);
      listener2 = mock(PhysicalNetworkListener.class);
      when(listener2.receiveMulticast(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
      network2 = NioPhysicalNetwork.startWith(listener2);
   }

   @AfterMethod
   protected void tearDown() {
      network1.close();
      network2.close();
   }
}
