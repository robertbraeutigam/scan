package com.vanillasource.scan.types.codec;

import java.io.EOFException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Bit/byte source fed by the caller. Dual of {@link BitWriter}: bytes arrive via
 * {@link #feed} as the wire delivers them; the consumer pulls bits and bytes via
 * the typed read methods. Holds the active-bit-byte state and a small byte queue
 * so {@link ValueDecoder} can be a pure frame walker.
 *
 * <p>Reads are non-blocking. Each {@code readBits} / {@code readBytes} requires
 * the caller to first check {@code hasBits} / {@code hasBytes}; calling a read
 * without a successful capacity check is a programming error.
 *
 * <p>The byte queue self-compacts on every feed when the read cursor has advanced,
 * so a long-running stream does not accumulate consumed bytes.
 */
public final class BitReader {
    private byte[] buffer;
    private int writePos;
    private int readPos;
    private int activeBitByte;
    private int bitsUsed;
    private boolean active;

    BitReader() {
        this(64);
    }

    BitReader(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
    }

    void feed(byte[] src, int off, int len) {
        if (readPos > 0) {
            int unread = writePos - readPos;
            if (unread > 0) {
                System.arraycopy(buffer, readPos, buffer, 0, unread);
            }
            writePos = unread;
            readPos = 0;
        }
        ensureCapacity(writePos + len);
        System.arraycopy(src, off, buffer, writePos, len);
        writePos += len;
    }

    public int available() {
        return writePos - readPos;
    }

    public boolean hasBits(int k) {
        if (k < 0 || k > 64) {
            throw new IllegalArgumentException("bit count out of range: " + k);
        }
        if (k == 0) {
            return true;
        }
        int free = active ? (8 - bitsUsed) : 0;
        if (free >= k) {
            return true;
        }
        int needed = (k - free + 7) / 8;
        return available() >= needed;
    }

    public long readBits(int k) {
        if (k < 0 || k > 64) {
            throw new IllegalArgumentException("bit count out of range: " + k);
        }
        if (k == 0) {
            return 0L;
        }
        if (!hasBits(k)) {
            throw new UncheckedIOException(new EOFException(
                    "requested " + k + " bits but only " + available()
                            + " buffered bytes plus " + (active ? (8 - bitsUsed) : 0) + " free bits"));
        }
        long result = 0L;
        int remaining = k;
        while (remaining > 0) {
            int free = active ? (8 - bitsUsed) : 0;
            int groupSize;
            if (free > 0) {
                groupSize = Math.min(remaining, free);
            } else {
                activeBitByte = buffer[readPos++] & 0xFF;
                bitsUsed = 0;
                active = true;
                free = 8;
                groupSize = Math.min(remaining, free);
            }
            int shift = (8 - bitsUsed) - groupSize;
            long bits = (activeBitByte >>> shift) & ((1L << groupSize) - 1);
            result = (result << groupSize) | bits;
            bitsUsed += groupSize;
            if (bitsUsed == 8) {
                active = false;
                bitsUsed = 0;
            }
            remaining -= groupSize;
        }
        return result;
    }

    public boolean hasBytes(int n) {
        return available() >= n;
    }

    public void readBytes(byte[] dst, int off, int n) {
        if (available() < n) {
            throw new UncheckedIOException(new EOFException(
                    "requested " + n + " bytes, only " + available() + " buffered"));
        }
        System.arraycopy(buffer, readPos, dst, off, n);
        readPos += n;
    }

    public int readOneByte() {
        if (readPos >= writePos) {
            throw new UncheckedIOException(new EOFException("no buffered bytes"));
        }
        return buffer[readPos++] & 0xFF;
    }

    /**
     * Reads {@code byteSize} bytes and assembles them as a big-endian unsigned value.
     * Caller must ensure {@code hasBytes(byteSize)} first; this method does not check.
     */
    public long readBigEndianBytes(int byteSize) {
        long result = 0L;
        for (int i = 0; i < byteSize; i++) {
            result = (result << 8) | readOneByte();
        }
        return result;
    }

    public void closeBitByte() {
        active = false;
        activeBitByte = 0;
        bitsUsed = 0;
    }

    private void ensureCapacity(int needed) {
        if (needed <= buffer.length) {
            return;
        }
        int newSize = buffer.length;
        while (newSize < needed) {
            newSize <<= 1;
        }
        buffer = Arrays.copyOf(buffer, newSize);
    }
}
