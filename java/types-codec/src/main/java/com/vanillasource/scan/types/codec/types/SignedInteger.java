package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

import java.util.OptionalInt;

public record SignedInteger(int byteSize) implements Type {
    public SignedInteger {
        if (byteSize < 1 || byteSize > 8) {
            throw new IllegalArgumentException("byteSize must be 1..8: " + byteSize);
        }
    }

    @Override public boolean containsStream() { return false; }
    @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
    @Override public OptionalInt fixedPrimitiveByteSize() { return OptionalInt.of(byteSize); }

    @Override
    public DecoderFrame createDecodeFrame() {
        return new SignedIntegerDecoderFrame(byteSize);
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        return new PrimitiveEncoderFrame(this);
    }

    @Override
    public void writeIntegerValue(BitWriter bits, long value) {
        if (byteSize < 8) {
            long max = (1L << (byteSize * 8 - 1)) - 1;
            long min = -(1L << (byteSize * 8 - 1));
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        "value " + value + " does not fit in " + byteSize + " signed bytes");
            }
        }
        bits.writeBigEndianBytes(value, byteSize);
    }
}
