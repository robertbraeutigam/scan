package com.vanillasource.scan.types.codec;

public interface Type {
   ValueDecoder createDecoder();

   ValueEncoder createEncoder();

   /**
    * Whether this type encodes one byte per value on the wire — i.e. a 1-byte
    * primitive. Used by {@code Array} and {@code Stream} to dispatch to a
    * chunk-emitting decoder/encoder for bulk byte content (per TYPES.md
    * §"Incremental Consumption", which expects batched delivery for runs of
    * 1-byte-primitive elements).
    */
   default boolean isPrimitiveByte() {
      return false;
   }
}
