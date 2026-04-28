package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/** Writes a fixed-width big-endian integer from a single {@link Event.IntegerScalar}. */
public final class IntegerEncoder implements ValueEncoder {
    private final int byteSize;
    private long value;
    private int remainingBytes;
    private boolean valueRead = false;

    public IntegerEncoder(int byteSize) {
        this.byteSize = byteSize;
        this.remainingBytes = byteSize;
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (!valueRead) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            value = ((Event.IntegerScalar) events.read()).value();
            valueRead = true;
        }
        while (remainingBytes > 0 && sink.writableBytes() > 0) {
            int b = (int) ((value >>> ((remainingBytes - 1) * 8)) & 0xFF);
            sink.writeUnsignedByte(b);
            remainingBytes--;
        }
        return remainingBytes == 0;
    }
}
