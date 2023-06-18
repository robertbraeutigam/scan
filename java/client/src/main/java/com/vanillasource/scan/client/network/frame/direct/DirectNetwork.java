/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import com.vanillasource.scan.client.network.Network;
import com.vanillasource.scan.client.network.frame.FrameNetwork;
import com.vanillasource.scan.client.network.frame.FrameNetworkListener;
import com.vanillasource.scan.client.network.NetworkListener;
import com.vanillasource.scan.client.network.Peer;
import com.vanillasource.scan.client.network.Role;
import com.vanillasource.scan.client.network.PeerAddress;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import com.vanillasource.scan.client.network.frame.FramePeer;

/**
 * A network that directly converts all messages into message frames.
 */
public final class DirectNetwork implements Network, FrameNetworkListener {
   private final FrameNetwork delegate;
   private final NetworkListener listener;
   private final QueryIds queryIds;
   private final Supplier<MessageIds> messageIdsFactory;

   public DirectNetwork(QueryIds queryIds, Supplier<MessageIds> messageIdsFactory, NetworkListener listener, Function<FrameNetworkListener, FrameNetwork> delegateFactory) {
      this.listener = listener;
      this.queryIds = queryIds;
      this.messageIdsFactory = messageIdsFactory;
      this.delegate = delegateFactory.apply(this);
   }

   @Override
   public CompletableFuture<Void> queryAll() {
      return queryIds.nextQueryId()
         .thenCompose(id -> delegate.identityQuery(id, new PeerAddress[] {}));
   }

   @Override
   public CompletableFuture<Void> close() {
      return delegate.close();
   }

   @Override
   public CompletableFuture<Peer> connect(PeerAddress address, Role role, Peer initiator) {
      return delegate.connect(address, role, new PeerToFramePeer(initiator))
         .thenApply(peer -> new FramePeerToPeer(messageIdsFactory.get(), peer));
   }

   @Override
   public void receiveAnnouncement(PeerAddress address) {
      listener.receiveAnnouncement(address);
   }

   @Override
   public CompletableFuture<FramePeer> receiveConnection(PeerAddress address, Role role, FramePeer initiator) {
      return listener.receiveConnection(address, role, new FramePeerToPeer(messageIdsFactory.get(), initiator))
         .thenApply(PeerToFramePeer::new);
   }
}


