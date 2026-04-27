package com.vanillasource.scan.types.codec;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Bit/byte sink that drains to a delegate {@link OutputStream} as soon as bytes are
 * settled. Implements the bit-accumulation rule from TYPES.md §"Encoder and Decoder
 * State" / §"Writing a k-bit Value" / §"Writing Byte-Aligned Data": a sub-byte
 * write opens an "active bit byte" earlier in the stream; subsequent byte-aligned
 * writes are buffered locally because the bit byte physically precedes them on the
 * wire; when the bit byte fills (or {@link #closeBitByte()} is called), the bit byte
 * is emitted to the delegate followed by the buffered suffix.
 *
 * <p>Worst-case in-flight memory is the bytes between an open bit byte and its
 * close — typically single digits in SCAN encoding, since aggregate boundaries
 * close the bit state. Without an open bit byte, {@code writeBytes} calls pass
 * straight through to the delegate.
 *
 * <p>Dual of {@link BitReader}: the reader is push-fed by the caller and the writer
 * pushes to a delegate. Neither participates in the {@link java.io} type hierarchy
 * — both are codec-owned mechanics with their own typed read/write API.
 */
public final class BitWriter {
    private final OutputStream delegate;
    private int activeBitByte;
    private int bitsUsed;
    private boolean active;
    private byte[] suffix;
    private int suffixLen;

    BitWriter(OutputStream delegate) {
        this.delegate = delegate;
        this.suffix = new byte[16];
    }

    public void writeBits(int k, long value) {
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
            int free = active ? (8 - bitsUsed) : 0;
            int groupSize;
            if (free > 0) {
                groupSize = Math.min(remaining, free);
            } else {
                activeBitByte = 0;
                bitsUsed = 0;
                active = true;
                free = 8;
                groupSize = Math.min(remaining, free);
            }
            long groupValue = (value >>> (remaining - groupSize)) & ((1L << groupSize) - 1);
            int shift = (8 - bitsUsed) - groupSize;
            activeBitByte |= (int) (groupValue << shift);
            bitsUsed += groupSize;
            if (bitsUsed == 8) {
                emitActive();
            }
            remaining -= groupSize;
        }
    }

    public void writeBytes(byte[] src, int off, int len) {
        try {
            if (active) {
                ensureSuffixCapacity(suffixLen + len);
                System.arraycopy(src, off, suffix, suffixLen, len);
                suffixLen += len;
            } else {
                delegate.write(src, off, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeBytes(byte[] src) {
        writeBytes(src, 0, src.length);
    }

    /**
     * Writes a non-negative {@code value} as {@code byteSize} big-endian bytes. Caller
     * must validate the value fits the chosen width; this method does not range-check.
     */
    public void writeBigEndianBytes(long value, int byteSize) {
        byte[] out = new byte[byteSize];
        for (int i = 0; i < byteSize; i++) {
            out[i] = (byte) ((value >>> ((byteSize - 1 - i) * 8)) & 0xFF);
        }
        writeBytes(out);
    }

    /**
     * Writes a non-negative {@code value} as a SCAN-style variable-length integer with
     * up to {@code maxN} bytes. Throws if {@code value} does not fit (TYPES.md
     * §"Per-Type Encoding" / VariableLengthInteger).
     */
    public void writeVarInt(long value, int maxN) {
        if (value < 0) {
            throw new IllegalArgumentException("VariableLengthInteger value must be non-negative: " + value);
        }
        int bitsNeeded = (value == 0) ? 1 : (64 - Long.numberOfLeadingZeros(value));
        int k = -1;
        for (int trial = 1; trial <= maxN; trial++) {
            int capacity = (trial < maxN) ? (7 * trial) : (7 * (trial - 1) + 8);
            if (capacity >= bitsNeeded) {
                k = trial;
                break;
            }
        }
        if (k < 0) {
            throw new IllegalArgumentException(
                    "value " + value + " does not fit in VariableLengthInteger(" + maxN + ")");
        }
        int totalBits = (k < maxN) ? (7 * k) : (7 * (k - 1) + 8);
        byte[] out = new byte[k];
        for (int i = 0; i < k; i++) {
            int groupBits = (i < k - 1) ? 7 : ((k < maxN) ? 7 : 8);
            int bitsAfter = totalBits - 7 * i - groupBits;
            int groupValue = (int) ((value >>> bitsAfter) & ((1L << groupBits) - 1));
            int byteValue = (i < k - 1) ? (0x80 | groupValue) : groupValue;
            out[i] = (byte) byteValue;
        }
        writeBytes(out);
    }

    public void closeBitByte() {
        if (active) {
            emitActive();
        }
    }

    private void emitActive() {
        try {
            delegate.write(activeBitByte & 0xFF);
            if (suffixLen > 0) {
                delegate.write(suffix, 0, suffixLen);
                suffixLen = 0;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        active = false;
        activeBitByte = 0;
        bitsUsed = 0;
    }

    private void ensureSuffixCapacity(int needed) {
        if (needed <= suffix.length) {
            return;
        }
        int newSize = suffix.length;
        while (newSize < needed) {
            newSize <<= 1;
        }
        suffix = Arrays.copyOf(suffix, newSize);
    }
}
