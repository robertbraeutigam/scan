package com.vanillasource.scan.types.codec2.bit;

import com.vanillasource.scan.types.codec2.BitSink;

/**
 * A {@link BitSink} that implements the spec rule from TYPES.md "Writing
 * Byte-Aligned Data": when an active bit byte is open, byte-aligned writes are
 * buffered behind it on the wire, so subsequent {@code writeBits} calls can
 * still pack into the same bit byte. The buffered suffix is flushed when the
 * bit byte fills up or {@link #closeBits()} is called.
 *
 * <p>The sink stores its output in an internal byte buffer that grows on
 * demand; {@link #toBytes()} returns a copy of all bytes flushed so far.
 * Bytes still trapped behind an open bit byte are <em>not</em> visible until
 * the bit byte closes.
 */
public final class BufferedBitSink implements BitSink {
    private byte[] output = new byte[64];
    private int outputLen = 0;

    private boolean active = false;
    private int activeByte = 0;
    private int bitsUsed = 0;

    private byte[] suffix = new byte[16];
    private int suffixLen = 0;

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
            ensureSuffixCapacity(suffixLen + len);
            System.arraycopy(buf, off, suffix, suffixLen, len);
            suffixLen += len;
        } else {
            ensureOutputCapacity(outputLen + len);
            System.arraycopy(buf, off, output, outputLen, len);
            outputLen += len;
        }
        return len;
    }

    @Override
    public int writeUnsignedByte(int unsignedByte) {
        int b = unsignedByte & 0xFF;
        if (active) {
            ensureSuffixCapacity(suffixLen + 1);
            suffix[suffixLen++] = (byte) b;
        } else {
            ensureOutputCapacity(outputLen + 1);
            output[outputLen++] = (byte) b;
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

    /**
     * @return A copy of the bytes flushed to the underlying output. Bytes
     *         buffered behind an open bit byte are not visible until that bit
     *         byte is emitted.
     */
    public byte[] toBytes() {
        byte[] copy = new byte[outputLen];
        System.arraycopy(output, 0, copy, 0, outputLen);
        return copy;
    }

    /** @return The current size of the flushed output in bytes. */
    public int size() {
        return outputLen;
    }

    private void emitActive() {
        ensureOutputCapacity(outputLen + 1 + suffixLen);
        output[outputLen++] = (byte) (activeByte & 0xFF);
        if (suffixLen > 0) {
            System.arraycopy(suffix, 0, output, outputLen, suffixLen);
            outputLen += suffixLen;
            suffixLen = 0;
        }
        active = false;
        activeByte = 0;
        bitsUsed = 0;
    }

    private void ensureOutputCapacity(int needed) {
        if (needed > output.length) {
            int newLen = output.length;
            while (newLen < needed) {
                newLen <<= 1;
            }
            byte[] grown = new byte[newLen];
            System.arraycopy(output, 0, grown, 0, outputLen);
            output = grown;
        }
    }

    private void ensureSuffixCapacity(int needed) {
        if (needed > suffix.length) {
            int newLen = suffix.length;
            while (newLen < needed) {
                newLen <<= 1;
            }
            byte[] grown = new byte[newLen];
            System.arraycopy(suffix, 0, grown, 0, suffixLen);
            suffix = grown;
        }
    }
}
