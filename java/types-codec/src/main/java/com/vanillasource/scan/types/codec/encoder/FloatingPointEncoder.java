package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/** Writes a 32- or 64-bit big-endian IEEE 754 float from a single {@link Event.FloatingPointScalar}. */
public final class FloatingPointEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public FloatingPointEncoder(int byteSize) {
        if (byteSize != 4 && byteSize != 8) {
            throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
        }
        long[] bitsHolder = new long[1];
        pipeline = consumeValue(byteSize, bitsHolder)
                .andThen(writeBytes(byteSize, bitsHolder));
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder consumeValue(int byteSize, long[] bitsHolder) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            double value = ((Event.FloatingPointScalar) events.read()).value();
            bitsHolder[0] = (byteSize == 4)
                    ? Float.floatToRawIntBits((float) value) & 0xFFFFFFFFL
                    : Double.doubleToRawLongBits(value);
            return true;
        };
    }

    private static ValueEncoder writeBytes(int byteSize, long[] bitsHolder) {
        return new ValueEncoder() {
            private int remainingBytes = byteSize;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                long bits = bitsHolder[0];
                while (remainingBytes > 0 && sink.writableBytes() > 0) {
                    int b = (int) ((bits >>> ((remainingBytes - 1) * 8)) & 0xFF);
                    sink.writeUnsignedByte(b);
                    remainingBytes--;
                }
                return remainingBytes == 0;
            }
        };
    }
}
