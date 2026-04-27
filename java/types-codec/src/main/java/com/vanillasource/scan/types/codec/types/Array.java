package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.SizeConstraint;
import com.vanillasource.scan.types.codec.Type;

import java.util.Objects;
import java.util.OptionalInt;

public record Array(Type element, SizeConstraint size) implements Type {
    public Array {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(size, "size");
        if (element.containsStream()) {
            throw new IllegalArgumentException("Array element must not contain a Stream");
        }
    }

    @Override public boolean containsStream() { return false; }
    @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }

    @Override
    public DecoderFrame createDecodeFrame() {
        element.requireSupportedAsArrayElement();
        return new DecoderFrames.ArrayFrame(this);
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        element.requireSupportedAsArrayElement();
        return new EncoderFrames.ArrayFrame(this);
    }
}
