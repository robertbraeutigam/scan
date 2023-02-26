/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import java.util.concurrent.CompletableFuture;
import java.nio.ByteBuffer;
import java.net.InetAddress;

/**
 * A network that works in terms of frames.
 */
public interface FrameNetwork {
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
   CompletableFuture<FramePeer> openConnection(InetAddress peer, FramePeer initiator);

   /**
    * Close the network, free up all resources.
    */
   void close();
}
