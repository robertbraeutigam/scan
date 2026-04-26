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
 * close the bit state. Without an open bit byte, {@code write} calls pass straight
 * through to the delegate.
 *
 * <p>{@link OutputStream#write} overrides drop the {@code throws IOException}
 * declaration and wrap delegate failures as {@link UncheckedIOException}, matching
 * the rest of the codec's exception style. Callers typed as {@code OutputStream}
 * still satisfy the parent's checked-exception signature.
 */
final class BitWriter extends OutputStream {
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

    @Override
    public void write(int b) {
        try {
            if (active) {
                appendSuffix((byte) b);
            } else {
                delegate.write(b & 0xFF);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        try {
            if (active) {
                ensureSuffixCapacity(suffixLen + len);
                System.arraycopy(b, off, suffix, suffixLen, len);
                suffixLen += len;
            } else {
                delegate.write(b, off, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    void closeBitByte() {
        if (active) {
            emitActive();
        }
    }

    @Override
    public void flush() {
        try {
            delegate.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            closeBitByte();
            delegate.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private void appendSuffix(byte b) {
        ensureSuffixCapacity(suffixLen + 1);
        suffix[suffixLen++] = b;
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
