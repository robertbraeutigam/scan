package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;

import java.util.List;

/**
 * Walks a list of fields in declaration order, consuming
 * {@code StartField(i)} / {@code EndField(i)} around each field's value
 * events. Bit state is left to the underlying {@link BitSink} so it carries
 * across the field boundaries as the spec requires.
 */
public final class StructEncoder implements ValueEncoder {
    private final List<Type> fieldTypes;
    private int fieldIndex = 0;
    private ValueEncoder currentField;
    private boolean startConsumed = false;
    private boolean fieldComplete = false;

    public StructEncoder(List<Type> fieldTypes) {
        this.fieldTypes = fieldTypes;
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        while (fieldIndex < fieldTypes.size()) {
            if (!startConsumed) {
                if (events.availableEvents() <= 0) {
                    return false;
                }
                events.read();
                startConsumed = true;
                currentField = fieldTypes.get(fieldIndex).createEncoder();
            }
            if (!fieldComplete) {
                if (!currentField.generate(events, sink)) {
                    return false;
                }
                fieldComplete = true;
                currentField = null;
            }
            if (events.availableEvents() <= 0) {
                return false;
            }
            events.read();
            fieldIndex++;
            startConsumed = false;
            fieldComplete = false;
        }
        return true;
    }
}
