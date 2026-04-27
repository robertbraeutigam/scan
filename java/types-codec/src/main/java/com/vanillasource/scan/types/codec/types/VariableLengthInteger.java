package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

import java.util.OptionalInt;

public record VariableLengthInteger(int maxBytes) implements Type {
    public VariableLengthInteger {
        if (maxBytes < 1 || maxBytes > 8) {
            throw new IllegalArgumentException("maxBytes must be 1..8: " + maxBytes);
        }
    }

    @Override public boolean containsStream() { return false; }
    @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }

    @Override
    public DecoderFrame createDecodeFrame() {
        return new DecoderFrames.VariableLengthIntegerFrame(maxBytes);
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        return new EncoderFrames.PrimitiveFrame(this);
    }

    @Override
    public void writeIntegerValue(BitWriter bits, long value) {
        bits.writeVarInt(value, maxBytes);
    }

    @Override
    public void requireSupportedAsArrayElement() {
        throw new UnsupportedOperationException(
                "Array of VariableLengthInteger not supported in iteration 5");
    }
}
