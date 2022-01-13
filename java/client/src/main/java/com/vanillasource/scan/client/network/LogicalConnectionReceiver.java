/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

public interface LogicalConnectionReceiver {
   /**
    * Called to create an incoming message.
    */
   Message create();
}
