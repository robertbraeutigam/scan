package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/** Writes a fixed-width big-endian integer from a single {@link Event.IntegerScalar}. */
public final class IntegerEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public IntegerEncoder(int byteSize) {
        long[] valueHolder = new long[1];
        pipeline = consumeValue(valueHolder)
                .andThen(writeBytes(byteSize, valueHolder));
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder consumeValue(long[] valueHolder) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            valueHolder[0] = ((Event.IntegerScalar) events.read()).value();
            return true;
        };
    }

    private static ValueEncoder writeBytes(int byteSize, long[] valueHolder) {
        return new ValueEncoder() {
            private int remainingBytes = byteSize;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                long value = valueHolder[0];
                while (remainingBytes > 0 && sink.writableBytes() > 0) {
                    int b = (int) ((value >>> ((remainingBytes - 1) * 8)) & 0xFF);
                    sink.writeUnsignedByte(b);
                    remainingBytes--;
                }
                return remainingBytes == 0;
            }
        };
    }
}
