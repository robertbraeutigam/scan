/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;

public interface NetworkListener {
   void receiveAnnouncement(PeerAddress address);

   /**
    * Receive a connection from the given initiator.
    */
   CompletableFuture<Peer> connect(PeerAddress address, Role role, Peer initiator);
}

