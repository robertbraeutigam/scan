/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.physical;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.net.InetAddress;

public interface PhysicalNetwork {
   /**
    * Send a UDP packet to the network. The rules of the sending may be implementation
    * dependent.
    * @return A future that completes if packet is sent with all rules defined by implementation.
    */
   CompletableFuture<Void> sendMulticast(ByteBuffer packet);

   /**
    * Open a connection to the given peer.
    * @return A future that completes with a channel iff it can be opened.
    */
   CompletableFuture<PhysicalPeer> openConnection(InetAddress peer, PhysicalPeer initiator);

   /**
    * Close the network, free up all resources.
    */
   void close();
}
