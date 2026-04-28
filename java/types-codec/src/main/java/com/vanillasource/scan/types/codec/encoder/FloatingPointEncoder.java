package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/** Writes a 32- or 64-bit big-endian IEEE 754 float from a single {@link Event.FloatingPointScalar}. */
public final class FloatingPointEncoder implements ValueEncoder {
    private final int byteSize;
    private long bits;
    private int remainingBytes;
    private boolean valueRead = false;

    public FloatingPointEncoder(int byteSize) {
        if (byteSize != 4 && byteSize != 8) {
            throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
        }
        this.byteSize = byteSize;
        this.remainingBytes = byteSize;
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (!valueRead) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            double value = ((Event.FloatingPointScalar) events.read()).value();
            bits = (byteSize == 4)
                    ? Float.floatToRawIntBits((float) value) & 0xFFFFFFFFL
                    : Double.doubleToRawLongBits(value);
            valueRead = true;
        }
        while (remainingBytes > 0 && sink.writableBytes() > 0) {
            int b = (int) ((bits >>> ((remainingBytes - 1) * 8)) & 0xFF);
            sink.writeUnsignedByte(b);
            remainingBytes--;
        }
        return remainingBytes == 0;
    }
}
