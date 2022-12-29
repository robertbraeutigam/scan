/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.physical.cache;

import com.vanillasource.scan.client.network.physical.PhysicalNetwork;
import com.vanillasource.scan.client.network.physical.PhysicalNetworkListener;
import com.vanillasource.scan.client.network.physical.PhysicalPeer;
import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * A physical network that caches and re-uses all physical connections.
 */
public final class CachingPhysicalNetwork implements PhysicalNetwork, PhysicalNetworkListener {
   private static final Logger LOGGER = LoggerFactory.getLogger(CachingPhysicalNetwork.class);
   private final PhysicalNetwork delegate;
   private final PhysicalNetworkListener delegateListener;
   private final Map<InetAddress, CompletableFuture<PhysicalPeer>> peers = new HashMap<>();

   public CachingPhysicalNetwork(Function<PhysicalNetworkListener, PhysicalNetwork> networkFactory, PhysicalNetworkListener listener) {
      this.delegate = networkFactory.apply(this);
      this.delegateListener = listener;
   }

   public CompletableFuture<Void> receiveMulticast(InetAddress sender, ByteBuffer packet) {
      return delegateListener.receiveMulticast(sender, packet);
   }

   @Override
   public CompletableFuture<PhysicalPeer> receiveConnection(InetAddress address, PhysicalPeer initiator) {
      boolean existed;
      synchronized (peers) {
         existed = peers.put(address, CompletableFuture.completedFuture(initiator)) != null;
      }
      if (existed) {
         LOGGER.warn("received connection from address {} which already had a connection, protocol violation", address);
      }
      return delegateListener.receiveConnection(address, initiator);
   }

   @Override
   public CompletableFuture<Void> sendMulticast(ByteBuffer packet) {
      return delegate.sendMulticast(packet);
   }

   @Override
   public CompletableFuture<PhysicalPeer> openConnection(InetAddress address, PhysicalPeer initiator) {
      synchronized (peers) {
         return peers.computeIfAbsent(address, a -> delegate.openConnection(address, initiator));
      }
   }

   @Override
   public void close() {
      delegate.close();
   }
}
