/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import java.util.concurrent.CompletableFuture;
import com.vanillasource.scan.client.network.PeerAddress;
import com.vanillasource.scan.client.network.Role;

public interface FrameNetworkListener {
   void receiveAnnouncement(PeerAddress address);

   CompletableFuture<FramePeer> receiveConnection(PeerAddress address, Role role, FramePeer initiator);
}

