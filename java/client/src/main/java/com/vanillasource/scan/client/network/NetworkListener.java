package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;

public interface NetworkListener {
   void receiveAnnouncement(PeerAddress address);

   /**
    * Receive a connection from the given initiator.
    */
   Peer receiveConnection(PeerAddress address, Role role, Peer initiator);
}

