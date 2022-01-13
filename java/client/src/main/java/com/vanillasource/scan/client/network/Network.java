/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;

public interface Network {
   /**
    * Establish a logical connection to the given address with the gven PSK.
    */
   LogicalConnection establish(byte[] address, byte[] psk, LogicalConnectionReceiver receiver);

   /**
    * Query all devices on the network. Note that this device may not have
    * access to all the devices listed. Only devices that are currently on the
    * network can answer this query. Not all devices may be detected however
    * due to network congestion or timeouts. This method may take several
    * seconds to complete, but will immediately update the supplied listener
    * each time a device answers.
    */
   void queryAll(WildcardQueryIssuer issuer);

}
