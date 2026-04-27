package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.SizeConstraint;
import com.vanillasource.scan.types.codec.Type;

import java.util.Objects;
import java.util.OptionalInt;

public record Set(Type element, SizeConstraint size) implements Type {
    public Set {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(size, "size");
        if (element.containsStream()) {
            throw new IllegalArgumentException("Set element must not contain a Stream");
        }
    }

    @Override public boolean containsStream() { return false; }
    @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }

    @Override
    public DecoderFrame createDecodeFrame() {
        throw new UnsupportedOperationException("type not supported in iteration 5: " + this);
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        throw new UnsupportedOperationException("type not supported in iteration 5: " + this);
    }
}
