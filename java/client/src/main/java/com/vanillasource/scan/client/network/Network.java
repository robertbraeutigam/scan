/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.concurrent.CompletableFuture;

public interface Network extends Endpoint {
   /**
    * Query all devices on the network. Note that this device may not have
    * access to all the devices listed. Only devices that are currently on the
    * network can answer this query. Not all devices may be detected however
    * due to network congestion or timeouts. This method may take several
    * seconds to complete, but will immediately update the supplied listener
    * each time a device answers.
    * @return A future that completes when the query was successfully sent.
    */
   CompletableFuture<Void> queryAll();

   /**
    * Free up this network instance, including ports and other network resources 
    * and close all logical connections.
    * @return A future that completes when all resources are freed.
    */
   CompletableFuture<Void> close();
}
