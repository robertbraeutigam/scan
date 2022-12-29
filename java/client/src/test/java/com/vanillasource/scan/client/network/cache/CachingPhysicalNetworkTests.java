package com.vanillasource.scan.client.network.physical.cache;

import org.testng.annotations.Test;
import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import org.testng.annotations.BeforeMethod;
import static org.mockito.Mockito.*;
import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import java.net.InetAddress;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Test
public final class CachingPhysicalNetworkTests {
   private PhysicalNetwork delegateNetwork;
   private PhysicalNetworkListener delegateListener;
   private CachingPhysicalNetwork network;

   public void testSecondOpenConnectionToSameHostDoesNotDelegate() throws IOException {
      network.openConnection(InetAddress.getLocalHost(), null);
      network.openConnection(InetAddress.getLocalHost(), null);

      verify(delegateNetwork, times(1)).openConnection(any(), any());
   }

   public void testDifferentHostsAlwaysDelegate() throws IOException {
      network.openConnection(InetAddress.getLocalHost(), null);
      network.openConnection(InetAddress.getByName("127.0.0.2"), null);

      verify(delegateNetwork, times(2)).openConnection(any(), any());
   }

   public void testOpenAfterReceivingConnectionDoesNotOpenAgain() throws IOException {
      network.receiveConnection(InetAddress.getLocalHost(), null);
      network.openConnection(InetAddress.getLocalHost(), null);

      verify(delegateNetwork, never()).openConnection(any(), any());
   }

   @BeforeMethod
   protected void setUp() {
      delegateNetwork = mock(PhysicalNetwork.class);
      when(delegateNetwork.openConnection(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
      delegateListener = mock(PhysicalNetworkListener.class);
      network = new CachingPhysicalNetwork(listener -> delegateNetwork, delegateListener);
   }
}
