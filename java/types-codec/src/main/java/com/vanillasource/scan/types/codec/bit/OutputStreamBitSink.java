package com.vanillasource.scan.types.codec.bit;

import com.vanillasource.scan.types.codec.BitSink;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * A {@link BitSink} that implements the spec rule from TYPES.md "Writing
 * Byte-Aligned Data": when an active bit byte is open, byte-aligned writes are
 * buffered behind it on the wire, so subsequent {@code writeBits} calls can
 * still pack into the same bit byte. The buffered suffix is flushed to the
 * underlying {@link OutputStream} when the bit byte fills up, when
 * {@link #closeBits()} is called, or when the buffer cap from TYPES.md
 * "Bit-byte buffer cap" is reached.
 *
 * <p>{@link IOException}s from the underlying stream are wrapped in
 * {@link UncheckedIOException} since {@link BitSink}'s methods do not declare
 * checked exceptions.
 */
public final class OutputStreamBitSink implements BitSink {
    /** Buffer cap from TYPES.md §"Bit-byte buffer cap": at most this many byte-aligned bytes may be buffered behind an open bit byte before it is force-closed. */
    static final int BUFFER_CAP = 32;

    private final OutputStream output;

    private boolean active = false;
    private int activeByte = 0;
    private int bitsUsed = 0;

    private final byte[] suffix = new byte[BUFFER_CAP];
    private int suffixLen = 0;

    public OutputStreamBitSink(OutputStream output) {
        this.output = output;
    }

    @Override
    public int writableBytes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int writableBits() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int write(byte[] buf, int off, int len) {
        if (active) {
            int space = BUFFER_CAP - suffixLen;
            int toSuffix = Math.min(len, space);
            if (toSuffix > 0) {
                System.arraycopy(buf, off, suffix, suffixLen, toSuffix);
                suffixLen += toSuffix;
            }
            if (suffixLen >= BUFFER_CAP) {
                emitActive();
            }
            int remaining = len - toSuffix;
            if (remaining > 0) {
                writeOut(buf, off + toSuffix, remaining);
            }
        } else {
            writeOut(buf, off, len);
        }
        return len;
    }

    @Override
    public int writeUnsignedByte(int unsignedByte) {
        int b = unsignedByte & 0xFF;
        if (active) {
            suffix[suffixLen++] = (byte) b;
            if (suffixLen >= BUFFER_CAP) {
                emitActive();
            }
        } else {
            writeOut(b);
        }
        return 1;
    }

    @Override
    public int writeBits(int bits, int count) {
        if (count <= 0) {
            return 0;
        }
        if (count > 8) {
            throw new IllegalArgumentException("writeBits count must be <= 8: " + count);
        }
        int mask = (1 << count) - 1;
        int value = bits & mask;
        if (!active) {
            active = true;
            activeByte = 0;
            bitsUsed = 0;
        }
        int free = 8 - bitsUsed;
        if (count <= free) {
            int shift = free - count;
            activeByte |= (value << shift);
            bitsUsed += count;
            if (bitsUsed == 8) {
                emitActive();
            }
        } else {
            // Spec: not enough free bits → allocate fresh byte; remaining bits
            // of the current bit byte are wasted (zeros).
            emitActive();
            active = true;
            activeByte = (value << (8 - count));
            bitsUsed = count;
        }
        return count;
    }

    @Override
    public void closeBits() {
        if (active) {
            emitActive();
        }
    }

    private void emitActive() {
        writeOut(activeByte & 0xFF);
        if (suffixLen > 0) {
            writeOut(suffix, 0, suffixLen);
            suffixLen = 0;
        }
        active = false;
        activeByte = 0;
        bitsUsed = 0;
    }

    private void writeOut(int b) {
        try {
            output.write(b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeOut(byte[] buf, int off, int len) {
        try {
            output.write(buf, off, len);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
