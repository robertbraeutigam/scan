package com.vanillasource.scan.types.codec2;

/**
 * Provides byte/bit reading to callers. It is non-blocking.
 */
public interface BitSource {
   /**
    * @return The number of available bytes.
    */
   int availableBytes();

   /**
    * Reads at most "n" bytes.
    * @return The number of bytes actually read.
    */
   int readBytes(byte[] dst, int off, int n);

   /**
    * Reads one byte.
    */
   int readUnsignedByte();

   /**
    * Reads the given amount of bits. As soon as this method is invoked,
    * either there is already a byte reserved for
    * bits, in which case the remaining bits from there will be read,
    * or, if a byte is available, that byte will be reserved for bits.
    * @param n The number of bits to read, at most 8.
    * @return The value read, or 0 if no bits are available.
    */
   int readBits(int n);

   int availableBits();

   /**
    * Discard any currently-active bit byte's remaining bits. Called by aggregate
    * codecs on entry to and exit from an Array / Set / Stream so the spec rule
    * "bit state resets at aggregate boundaries" is enforced. After this call,
    * {@link #availableBits()} reflects only what subsequent bytes provide.
    */
   default void closeBits() {}
}
