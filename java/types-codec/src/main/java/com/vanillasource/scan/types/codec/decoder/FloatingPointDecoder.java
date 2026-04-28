package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/** Reads a 32- or 64-bit big-endian IEEE 754 float and emits a {@link Event.FloatingPointScalar}. */
public final class FloatingPointDecoder implements ValueDecoder {
    private final int byteSize;
    private int remainingBytes;
    private long bits = 0;
    private boolean readyToEmit = false;

    public FloatingPointDecoder(int byteSize) {
        if (byteSize != 4 && byteSize != 8) {
            throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
        }
        this.byteSize = byteSize;
        this.remainingBytes = byteSize;
    }

    @Override
    public boolean parse(BitSource reader, EventSink sink) {
        if (!readyToEmit) {
            while (remainingBytes > 0 && reader.availableBytes() > 0) {
                bits = (bits << 8) | reader.readUnsignedByte();
                remainingBytes--;
            }
            if (remainingBytes > 0) {
                return false;
            }
            readyToEmit = true;
        }
        if (sink.writableEvents() <= 0) {
            return false;
        }
        double value = (byteSize == 4)
                ? Float.intBitsToFloat((int) bits)
                : Double.longBitsToDouble(bits);
        sink.put(new Event.FloatingPointScalar(value));
        return true;
    }
}
