/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.physical;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import com.vanillasource.scan.client.network.Peer;

public interface PhysicalNetworkListener {
   CompletableFuture<Void> receiveMulticast(InetAddress sender, ByteBuffer packet);

   CompletableFuture<Peer> receiveConnection(InetAddress address, Peer initiator);
}

