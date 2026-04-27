package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

import java.util.OptionalInt;

public record FloatingPoint(int byteSize) implements Type {
    public FloatingPoint {
        if (byteSize != 4 && byteSize != 8) {
            throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
        }
    }

    @Override public boolean containsStream() { return false; }
    @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
    @Override public OptionalInt fixedPrimitiveByteSize() { return OptionalInt.of(byteSize); }

    @Override
    public DecoderFrame createDecodeFrame() {
        return new FloatingPointDecoderFrame(byteSize);
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        return new PrimitiveEncoderFrame(this);
    }

    @Override
    public void writeFloatValue(BitWriter bits, double value) {
        if (byteSize == 4) {
            bits.writeBigEndianBytes(Float.floatToRawIntBits((float) value) & 0xFFFFFFFFL, 4);
        } else {
            bits.writeBigEndianBytes(Double.doubleToRawLongBits(value), 8);
        }
    }
}
