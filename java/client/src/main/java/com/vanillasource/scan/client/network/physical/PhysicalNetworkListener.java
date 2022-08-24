/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.physical;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface PhysicalNetworkListener {
   CompletableFuture<Void> receiveMulticast(InetAddress sender, ByteBuffer packet);

   CompletableFuture<PhysicalPeer> receiveConnection(InetAddress address, PhysicalPeer initiator);
}

