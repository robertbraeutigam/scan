package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;

import java.util.List;

/**
 * Composes the struct's fields via {@link ValueEncoder#andThen}: for each field
 * in declaration order consume {@code StartField(i)} / {@code EndField(i)}
 * around its value events. Bit state is left to the underlying {@link BitSink}
 * so it carries across the field boundaries as the spec requires.
 */
public final class StructEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public StructEncoder(List<Type> fieldTypes) {
        ValueEncoder p = noOp();
        for (Type fieldType : fieldTypes) {
            p = p.andThen(fieldType.createEncoder().bracketed());
        }
        pipeline = p;
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder noOp() {
        return (events, sink) -> true;
    }
}
