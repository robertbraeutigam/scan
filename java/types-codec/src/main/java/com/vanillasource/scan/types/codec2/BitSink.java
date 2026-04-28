package com.vanillasource.scan.types.codec2;

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
}
