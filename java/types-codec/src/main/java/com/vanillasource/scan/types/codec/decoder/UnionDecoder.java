package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.type.Union;

import java.util.List;

/**
 * Composes the union's phases via {@link ValueDecoder#andThen}: read the
 * {@code ceil(log2 n)}-bit discriminator (zero bits when n = 1), emit
 * {@code Constructor(j)}, then run the chosen constructor's fields inline (via
 * {@link StructDecoder}). Bit state passes through naturally.
 */
public final class UnionDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public UnionDecoder(List<List<Type>> constructorFields) {
        int discriminatorBits = Union.discriminatorBits(constructorFields.size());
        int[] indexHolder = new int[1];
        pipeline = readDiscriminator(discriminatorBits, constructorFields.size(), indexHolder)
                .andThen(emitConstructor(indexHolder))
                .andThen(runBody(constructorFields, indexHolder));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder readDiscriminator(int discriminatorBits, int constructorCount, int[] indexHolder) {
        return (bits, sink) -> {
            if (discriminatorBits > 0 && bits.availableBits() < discriminatorBits) {
                return false;
            }
            int chosenIndex = (discriminatorBits == 0) ? 0 : bits.readBits(discriminatorBits);
            if (chosenIndex >= constructorCount) {
                throw new IllegalStateException(
                        "invalid discriminator " + chosenIndex
                                + " for union with " + constructorCount + " constructors");
            }
            indexHolder[0] = chosenIndex;
            return true;
        };
    }

    private static ValueDecoder emitConstructor(int[] indexHolder) {
        return (bits, sink) -> {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.write(new Event.Constructor(indexHolder[0]));
            return true;
        };
    }

    private static ValueDecoder runBody(List<List<Type>> constructorFields, int[] indexHolder) {
        return new ValueDecoder() {
            private ValueDecoder body;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                List<Type> fields = constructorFields.get(indexHolder[0]);
                if (fields.isEmpty()) {
                    return true;
                }
                if (body == null) {
                    body = new StructDecoder(fields);
                }
                return body.parse(bits, sink);
            }
        };
    }
}
