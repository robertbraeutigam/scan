package com.vanillasource.scan.client.network.physical;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public interface PhysicalNetwork extends AutoCloseable {
   /**
    * Send a UDP packet to the network. The rules of the sending may be implementation
    * dependent.
    */
   void sendMulticast(ByteBuffer packet);

   /**
    * Open a connection to the given peer.
    */
   PhysicalPeer openConnection(InetAddress peer, PhysicalPeer initiator);

   /**
    * Close the network, free up all resources.
    */
   void close();
}
