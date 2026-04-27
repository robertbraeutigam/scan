package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

import java.util.Objects;
import java.util.OptionalInt;

public record Stream(Type element) implements Type {
    public Stream {
        Objects.requireNonNull(element, "element");
        if (element.containsStream()) {
            throw new IllegalArgumentException("Stream element must not contain a Stream");
        }
    }

    @Override public boolean containsStream() { return true; }
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
