package com.vanillasource.scan.types.codec2.bit;

import com.vanillasource.scan.types.codec2.BitSource;

import java.io.OutputStream;

/**
 * A <code>BitReader</code> that is fed bytes. It only accepts bytes if the previously
 * fed bytes are completely read.
 * Note, that this does not copy the bytes it is fed, but uses them directly to read. Class is
 * not thread-safe.
 */
public final class FedBitSource extends OutputStream implements BitSource {
   private boolean singleByteInsteadByteArray = true;
   // Single byte buffer (mode single)
   private int singleByte = 0;
   // Byte buffer (mode array)
   private byte[] byteBuffer;
   private int offset = 0;
   private int length = 0;
   // Free bytes (this is for both modes)
   private int availableBytes = 0; // No bytes available initially

   // Remember the bit byte
   private int bitByte = 0;
   private int availableBits = 0; // No bits available initially

   @Override
   public void write(int i)  {
      if (availableBytes > 0) {
         throw new IllegalStateException("Not yet read fully.");
      }
      this.singleByteInsteadByteArray = true;
      this.singleByte = i;
      this.availableBytes = 1;
   }

   @Override
   public void write(byte[] b, int off, int len)  {
      if (availableBytes > 0) {
         throw new IllegalStateException("Not yet read fully.");
      }
      this.singleByteInsteadByteArray = false;
      this.byteBuffer = b;
      this.offset = off;
      this.length = len;
      this.availableBytes = len;
   }

   @Override
   public int availableBytes() {
      return availableBytes;
   }

   @Override
   public int readBytes(byte[] dst, int off, int n) {
      if (availableBytes > 0 && n > 0) {
         if (singleByteInsteadByteArray) {
            dst[off] = (byte) (singleByte & 0xFF);
            availableBytes = 0;
            return 1;
         } else {
            int copyCount = Math.min(n, availableBytes);
            System.arraycopy(byteBuffer, offset, dst, off, copyCount);
            offset += copyCount;
            availableBytes -= copyCount;
            return copyCount;
         }
      } else {
         return 0;
      }
   }

   /**
    * @return A byte if available, 0 otherwise.
    */
   @Override
   public int readUnsignedByte() {
      if (availableBytes <= 0) {
         return 0;
      }
      if (singleByteInsteadByteArray) {
         availableBytes = 0;
         return singleByte & 0xFF;
      } else {
         availableBytes--;
         offset++;
         return byteBuffer[offset-1] & 0xFF;
      }
   }

   @Override
   public int readBits(int n) {
      if (n <= 0) {
         return 0;
      }
      if (availableBits == 0) {
         if (availableBytes == 0) {
            return 0;
         }
         if (singleByteInsteadByteArray) {
            bitByte = singleByte & 0xFF;
         } else {
            bitByte = byteBuffer[offset++] & 0xFF;
         }
         availableBytes--;
         availableBits = 8;
      }
      int take = Math.min(n, availableBits);
      int shift = availableBits - take;
      int value = (bitByte >>> shift) & ((1 << take) - 1);
      availableBits -= take;
      return value;
   }

   /**
    * @return The number of bits available to be read, maximum 8.
    */
   @Override
   public int availableBits() {
      if (availableBits > 0) {
         return availableBits;
      } else if (availableBytes > 0) {
         return 8;
      } else {
         return 0;
      }
   }
}
