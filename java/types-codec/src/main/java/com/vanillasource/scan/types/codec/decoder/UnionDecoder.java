package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.type.Union;

import java.util.List;

/**
 * Reads the {@code ceil(log2 n)}-bit discriminator (zero bits when n = 1),
 * emits {@code Constructor(j)}, then runs the chosen constructor's fields
 * inline (via {@link StructDecoder}). Bit state passes through naturally.
 */
public final class UnionDecoder implements ValueDecoder {
    private final List<List<Type>> constructorFields;
    private final int discriminatorBits;
    private boolean discriminatorEmitted = false;
    private int chosenIndex;
    private ValueDecoder body;

    public UnionDecoder(List<List<Type>> constructorFields) {
        this.constructorFields = constructorFields;
        this.discriminatorBits = Union.discriminatorBits(constructorFields.size());
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        if (!discriminatorEmitted) {
            if (discriminatorBits > 0 && bits.availableBits() < discriminatorBits) {
                return false;
            }
            if (sink.writableEvents() <= 0) {
                return false;
            }
            chosenIndex = (discriminatorBits == 0) ? 0 : bits.readBits(discriminatorBits);
            if (chosenIndex >= constructorFields.size()) {
                throw new IllegalStateException(
                        "invalid discriminator " + chosenIndex
                                + " for union with " + constructorFields.size() + " constructors");
            }
            sink.put(new Event.Constructor(chosenIndex));
            discriminatorEmitted = true;
            List<Type> fields = constructorFields.get(chosenIndex);
            body = fields.isEmpty() ? null : new StructDecoder(fields);
        }
        if (body == null) {
            return true;
        }
        return body.parse(bits, sink);
    }
}
