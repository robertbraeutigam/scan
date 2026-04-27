package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

import java.util.OptionalInt;

public record Unit() implements Type {
    @Override public boolean containsStream() { return false; }
    @Override public OptionalInt staticBitSize() { return OptionalInt.of(0); }
    @Override public OptionalInt fixedPrimitiveByteSize() { return OptionalInt.of(0); }

    @Override
    public DecoderFrame createDecodeFrame() {
        return new DecoderFrames.UnitFrame();
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        return null;
    }
}
