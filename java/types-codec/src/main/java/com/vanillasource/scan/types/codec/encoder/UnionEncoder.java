package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.type.Union;

import java.util.List;

/**
 * Composes the union's phases via {@link ValueEncoder#andThen}: consume a
 * single {@code Constructor(j)} event, write the {@code ceil(log2 n)}-bit
 * discriminator (zero bits when n = 1), then run the chosen constructor's
 * fields inline (via {@link StructEncoder}). Bit state passes through
 * naturally.
 */
public final class UnionEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public UnionEncoder(List<List<Type>> constructorFields) {
        int discriminatorBits = Union.discriminatorBits(constructorFields.size());
        int[] indexHolder = new int[1];
        pipeline = consumeConstructor(constructorFields.size(), indexHolder)
                .andThen(writeDiscriminator(discriminatorBits, indexHolder))
                .andThen(runBody(constructorFields, indexHolder));
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder consumeConstructor(int constructorCount, int[] indexHolder) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            int index = ((Event.Constructor) events.read()).index();
            if (index < 0 || index >= constructorCount) {
                throw new IllegalArgumentException(
                        "constructor index " + index + " out of range [0,"
                                + constructorCount + ")");
            }
            indexHolder[0] = index;
            return true;
        };
    }

    private static ValueEncoder writeDiscriminator(int discriminatorBits, int[] indexHolder) {
        if (discriminatorBits == 0) {
            return (events, sink) -> true;
        }
        return (events, sink) -> {
            if (sink.writableBits() < discriminatorBits) {
                return false;
            }
            sink.writeBits(indexHolder[0], discriminatorBits);
            return true;
        };
    }

    private static ValueEncoder runBody(List<List<Type>> constructorFields, int[] indexHolder) {
        return new ValueEncoder() {
            private ValueEncoder body;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                List<Type> fields = constructorFields.get(indexHolder[0]);
                if (fields.isEmpty()) {
                    return true;
                }
                if (body == null) {
                    body = new StructEncoder(fields);
                }
                return body.generate(events, sink);
            }
        };
    }
}
