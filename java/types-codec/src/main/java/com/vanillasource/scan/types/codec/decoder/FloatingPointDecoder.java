package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/** Reads a 32- or 64-bit big-endian IEEE 754 float and emits a {@link Event.FloatingPointScalar}. */
public final class FloatingPointDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public FloatingPointDecoder(int byteSize) {
        if (byteSize != 4 && byteSize != 8) {
            throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
        }
        long[] bitsHolder = new long[1];
        pipeline = readBytes(byteSize, bitsHolder)
                .andThen(emitFloat(byteSize, bitsHolder));
    }

    @Override
    public boolean parse(BitSource reader, EventSink sink) {
        return pipeline.parse(reader, sink);
    }

    private static ValueDecoder readBytes(int byteSize, long[] bitsHolder) {
        return new ValueDecoder() {
            private int remainingBytes = byteSize;
            private long bits = 0;

            @Override
            public boolean parse(BitSource reader, EventSink sink) {
                while (remainingBytes > 0 && reader.availableBytes() > 0) {
                    bits = (bits << 8) | reader.readUnsignedByte();
                    remainingBytes--;
                }
                if (remainingBytes > 0) {
                    return false;
                }
                bitsHolder[0] = bits;
                return true;
            }
        };
    }

    private static ValueDecoder emitFloat(int byteSize, long[] bitsHolder) {
        return (reader, sink) -> {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            double value = (byteSize == 4)
                    ? Float.intBitsToFloat((int) bitsHolder[0])
                    : Double.longBitsToDouble(bitsHolder[0]);
            sink.write(new Event.FloatingPointScalar(value));
            return true;
        };
    }
}
