package com.vanillasource.scan.types.codec;

public interface BitSink {
   /**
    * @return The number of writable bytes to this sink.
    */
   int writableBytes();

   /**
    * Write the given bytes, or parts thereof, into the sink.
    * @return The number of bytes written.
    */
   int write(byte[] buf, int off, int len);

   /**
    * Write the given byte.
    * @return The number of bytes written. Either 0 or 1.
    */
   int writeUnsignedByte(int unsignedByte);

   /**
    * @return The number of writable bits into this sink.
    */
   int writableBits();

   /**
    * Write the given bits, at most 8, into the sink.
    * @return The number of bits written.
    */
   int writeBits(int bits, int count);

   /**
    * Close any currently-active bit byte by emitting it (with its unused slots
    * left as 0) and flushing any byte writes that have been buffered behind it
    * (per TYPES.md "Writing Byte-Aligned Data": byte-aligned writes do not close
    * the bit byte, so a buffering BitSink must hold them until the bit byte
    * either fills up, the buffer cap is reached, or this method is called).
    * Callers invoke this only at the end of a value, to flush any partially
    * filled bit byte; aggregate boundaries no longer reset bit state.
    */
   default void closeBits() {}
}
