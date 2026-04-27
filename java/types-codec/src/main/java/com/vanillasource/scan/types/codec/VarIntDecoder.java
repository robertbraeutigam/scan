package com.vanillasource.scan.types.codec;

/**
 * Push-style accumulator for a single {@link Type.VariableLengthInteger} value.
 * {@link #feed(int)} consumes one byte and returns {@code true} when the integer is
 * complete (continuation bit clear, or {@code maxBytes} bytes consumed); the value
 * is then read via {@link #value()}.
 */
public final class VarIntDecoder {
    private final int maxBytes;
    private long accumulator;
    private int bytesRead;

    public VarIntDecoder(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public boolean feed(int b) {
        bytesRead++;
        if (bytesRead == maxBytes) {
            accumulator = (accumulator << 8) | (b & 0xFF);
            return true;
        }
        if ((b & 0x80) == 0) {
            accumulator = (accumulator << 7) | (b & 0xFF);
            return true;
        }
        accumulator = (accumulator << 7) | (b & 0x7F);
        return false;
    }

    public long value() {
        return accumulator;
    }
}
