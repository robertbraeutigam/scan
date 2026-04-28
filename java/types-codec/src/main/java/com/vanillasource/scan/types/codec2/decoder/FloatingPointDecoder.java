package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/** Reads a 32- or 64-bit big-endian IEEE 754 float and emits a {@link DecodingEvent.FloatingPointScalar}. */
public final class FloatingPointDecoder implements ValueDecoder {
    private final int byteSize;
    private int remainingBytes;
    private long bits = 0;

    FloatingPointDecoder(int byteSize) {
        if (byteSize != 4 && byteSize != 8) {
            throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
        }
        this.byteSize = byteSize;
        this.remainingBytes = byteSize;
    }

    @Override
    public boolean parse(BitReader reader, DecodingEventHandler handler) {
        while (remainingBytes > 0 && reader.availableBytes() > 0) {
            bits = (bits << 8) | reader.readUnsignedByte();
            remainingBytes--;
        }
        if (remainingBytes > 0) {
            return false;
        }
        double value = (byteSize == 4)
                ? Float.intBitsToFloat((int) bits)
                : Double.longBitsToDouble(bits);
        handler.onEvent(new DecodingEvent.FloatingPointScalar(value));
        return true;
    }
}
