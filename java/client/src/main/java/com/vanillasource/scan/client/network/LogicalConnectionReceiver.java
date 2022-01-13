/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

public interface LogicalConnectionReceiver {
   /**
    * Called when an incoming connection is successfully made.
    * @return The receiver side of the logical connection.
    */
   LogicalConnection establish(byte[] address, byte[] psk, LogicalConnection connection);
}
