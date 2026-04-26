package com.vanillasource.scan.types.codec;

import java.io.EOFException;
import java.io.UncheckedIOException;

/**
 * Mechanical reader for the Values Binary Representation. Dual of {@link BitWriter}:
 * sub-byte reads draw from a buffered active byte; byte-aligned reads advance the
 * cursor without disturbing it. See TYPES.md §"Encoder and Decoder State".
 */
final class BitReader {
    private final byte[] input;
    private int cursor;
    private int activeByte;
    private int bitsUsed;
    private boolean active;

    BitReader(byte[] input) {
        this.input = input;
        this.cursor = 0;
        this.activeByte = 0;
        this.bitsUsed = 0;
        this.active = false;
    }

    long readBits(int k) {
        if (k < 0 || k > 64) {
            throw new IllegalArgumentException("bit count out of range: " + k);
        }
        if (k == 0) {
            return 0L;
        }
        int remaining = k;
        long result = 0L;
        while (remaining > 0) {
            int free = active ? (8 - bitsUsed) : 0;
            int groupSize;
            if (free > 0) {
                groupSize = Math.min(remaining, free);
            } else {
                loadActiveByte();
                free = 8;
                groupSize = Math.min(remaining, free);
            }
            int shift = (8 - bitsUsed) - groupSize;
            long bits = (activeByte >>> shift) & ((1L << groupSize) - 1);
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

    void readBytes(byte[] dst, int off, int len) {
        if (cursor + len > input.length) {
            throw new UncheckedIOException(new EOFException(
                    "requested " + len + " bytes at cursor " + cursor + " but input has " + input.length));
        }
        System.arraycopy(input, cursor, dst, off, len);
        cursor += len;
    }

    byte[] readBytes(int len) {
        byte[] out = new byte[len];
        readBytes(out, 0, len);
        return out;
    }

    void closeBitByte() {
        active = false;
        bitsUsed = 0;
    }

    int cursor() {
        return cursor;
    }

    private void loadActiveByte() {
        if (cursor >= input.length) {
            throw new UncheckedIOException(new EOFException(
                    "requested bit byte at cursor " + cursor + " but input has " + input.length));
        }
        activeByte = input[cursor] & 0xFF;
        cursor++;
        bitsUsed = 0;
        active = true;
    }
}
