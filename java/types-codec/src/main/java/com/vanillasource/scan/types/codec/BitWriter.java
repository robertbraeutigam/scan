package com.vanillasource.scan.types.codec;

import java.util.Arrays;

/**
 * Mechanical writer for the Values Binary Representation. Tracks a byte cursor and an
 * optional "active bit byte" earlier in the stream whose unfilled slots are still
 * available for sub-byte writes — see TYPES.md §"Encoder and Decoder State" and
 * §"Writing a k-bit Value" / §"Writing Byte-Aligned Data".
 */
final class BitWriter {
    private byte[] buffer;
    private int cursor;
    private int bitBytePos;
    private int bitsUsed;

    BitWriter() {
        this(64);
    }

    BitWriter(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
        this.cursor = 0;
        this.bitBytePos = -1;
        this.bitsUsed = 0;
    }

    void writeBits(int k, long value) {
        if (k < 0 || k > 64) {
            throw new IllegalArgumentException("bit count out of range: " + k);
        }
        if (k == 0) {
            return;
        }
        long mask = (k == 64) ? -1L : ((1L << k) - 1);
        if ((value & ~mask) != 0L) {
            throw new IllegalArgumentException("value " + value + " does not fit in " + k + " bits");
        }
        int remaining = k;
        while (remaining > 0) {
            int free = (bitBytePos < 0) ? 0 : (8 - bitsUsed);
            int groupSize;
            if (free > 0) {
                groupSize = Math.min(remaining, free);
            } else {
                allocateBitByte();
                free = 8;
                groupSize = Math.min(remaining, free);
            }
            long groupValue = (value >>> (remaining - groupSize)) & ((1L << groupSize) - 1);
            int shift = (8 - bitsUsed) - groupSize;
            buffer[bitBytePos] = (byte) ((buffer[bitBytePos] & 0xFF) | (groupValue << shift));
            bitsUsed += groupSize;
            if (bitsUsed == 8) {
                bitBytePos = -1;
                bitsUsed = 0;
            }
            remaining -= groupSize;
        }
    }

    void writeBytes(byte[] src, int off, int len) {
        ensureCapacity(cursor + len);
        System.arraycopy(src, off, buffer, cursor, len);
        cursor += len;
    }

    void writeBytes(byte[] src) {
        writeBytes(src, 0, src.length);
    }

    void closeBitByte() {
        bitBytePos = -1;
        bitsUsed = 0;
    }

    int byteSize() {
        return cursor;
    }

    byte[] toByteArray() {
        return Arrays.copyOf(buffer, cursor);
    }

    private void allocateBitByte() {
        ensureCapacity(cursor + 1);
        buffer[cursor] = 0;
        bitBytePos = cursor;
        bitsUsed = 0;
        cursor++;
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
