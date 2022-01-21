/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.topology;

import com.vanillasource.scan.client.network.Peer;
import com.vanillasource.scan.client.network.Network;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Network that makes lookups and queries over UDP.
 */
public final class LocalNetwork implements Network {
   @Override
   public Peer establish(byte[] address, byte[] psk, Peer initiator) {
      return null; // TODO
   }

   @Override
   public void queryAll() {
      // TODO
   }

   @Override
   public CompletableFuture<Void> close() {
      return null; // TODO
   }
}

