package com.vanillasource.scan.types.codec2.bit;

import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public final class FedBitReaderTests {

   // ---- write(int) ----

   @Test
   public void writeSingleByteMakesItAvailable() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB);
      assertEquals(r.available(), 1);
   }

   @Test
   public void writeSingleByteThrowsIfPreviousNotConsumed() {
      FedBitReader r = new FedBitReader();
      r.write(0x10);
      assertThrows(IllegalStateException.class, () -> r.write(0x20));
   }

   @Test
   public void writeSingleByteAcceptedAfterPreviousFullyRead() {
      FedBitReader r = new FedBitReader();
      r.write(0x10);
      byte[] dst = new byte[1];
      r.readBytes(dst, 0, 1);
      r.write(0x20);
      assertEquals(r.available(), 1);
   }

   @Test
   public void writeSingleByteMasksToLowByte() {
      FedBitReader r = new FedBitReader();
      r.write(0x1FF);
      byte[] dst = new byte[1];
      r.readBytes(dst, 0, 1);
      assertEquals(dst[0], (byte) 0xFF);
   }

   // ---- write(byte[], int, int) ----

   @Test
   public void writeArrayMakesAllBytesAvailable() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3, 4 }, 0, 4);
      assertEquals(r.available(), 4);
   }

   @Test
   public void writeArrayRespectsOffsetAndLength() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3, 4, 5, 6 }, 2, 3);
      assertEquals(r.available(), 3);
      byte[] dst = new byte[3];
      r.readBytes(dst, 0, 3);
      assertEquals(dst, new byte[] { 3, 4, 5 });
   }

   @Test
   public void writeArrayThrowsIfPreviousNotConsumed() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2 }, 0, 2);
      assertThrows(IllegalStateException.class,
            () -> r.write(new byte[] { 3 }, 0, 1));
   }

   @Test
   public void writeArrayAcceptedAfterPreviousFullyRead() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2 }, 0, 2);
      byte[] dst = new byte[2];
      r.readBytes(dst, 0, 2);
      r.write(new byte[] { 3, 4 }, 0, 2);
      assertEquals(r.available(), 2);
   }

   @Test
   public void writeArrayThrowsIfPreviousSingleByteNotConsumed() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB);
      assertThrows(IllegalStateException.class,
            () -> r.write(new byte[] { 1 }, 0, 1));
   }

   @Test
   public void writeSingleByteThrowsIfPreviousArrayNotConsumed() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2 }, 0, 2);
      assertThrows(IllegalStateException.class, () -> r.write(0xAB));
   }

   // ---- write(byte[]) (OutputStream convenience overload) ----

   @Test
   public void outputStreamWriteByteArrayOverloadFeedsAllBytes() throws IOException {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 0x10, 0x20, 0x30 });
      assertEquals(r.available(), 3);
      byte[] dst = new byte[3];
      r.readBytes(dst, 0, 3);
      assertEquals(dst, new byte[] { 0x10, 0x20, 0x30 });
   }

   // ---- available() ----

   @Test
   public void availableIsZeroInitially() {
      FedBitReader r = new FedBitReader();
      assertEquals(r.available(), 0);
   }

   @Test
   public void availableDecreasesAsBytesAreRead() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3, 4 }, 0, 4);
      byte[] dst = new byte[2];
      r.readBytes(dst, 0, 2);
      assertEquals(r.available(), 2);
      r.readBytes(dst, 0, 2);
      assertEquals(r.available(), 0);
   }

   @Test
   public void availableDecreasesWhenByteReservedForBits() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { (byte) 0xAB, (byte) 0xCD }, 0, 2);
      r.readBits(1);
      // The first byte is now reserved for bit reading and no longer in the byte queue.
      assertEquals(r.available(), 1);
   }

   // ---- readBytes ----

   @Test
   public void readBytesReturnsZeroWhenEmpty() {
      FedBitReader r = new FedBitReader();
      assertEquals(r.readBytes(new byte[4], 0, 4), 0);
   }

   @Test
   public void readBytesReturnsZeroForZeroLengthRequest() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3 }, 0, 3);
      assertEquals(r.readBytes(new byte[4], 0, 0), 0);
      assertEquals(r.available(), 3);
   }

   @Test
   public void readBytesSingleByteMode() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB);
      byte[] dst = new byte[1];
      int read = r.readBytes(dst, 0, 1);
      assertEquals(read, 1);
      assertEquals(dst[0], (byte) 0xAB);
      assertEquals(r.available(), 0);
   }

   @Test
   public void readBytesSingleByteModeRespectsDestinationOffset() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB);
      byte[] dst = new byte[] { 1, 1, 1, 1 };
      int read = r.readBytes(dst, 2, 1);
      assertEquals(read, 1);
      assertEquals(dst, new byte[] { 1, 1, (byte) 0xAB, 1 });
   }

   @Test
   public void readBytesArrayMode() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3, 4 }, 0, 4);
      byte[] dst = new byte[4];
      int read = r.readBytes(dst, 0, 4);
      assertEquals(read, 4);
      assertEquals(dst, new byte[] { 1, 2, 3, 4 });
   }

   @Test
   public void readBytesArrayModeRespectsDestinationOffset() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 0x10, 0x20 }, 0, 2);
      byte[] dst = new byte[] { 0, 0, 0, 0 };
      int read = r.readBytes(dst, 1, 2);
      assertEquals(read, 2);
      assertEquals(dst, new byte[] { 0, 0x10, 0x20, 0 });
   }

   @Test
   public void readBytesArrayModePartialReadAdvancesPosition() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3, 4 }, 0, 4);
      byte[] dst = new byte[2];
      assertEquals(r.readBytes(dst, 0, 2), 2);
      assertEquals(dst, new byte[] { 1, 2 });
      assertEquals(r.readBytes(dst, 0, 2), 2);
      assertEquals(dst, new byte[] { 3, 4 });
      assertEquals(r.available(), 0);
   }

   @Test
   public void readBytesIsLimitedByAvailable() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3 }, 0, 3);
      byte[] dst = new byte[10];
      assertEquals(r.readBytes(dst, 0, 10), 3);
      assertEquals(r.available(), 0);
   }

   @Test
   public void readBytesIsLimitedByN() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { 1, 2, 3, 4, 5 }, 0, 5);
      byte[] dst = new byte[5];
      assertEquals(r.readBytes(dst, 0, 2), 2);
      assertEquals(r.available(), 3);
   }

   @Test
   public void readBytesArrayHonorsSourceOffset() {
      FedBitReader r = new FedBitReader();
      // Source offset 3, length 2 → exposes bytes {0x44, 0x55}.
      r.write(new byte[] { 0x11, 0x22, 0x33, 0x44, 0x55, 0x66 }, 3, 2);
      byte[] dst = new byte[2];
      assertEquals(r.readBytes(dst, 0, 2), 2);
      assertEquals(dst, new byte[] { 0x44, 0x55 });
   }

   // ---- readBits ----

   @Test
   public void readBitsReturnsZeroWhenEmpty() {
      FedBitReader r = new FedBitReader();
      assertEquals(r.readBits(3), 0);
   }

   @Test
   public void readBitsReturnsZeroForZeroN() {
      FedBitReader r = new FedBitReader();
      r.write(0xFF);
      assertEquals(r.readBits(0), 0);
      // No byte got reserved.
      assertEquals(r.available(), 1);
      assertEquals(r.availableBits(), 8);
   }

   @Test
   public void readBitsReturnsZeroForNegativeN() {
      FedBitReader r = new FedBitReader();
      r.write(0xFF);
      assertEquals(r.readBits(-1), 0);
      assertEquals(r.available(), 1);
   }

   @Test
   public void readBitsReservesByteFromSingleByteMode() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB); // 10101011
      assertEquals(r.readBits(3), 0b101);
      assertEquals(r.availableBits(), 5);
      assertEquals(r.available(), 0);
   }

   @Test
   public void readBitsReservesByteFromArrayMode() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { (byte) 0xAB, (byte) 0xCD }, 0, 2);
      assertEquals(r.readBits(3), 0b101);
      assertEquals(r.availableBits(), 5);
      // Only the second byte remains in the byte queue.
      assertEquals(r.available(), 1);
   }

   @Test
   public void readBitsReadsMSBFirst() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB); // 1010_1011
      assertEquals(r.readBits(1), 0b1);
      assertEquals(r.readBits(1), 0b0);
      assertEquals(r.readBits(1), 0b1);
      assertEquals(r.readBits(1), 0b0);
      assertEquals(r.readBits(1), 0b1);
      assertEquals(r.readBits(1), 0b0);
      assertEquals(r.readBits(1), 0b1);
      assertEquals(r.readBits(1), 0b1);
   }

   @Test
   public void consecutiveReadBitsConsumeFromSameReservedByte() {
      FedBitReader r = new FedBitReader();
      r.write(0b10110100);
      assertEquals(r.readBits(2), 0b10);
      assertEquals(r.readBits(3), 0b110);
      assertEquals(r.readBits(3), 0b100);
      assertEquals(r.availableBits(), 0);
   }

   @Test
   public void readBitsReadsFullByte() {
      FedBitReader r = new FedBitReader();
      r.write(0xC3);
      assertEquals(r.readBits(8), 0xC3);
      assertEquals(r.availableBits(), 0);
      assertEquals(r.available(), 0);
   }

   @Test
   public void readBitsReservesNextByteAfterCurrentExhausted() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { (byte) 0xF0, 0x0F }, 0, 2);
      assertEquals(r.readBits(8), 0xF0);
      assertEquals(r.availableBits(), 8);
      assertEquals(r.readBits(8), 0x0F);
      assertEquals(r.availableBits(), 0);
      assertEquals(r.available(), 0);
   }

   @Test
   public void readBitsExhaustingByteAllowsNewWrite() {
      FedBitReader r = new FedBitReader();
      r.write(0xFF);
      r.readBits(8);
      // availableBytes=0 even though the byte was reserved → new write must succeed.
      r.write(0x55);
      assertEquals(r.readBits(8), 0x55);
   }

   @Test
   public void readBitsAcceptsNewWriteWhilePartialBitsRemain() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB); // reservation transfers byte out of the queue
      r.readBits(3); // 5 bits still buffered, but availableBytes=0
      r.write(0xCD); // permitted: bit byte is independent of byte queue
      // Remaining 5 bits of 0xAB = 0b01011.
      assertEquals(r.readBits(5), 0b01011);
      // Now the next read pulls from the freshly written byte.
      assertEquals(r.readBits(8), 0xCD);
   }

   @Test
   public void readBitsThenReadBytesInterleave() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { (byte) 0xC0, 0x42, 0x43 }, 0, 3);
      // Pull 2 bits from the first byte: top two bits of 0xC0 = 11.
      assertEquals(r.readBits(2), 0b11);
      // Switch to byte reads — should pick up the next two unreserved bytes.
      byte[] dst = new byte[2];
      assertEquals(r.readBytes(dst, 0, 2), 2);
      assertEquals(dst, new byte[] { 0x42, 0x43 });
   }

   @Test
   public void readBitsLimitedToAvailableBitsWhenAskingMore() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB); // 1010_1011
      r.readBits(5); // consume top 5 → 10101
      // Only 3 bits remain (011); asking for 8 returns those 3.
      assertEquals(r.availableBits(), 3);
      assertEquals(r.readBits(8), 0b011);
      assertEquals(r.availableBits(), 0);
   }

   // ---- availableBits() ----

   @Test
   public void availableBitsZeroInitially() {
      FedBitReader r = new FedBitReader();
      assertEquals(r.availableBits(), 0);
   }

   @Test
   public void availableBitsEightAfterByteWrittenButNoBitRead() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB);
      assertEquals(r.availableBits(), 8);
   }

   @Test
   public void availableBitsDecreasesAfterReadBits() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB);
      r.readBits(3);
      assertEquals(r.availableBits(), 5);
   }

   @Test
   public void availableBitsZeroAfterReservedByteFullyConsumed() {
      FedBitReader r = new FedBitReader();
      r.write(0xAB);
      r.readBits(8);
      assertEquals(r.availableBits(), 0);
   }

   @Test
   public void availableBitsEightAgainAfterFreshByteFollowsConsumption() {
      FedBitReader r = new FedBitReader();
      r.write(new byte[] { (byte) 0xAB, (byte) 0xCD }, 0, 2);
      r.readBits(8);
      // First byte is fully consumed; another byte is still in the queue.
      assertEquals(r.availableBits(), 8);
   }
}
