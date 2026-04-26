package com.vanillasource.scan.types.codec;

import org.testng.annotations.Test;

import java.io.UncheckedIOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public final class BitCursorTests {

    @Test
    public void emptyWriterProducesEmptyOutput() {
        BitWriter w = new BitWriter();
        assertEquals(w.toByteArray(), new byte[0]);
        assertEquals(w.byteSize(), 0);
    }

    @Test
    public void singleBitOccupiesTopOfFirstByte() {
        BitWriter w = new BitWriter();
        w.writeBits(1, 1);
        assertEquals(w.toByteArray(), new byte[] { (byte) 0x80 });
    }

    @Test
    public void consecutiveBitsFillFromMostSignificant() {
        BitWriter w = new BitWriter();
        w.writeBits(3, 0b101);
        w.writeBits(2, 0b11);
        // 101_11_000 = 0xB8
        assertEquals(w.toByteArray(), new byte[] { (byte) 0xB8 });
    }

    @Test
    public void filling8BitsClosesActiveByte() {
        BitWriter w = new BitWriter();
        w.writeBits(8, 0xA5);
        w.writeBits(1, 1);
        // First byte = 0xA5, second byte = 0x80
        assertEquals(w.toByteArray(), new byte[] { (byte) 0xA5, (byte) 0x80 });
    }

    @Test
    public void byteAlignedWriteDoesNotCloseActiveBitByte() {
        BitWriter w = new BitWriter();
        w.writeBits(1, 1);
        w.writeBytes(new byte[] { 0x12, 0x34, 0x56, 0x78 });
        w.writeBits(1, 1);
        // bit-byte (with both bits set) physically precedes the four data bytes.
        // 11_000000 = 0xC0
        assertEquals(w.toByteArray(),
                new byte[] { (byte) 0xC0, 0x12, 0x34, 0x56, 0x78 });
    }

    @Test
    public void bitWriteWithoutFreeSlotsAllocatesAfterDataBytes() {
        BitWriter w = new BitWriter();
        w.writeBits(8, 0x55);
        w.writeBytes(new byte[] { 0x11, 0x22 });
        w.writeBits(2, 0b10);
        // 0x55, 0x11, 0x22, 10_000000 = 0x80
        assertEquals(w.toByteArray(),
                new byte[] { 0x55, 0x11, 0x22, (byte) 0x80 });
    }

    @Test
    public void closeBitByteForcesNextBitToFreshByte() {
        BitWriter w = new BitWriter();
        w.writeBits(3, 0b101);
        w.closeBitByte();
        w.writeBits(5, 0b10101);
        // First byte: 101_00000 = 0xA0; second byte: 10101_000 = 0xA8
        assertEquals(w.toByteArray(), new byte[] { (byte) 0xA0, (byte) 0xA8 });
    }

    @Test
    public void writeBitsRejectsValueLargerThanWidth() {
        BitWriter w = new BitWriter();
        assertThrows(IllegalArgumentException.class, () -> w.writeBits(3, 0b1000));
    }

    @Test
    public void writeBitsRejectsNegativeOrOversizedWidth() {
        BitWriter w = new BitWriter();
        assertThrows(IllegalArgumentException.class, () -> w.writeBits(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> w.writeBits(65, 0));
    }

    @Test
    public void roundTripPureBits() {
        BitWriter w = new BitWriter();
        w.writeBits(1, 1);
        w.writeBits(2, 0b10);
        w.writeBits(3, 0b110);
        w.writeBits(2, 0b01);
        BitReader r = new BitReader(w.toByteArray());
        assertEquals(r.readBits(1), 1L);
        assertEquals(r.readBits(2), 0b10L);
        assertEquals(r.readBits(3), 0b110L);
        assertEquals(r.readBits(2), 0b01L);
    }

    @Test
    public void roundTripInterleavedBitsAndBytes() {
        BitWriter w = new BitWriter();
        w.writeBits(1, 1);
        w.writeBytes(new byte[] { 0x12, 0x34, 0x56, 0x78 });
        w.writeBits(1, 1);
        w.writeBits(6, 0b101010);
        BitReader r = new BitReader(w.toByteArray());
        assertEquals(r.readBits(1), 1L);
        byte[] data = r.readBytes(4);
        assertEquals(data, new byte[] { 0x12, 0x34, 0x56, 0x78 });
        assertEquals(r.readBits(1), 1L);
        assertEquals(r.readBits(6), 0b101010L);
    }

    @Test
    public void roundTripWideBitsSpanningBytes() {
        // active byte gets 5 bits used, then a 12-bit value must split 3+8+1
        BitWriter w = new BitWriter();
        w.writeBits(5, 0b10101);
        w.writeBits(12, 0xABC);
        w.writeBits(4, 0b1101);
        BitReader r = new BitReader(w.toByteArray());
        assertEquals(r.readBits(5), 0b10101L);
        assertEquals(r.readBits(12), 0xABCL);
        assertEquals(r.readBits(4), 0b1101L);
    }

    @Test
    public void roundTripFullWidth64Bits() {
        BitWriter w = new BitWriter();
        long value = 0xDEADBEEF_CAFEBABEL;
        w.writeBits(64, value);
        BitReader r = new BitReader(w.toByteArray());
        assertEquals(r.readBits(64), value);
    }

    @Test
    public void roundTripCloseBitByte() {
        BitWriter w = new BitWriter();
        w.writeBits(3, 0b101);
        w.closeBitByte();
        w.writeBits(3, 0b011);
        byte[] encoded = w.toByteArray();
        // Two bytes: first 101_00000, second 011_00000
        assertEquals(encoded, new byte[] { (byte) 0xA0, (byte) 0x60 });
        BitReader r = new BitReader(encoded);
        assertEquals(r.readBits(3), 0b101L);
        r.closeBitByte();
        assertEquals(r.readBits(3), 0b011L);
    }

    @Test
    public void readerEofOnEmptyInput() {
        BitReader r = new BitReader(new byte[0]);
        assertThrows(UncheckedIOException.class, () -> r.readBits(1));
    }

    @Test
    public void readerEofWhenBitByteUnavailable() {
        BitReader r = new BitReader(new byte[] { 0x55 });
        assertEquals(r.readBits(8), 0x55L);
        assertThrows(UncheckedIOException.class, () -> r.readBits(1));
    }

    @Test
    public void readerEofOnTruncatedByteAlignedRead() {
        BitReader r = new BitReader(new byte[] { 0x01, 0x02 });
        assertThrows(UncheckedIOException.class, () -> r.readBytes(3));
    }

    @Test
    public void readerCursorReflectsAllocatedBytes() {
        BitWriter w = new BitWriter();
        w.writeBits(1, 1);
        w.writeBytes(new byte[] { 0x10, 0x20 });
        w.writeBits(1, 1);
        BitReader r = new BitReader(w.toByteArray());
        r.readBits(1);
        // bit byte was allocated (cursor advanced past it)
        assertEquals(r.cursor(), 1);
        r.readBytes(2);
        assertEquals(r.cursor(), 3);
        r.readBits(1);
        // bit byte was already buffered; cursor unchanged
        assertEquals(r.cursor(), 3);
    }
}
