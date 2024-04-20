package com.vanillasource.scan.client.network.network.direct;

import com.vanillasource.scan.client.network.*;
import com.vanillasource.scan.client.network.frame.FrameNetwork;
import com.vanillasource.scan.client.network.frame.FrameNetworkListener;
import com.vanillasource.scan.client.network.frame.FramePeer;

import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A network that directly converts all messages into message frames,
 * without authentication, authorization, or any other protocol features.
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


