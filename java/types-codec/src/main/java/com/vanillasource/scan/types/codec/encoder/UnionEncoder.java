package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.type.Union;

import java.util.List;

/**
 * Consumes a single {@code Constructor(j)} event, writes the
 * {@code ceil(log2 n)}-bit discriminator (zero bits when n = 1), then runs the
 * chosen constructor's fields inline (via {@link StructEncoder}). Bit state
 * passes through naturally.
 */
public final class UnionEncoder implements ValueEncoder {
    private final List<List<Type>> constructorFields;
    private final int discriminatorBits;
    private boolean discriminatorWritten = false;
    private ValueEncoder body;

    public UnionEncoder(List<List<Type>> constructorFields) {
        this.constructorFields = constructorFields;
        this.discriminatorBits = Union.discriminatorBits(constructorFields.size());
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (!discriminatorWritten) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            if (discriminatorBits > 0 && sink.writableBits() < discriminatorBits) {
                return false;
            }
            int index = ((Event.Constructor) events.read()).index();
            if (index < 0 || index >= constructorFields.size()) {
                throw new IllegalArgumentException(
                        "constructor index " + index + " out of range [0,"
                                + constructorFields.size() + ")");
            }
            if (discriminatorBits > 0) {
                sink.writeBits(index, discriminatorBits);
            }
            discriminatorWritten = true;
            List<Type> fields = constructorFields.get(index);
            body = fields.isEmpty() ? null : new StructEncoder(fields);
        }
        if (body == null) {
            return true;
        }
        return body.generate(events, sink);
    }
}
