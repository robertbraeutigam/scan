/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface FrameNetworkListener {
   CompletableFuture<Void> receiveMulticast(InetAddress sender, ByteBuffer packet);

   CompletableFuture<FramePeer> receiveConnection(InetAddress address, FramePeer initiator);
}

