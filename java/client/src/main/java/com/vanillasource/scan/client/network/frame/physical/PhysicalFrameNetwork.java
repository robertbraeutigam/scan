/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.physical;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.net.InetAddress;
import com.vanillasource.scan.client.network.frame.FrameNetwork;
import com.vanillasource.scan.client.network.frame.FramePeer;
import com.vanillasource.scan.client.network.frame.FrameNetworkListener;
import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import java.util.function.Function;
import com.vanillasource.scan.client.network.physical.PhysicalPeer;

/**
 * A frame network that runs directly on a physical network.
 */
public final class PhysicalFrameNetwork implements FrameNetwork, PhysicalNetworkListener {
   private final PhysicalNetwork physicalNetwork;
   private final FrameNetworkListener listener;

   public PhysicalFrameNetwork(Function<PhysicalNetworkListener, PhysicalNetwork> networkFactory, FrameNetworkListener listener) {
      this.physicalNetwork = networkFactory.apply(this);
      this.listener = listener;
   }

   @Override
   public CompletableFuture<Void> sendMulticast(ByteBuffer packet) {
      return physicalNetwork.sendMulticast(packet);
   }

   @Override
   public CompletableFuture<FramePeer> openConnection(InetAddress peer, FramePeer initiator) {
      return physicalNetwork.openConnection(peer, new PhysicalFromFramePeer(initiator))
         .thenApply(FrameFromPhysicalPeer::new);
   }

   @Override
   public CompletableFuture<Void> receiveMulticast(InetAddress sender, ByteBuffer packet) {
      return listener.receiveMulticast(sender, packet);
   }

   @Override
   public CompletableFuture<PhysicalPeer> receiveConnection(InetAddress address, PhysicalPeer initiator) {
      return listener.receiveConnection(address, new FrameFromPhysicalPeer(initiator))
         .thenApply(PhysicalFromFramePeer::new);
   }

   @Override
   public void close() {
      physicalNetwork.close();
   }
}
