package com.vanillasource.scan.client.network;

public interface NetworkListener {
   void receiveAnnouncement(PeerAddress address);

   /**
    * Receive a connection from the given initiator.
    */
   Peer receiveConnection(PeerAddress address, Role role, Peer initiator);
}

