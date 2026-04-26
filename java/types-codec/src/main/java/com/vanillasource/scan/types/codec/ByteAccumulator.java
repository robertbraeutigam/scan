package com.vanillasource.scan.types.codec;

import java.io.EOFException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Growable byte buffer with separate write and read cursors. Backs the push-style
 * decoder: {@link ValueDecoder#feed} appends; the decoder consumes as types complete.
 */
final class ByteAccumulator {
    private byte[] buffer;
    private int writePos;
    private int readPos;

    ByteAccumulator() {
        this(64);
    }

    ByteAccumulator(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
        this.writePos = 0;
        this.readPos = 0;
    }

    void append(byte[] src, int off, int len) {
        ensureCapacity(writePos + len);
        System.arraycopy(src, off, buffer, writePos, len);
        writePos += len;
    }

    int available() {
        return writePos - readPos;
    }

    byte readOne() {
        if (readPos >= writePos) {
            throw new UncheckedIOException(new EOFException("no buffered bytes"));
        }
        return buffer[readPos++];
    }

    void readInto(byte[] dst, int off, int len) {
        if (available() < len) {
            throw new UncheckedIOException(new EOFException(
                    "requested " + len + " bytes, only " + available() + " buffered"));
        }
        System.arraycopy(buffer, readPos, dst, off, len);
        readPos += len;
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
