/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

/**
 * A network endpoint where connections can be established.
 */
public interface Endpoint {
   /**
    * Called when a connection is successfully made.
    * @param initiator The initiator of the connection.
    * @return The responder of the logical connection.
    */
   Peer establish(byte[] address, byte[] psk, Peer initiator);
}
