package com.vanillasource.scan.client.network.network;

import com.vanillasource.scan.client.network.data.VariableLengthInteger;

/**
 * Tracked used / available message ids and try to always return an optimal one.
 * Since message ids are included in the messages as variable length integers,
 * the smaller this id is, the fewer bytes it occupies in the message. At a minimum
 * it is 1 byte (if the id is less than 128), at most this is 8 bytes.
 */
public interface MessageIds {
   VariableLengthInteger reserveId();

   void releaseId(VariableLengthInteger id);
}

