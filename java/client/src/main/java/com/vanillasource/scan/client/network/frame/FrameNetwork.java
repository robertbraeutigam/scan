/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import com.vanillasource.scan.client.network.PeerAddress;
import com.vanillasource.scan.client.network.Peer;
import com.vanillasource.scan.client.network.Role;
import java.util.concurrent.CompletableFuture;

public interface FrameNetwork {
   CompletableFuture<Void> identityQuery(int queryId, PeerAddress[] addresses);

   CompletableFuture<Void> identityAnnouncement(PeerAddress[] addresses);

   CompletableFuture<Void> close();

   CompletableFuture<FramePeer> connect(PeerAddress address, Role role, Peer initiator);
}
