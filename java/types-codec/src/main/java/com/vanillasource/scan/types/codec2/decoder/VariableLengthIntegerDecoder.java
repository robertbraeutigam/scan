package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitSource;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.EventSink;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/**
 * Reads an unsigned variable-length integer. Each non-final byte has its high bit set
 * and contributes 7 value bits; the final byte contributes 8 value bits and is final
 * either because its high bit is clear or because {@code maxBytes} bytes have been read.
 */
public final class VariableLengthIntegerDecoder implements ValueDecoder {
    private final int maxBytes;
    private int bytesRead = 0;
    private long accumulator = 0;
    private boolean readyToEmit = false;

    public VariableLengthIntegerDecoder(int maxBytes) {
        if (maxBytes < 1 || maxBytes > 8) {
            throw new IllegalArgumentException("maxBytes must be 1..8: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        if (!readyToEmit) {
            while (bits.availableBytes() > 0) {
                int b = bits.readUnsignedByte();
                bytesRead++;
                if (bytesRead == maxBytes) {
                    accumulator = (accumulator << 8) | (b & 0xFF);
                    readyToEmit = true;
                    break;
                }
                if ((b & 0x80) == 0) {
                    accumulator = (accumulator << 7) | (b & 0xFF);
                    readyToEmit = true;
                    break;
                }
                accumulator = (accumulator << 7) | (b & 0x7F);
            }
            if (!readyToEmit) {
                return false;
            }
        }
        if (sink.writableEvents() <= 0) {
            return false;
        }
        int sign = accumulator == 0 ? 0 : 1;
        sink.put(new Event.IntegerScalar(accumulator, sign));
        return true;
    }
}
