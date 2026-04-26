package com.vanillasource.scan.types.codec;

import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class BitCursorTests {

    @Test
    public void emptyWriterProducesEmptyOutput() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.closeBitByte();
        assertEquals(sink.toByteArray(), new byte[0]);
    }

    @Test
    public void singleBitOccupiesTopOfFirstByte() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(1, 1);
        w.closeBitByte();
        assertEquals(sink.toByteArray(), new byte[] { (byte) 0x80 });
    }

    @Test
    public void consecutiveBitsFillFromMostSignificant() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(3, 0b101);
        w.writeBits(2, 0b11);
        w.closeBitByte();
        // 101_11_000 = 0xB8
        assertEquals(sink.toByteArray(), new byte[] { (byte) 0xB8 });
    }

    @Test
    public void filling8BitsClosesActiveByte() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(8, 0xA5);
        w.writeBits(1, 1);
        w.closeBitByte();
        assertEquals(sink.toByteArray(), new byte[] { (byte) 0xA5, (byte) 0x80 });
    }

    @Test
    public void byteAlignedWriteDoesNotCloseActiveBitByte() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(1, 1);
        w.writeBytes(new byte[] { 0x12, 0x34, 0x56, 0x78 });
        w.writeBits(1, 1);
        w.closeBitByte();
        // bit-byte (with both bits set) physically precedes the four data bytes.
        // 11_000000 = 0xC0
        assertEquals(sink.toByteArray(),
                new byte[] { (byte) 0xC0, 0x12, 0x34, 0x56, 0x78 });
    }

    @Test
    public void bitWriteWithoutFreeSlotsAllocatesAfterDataBytes() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(8, 0x55);
        w.writeBytes(new byte[] { 0x11, 0x22 });
        w.writeBits(2, 0b10);
        w.closeBitByte();
        // 0x55, 0x11, 0x22, 10_000000 = 0x80
        assertEquals(sink.toByteArray(),
                new byte[] { 0x55, 0x11, 0x22, (byte) 0x80 });
    }

    @Test
    public void closeBitByteForcesNextBitToFreshByte() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(3, 0b101);
        w.closeBitByte();
        w.writeBits(5, 0b10101);
        w.closeBitByte();
        // First byte: 101_00000 = 0xA0; second byte: 10101_000 = 0xA8
        assertEquals(sink.toByteArray(), new byte[] { (byte) 0xA0, (byte) 0xA8 });
    }

    @Test
    public void writeBitsRejectsValueLargerThanWidth() {
        BitWriter w = new BitWriter(new ByteArrayOutputStream());
        assertThrows(IllegalArgumentException.class, () -> w.writeBits(3, 0b1000));
    }

    @Test
    public void writeBitsRejectsNegativeOrOversizedWidth() {
        BitWriter w = new BitWriter(new ByteArrayOutputStream());
        assertThrows(IllegalArgumentException.class, () -> w.writeBits(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> w.writeBits(65, 0));
    }

    @Test
    public void byteWritesPassThroughWhenNoBitByteOpen() {
        // Pure byte stream — every write should reach the delegate immediately.
        RecordingStream sink = new RecordingStream();
        BitWriter w = new BitWriter(sink);
        w.writeBytes(new byte[] { 0x01, 0x02, 0x03 });
        assertEquals(sink.flushedSoFar(), new byte[] { 0x01, 0x02, 0x03 });
        w.writeBytes(new byte[] { 0x04 });
        assertEquals(sink.flushedSoFar(), new byte[] { 0x01, 0x02, 0x03, 0x04 });
    }

    @Test
    public void bytesAfterOpenBitByteAreHeldUntilClose() {
        RecordingStream sink = new RecordingStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(2, 0b10);
        w.writeBytes(new byte[] { 0x42, 0x43 });
        // Nothing has been emitted yet — the bit byte is still open.
        assertEquals(sink.flushedSoFar(), new byte[0]);
        w.closeBitByte();
        // bit byte = 10_000000 = 0x80; then suffix 0x42 0x43.
        assertEquals(sink.flushedSoFar(), new byte[] { (byte) 0x80, 0x42, 0x43 });
    }

    @Test
    public void roundTripPureBits() {
        BitReader r = roundTrip(w -> {
            w.writeBits(1, 1);
            w.writeBits(2, 0b10);
            w.writeBits(3, 0b110);
            w.writeBits(2, 0b01);
        });
        assertEquals(r.readBits(1), 1L);
        assertEquals(r.readBits(2), 0b10L);
        assertEquals(r.readBits(3), 0b110L);
        assertEquals(r.readBits(2), 0b01L);
    }

    @Test
    public void roundTripInterleavedBitsAndBytes() {
        BitReader r = roundTrip(w -> {
            w.writeBits(1, 1);
            w.writeBytes(new byte[] { 0x12, 0x34, 0x56, 0x78 });
            w.writeBits(1, 1);
            w.writeBits(6, 0b101010);
        });
        assertEquals(r.readBits(1), 1L);
        byte[] data = new byte[4];
        r.readBytes(data, 0, 4);
        assertEquals(data, new byte[] { 0x12, 0x34, 0x56, 0x78 });
        assertEquals(r.readBits(1), 1L);
        assertEquals(r.readBits(6), 0b101010L);
    }

    @Test
    public void roundTripWideBitsSpanningBytes() {
        BitReader r = roundTrip(w -> {
            w.writeBits(5, 0b10101);
            w.writeBits(12, 0xABC);
            w.writeBits(4, 0b1101);
        });
        assertEquals(r.readBits(5), 0b10101L);
        assertEquals(r.readBits(12), 0xABCL);
        assertEquals(r.readBits(4), 0b1101L);
    }

    @Test
    public void roundTripFullWidth64Bits() {
        long value = 0xDEADBEEF_CAFEBABEL;
        BitReader r = roundTrip(w -> w.writeBits(64, value));
        assertEquals(r.readBits(64), value);
    }

    @Test
    public void roundTripCloseBitByte() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(3, 0b101);
        w.closeBitByte();
        w.writeBits(3, 0b011);
        w.closeBitByte();
        byte[] encoded = sink.toByteArray();
        assertEquals(encoded, new byte[] { (byte) 0xA0, (byte) 0x60 });
        BitReader r = new BitReader();
        r.feed(encoded, 0, encoded.length);
        assertEquals(r.readBits(3), 0b101L);
        r.closeBitByte();
        assertEquals(r.readBits(3), 0b011L);
    }

    @Test
    public void readerReportsCapacityBeforeFeed() {
        BitReader r = new BitReader();
        assertFalse(r.hasBits(1));
        assertFalse(r.hasBytes(1));
        assertEquals(r.available(), 0);
    }

    @Test
    public void readerEofOnEmptyInput() {
        BitReader r = new BitReader();
        assertThrows(UncheckedIOException.class, () -> r.readBits(1));
    }

    @Test
    public void readerEofWhenBitByteUnavailable() {
        BitReader r = new BitReader();
        r.feed(new byte[] { 0x55 }, 0, 1);
        assertEquals(r.readBits(8), 0x55L);
        assertThrows(UncheckedIOException.class, () -> r.readBits(1));
    }

    @Test
    public void readerEofOnTruncatedByteAlignedRead() {
        BitReader r = new BitReader();
        r.feed(new byte[] { 0x01, 0x02 }, 0, 2);
        assertThrows(UncheckedIOException.class, () -> r.readBytes(new byte[3], 0, 3));
    }

    @Test
    public void readerOneByteAtATimeMatchesBulk() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        w.writeBits(1, 1);
        w.writeBytes(new byte[] { 0x10, 0x20 });
        w.writeBits(1, 1);
        w.writeBits(6, 0b010101);
        byte[] encoded = sink.toByteArray();

        BitReader r = new BitReader();
        for (byte b : encoded) {
            r.feed(new byte[] { b }, 0, 1);
        }
        assertEquals(r.readBits(1), 1L);
        byte[] data = new byte[2];
        r.readBytes(data, 0, 2);
        assertEquals(data, new byte[] { 0x10, 0x20 });
        assertEquals(r.readBits(1), 1L);
        assertEquals(r.readBits(6), 0b010101L);
    }

    @Test
    public void readerHasBitsAcrossMultipleFeeds() {
        BitReader r = new BitReader();
        // Need 12 bits. Feed one byte at a time, hasBits should flip from false to true.
        assertFalse(r.hasBits(12));
        r.feed(new byte[] { (byte) 0xAB }, 0, 1);
        assertFalse(r.hasBits(12));
        r.feed(new byte[] { (byte) 0xC0 }, 0, 1);
        assertTrue(r.hasBits(12));
        assertEquals(r.readBits(12), 0xABCL);
    }

    private static BitReader roundTrip(java.util.function.Consumer<BitWriter> writes) {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BitWriter w = new BitWriter(sink);
        writes.accept(w);
        w.closeBitByte();
        byte[] encoded = sink.toByteArray();
        BitReader r = new BitReader();
        r.feed(encoded, 0, encoded.length);
        return r;
    }

    private static final class RecordingStream extends OutputStream {
        private final List<Byte> bytes = new ArrayList<>();

        @Override
        public void write(int b) throws IOException {
            bytes.add((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) {
                bytes.add(b[off + i]);
            }
        }

        byte[] flushedSoFar() {
            byte[] out = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                out[i] = bytes.get(i);
            }
            return out;
        }
    }
}
