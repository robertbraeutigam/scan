package com.vanillasource.scan.client.network.network;

import com.vanillasource.scan.client.network.*;
import com.vanillasource.scan.client.network.frame.FrameNetwork;
import com.vanillasource.scan.client.network.frame.FrameNetworkListener;
import com.vanillasource.scan.client.network.frame.FramePeer;

import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Network} that uses a {@link FrameNetwork} for implementing sending and receiving
 * messages. It is expected that the used framed network will be configured to take care
 * of resolving, handshake, encryption, and other lower-level concerns.
 */
public final class FrameNetworkNetwork implements Network, FrameNetworkListener {
   private final FrameNetwork delegate;
   private final NetworkListener listener;
   private final QueryIds queryIds;
   private final Supplier<MessageIds> messageIdsFactory;

   public FrameNetworkNetwork(QueryIds queryIds, Supplier<MessageIds> messageIdsFactory, NetworkListener listener, Function<FrameNetworkListener, FrameNetwork> delegateFactory) {
      this.listener = listener;
      this.queryIds = queryIds;
      this.messageIdsFactory = messageIdsFactory;
      this.delegate = delegateFactory.apply(this);
   }

   @Override
   public void queryAll() {
      delegate.identityQuery(queryIds.nextQueryId(), Collections.emptyList());
   }

   @Override
   public void close() {
      delegate.close();
   }

   @Override
   public Peer connect(PeerAddress address, Role role, Peer initiator) {
      FramePeer responder = delegate.connect(address, role, new PeerToFramePeer(initiator));
      return new FramePeerToPeer(messageIdsFactory.get(), responder);
   }

   @Override
   public void receiveAnnouncement(PeerAddress address) {
      listener.receiveAnnouncement(address);
   }

   @Override
   public FramePeer receiveConnection(PeerAddress address, Role role, FramePeer initiator) {
      Peer responder = listener.receiveConnection(address, role, new FramePeerToPeer(messageIdsFactory.get(), initiator));
      return new PeerToFramePeer(responder);
   }
}


