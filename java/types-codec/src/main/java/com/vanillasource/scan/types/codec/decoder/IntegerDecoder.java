package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/** Reads a fixed-width signed or unsigned big-endian integer, sign-extending when signed. */
public final class IntegerDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public IntegerDecoder(int byteSize, boolean signed) {
        long[] valueHolder = new long[1];
        pipeline = readBytes(byteSize, signed, valueHolder)
                .andThen(emitInteger(valueHolder, signed));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder readBytes(int byteSize, boolean signed, long[] valueHolder) {
        return new ValueDecoder() {
            private int remainingBytes = byteSize;
            private long value = 0;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                while (remainingBytes > 0 && bits.availableBytes() > 0) {
                    value = (value << 8) | bits.readUnsignedByte();
                    remainingBytes--;
                }
                if (remainingBytes > 0) {
                    return false;
                }
                if (signed && byteSize < 8) {
                    long signBit = 1L << (byteSize * 8 - 1);
                    if ((value & signBit) != 0) {
                        value |= -1L << (byteSize * 8);
                    }
                }
                valueHolder[0] = value;
                return true;
            }
        };
    }

    private static ValueDecoder emitInteger(long[] valueHolder, boolean signed) {
        return (bits, sink) -> {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            long v = valueHolder[0];
            int sign = signed ? Long.signum(v) : (v == 0 ? 0 : 1);
            sink.write(new Event.IntegerScalar(v, sign));
            return true;
        };
    }
}
