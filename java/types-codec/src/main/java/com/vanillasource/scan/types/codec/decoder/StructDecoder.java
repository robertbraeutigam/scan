package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;

import java.util.List;

/**
 * Composes the struct's fields via {@link ValueDecoder#andThen}: for each field
 * in declaration order emit {@code StartField(i)} / {@code EndField(i)} around
 * its value events. Bit state is left to the underlying {@link BitSource} so
 * it carries across the field boundaries as the spec requires.
 *
 * <p>If a field's decoder never completes (e.g. a {@code Stream}) the struct
 * decoder also never completes — no {@code EndField} is emitted, matching
 * "no {@code EndField(i)} is emitted — the stream runs until the enclosing
 * transport ends" in TYPES.md.
 */
public final class StructDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public StructDecoder(List<Type> fieldTypes) {
        ValueDecoder p = noOp();
        for (int i = 0; i < fieldTypes.size(); i++) {
            p = p.andThen(fieldTypes.get(i).createDecoder()
                    .between(new Event.StartField(i), new Event.EndField(i)));
        }
        pipeline = p;
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder noOp() {
        return (bits, sink) -> true;
    }
}
