package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Reads an unsigned variable-length integer. Each non-final byte has its high bit set
 * and contributes 7 value bits; the final byte contributes 8 value bits and is final
 * either because its high bit is clear or because {@code maxBytes} bytes have been read.
 */
public final class VariableLengthIntegerDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public VariableLengthIntegerDecoder(int maxBytes) {
        if (maxBytes < 1 || maxBytes > 8) {
            throw new IllegalArgumentException("maxBytes must be 1..8: " + maxBytes);
        }
        long[] valueHolder = new long[1];
        pipeline = readBytes(maxBytes, valueHolder)
                .andThen(ValueDecoder.writeEvent(() -> new Event.IntegerScalar(
                        valueHolder[0], valueHolder[0] == 0 ? 0 : 1)));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder readBytes(int maxBytes, long[] valueHolder) {
        return new ValueDecoder() {
            private int bytesRead = 0;
            private long accumulator = 0;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                while (bits.availableBytes() > 0) {
                    int b = bits.readUnsignedByte();
                    bytesRead++;
                    if (bytesRead == maxBytes) {
                        accumulator = (accumulator << 8) | (b & 0xFF);
                        valueHolder[0] = accumulator;
                        return true;
                    }
                    if ((b & 0x80) == 0) {
                        accumulator = (accumulator << 7) | (b & 0xFF);
                        valueHolder[0] = accumulator;
                        return true;
                    }
                    accumulator = (accumulator << 7) | (b & 0x7F);
                }
                return false;
            }
        };
    }

}
