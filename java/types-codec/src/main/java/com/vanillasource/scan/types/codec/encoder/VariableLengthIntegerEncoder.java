package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Writes an unsigned variable-length integer. Each non-final byte has its high bit set
 * and contributes 7 value bits; the final byte contributes 8 value bits when {@code maxBytes}
 * is reached, otherwise 7 value bits with its high bit clear.
 */
public final class VariableLengthIntegerEncoder implements ValueEncoder {
    private final int maxBytes;
    private byte[] buffer;
    private int totalBytes = 0;
    private int bytesWritten = 0;
    private boolean valueRead = false;

    public VariableLengthIntegerEncoder(int maxBytes) {
        if (maxBytes < 1 || maxBytes > 8) {
            throw new IllegalArgumentException("maxBytes must be 1..8: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (!valueRead) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            long value = ((Event.IntegerScalar) events.read()).value();
            buffer = encode(value);
            totalBytes = buffer.length;
            valueRead = true;
        }
        while (bytesWritten < totalBytes && sink.writableBytes() > 0) {
            sink.writeUnsignedByte(buffer[bytesWritten] & 0xFF);
            bytesWritten++;
        }
        return bytesWritten == totalBytes;
    }

    private byte[] encode(long value) {
        int n = 1;
        while (n < maxBytes && (value >>> (7 * n)) != 0) {
            n++;
        }
        byte[] out = new byte[n];
        if (n == maxBytes) {
            out[n - 1] = (byte) (value & 0xFF);
            long remaining = value >>> 8;
            for (int i = n - 2; i >= 0; i--) {
                out[i] = (byte) (0x80 | (remaining & 0x7F));
                remaining >>>= 7;
            }
        } else {
            out[n - 1] = (byte) (value & 0x7F);
            long remaining = value >>> 7;
            for (int i = n - 2; i >= 0; i--) {
                out[i] = (byte) (0x80 | (remaining & 0x7F));
                remaining >>>= 7;
            }
        }
        return out;
    }
}
