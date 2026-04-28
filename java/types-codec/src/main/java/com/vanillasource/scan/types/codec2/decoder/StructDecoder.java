package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitSource;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.EventSink;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;

import java.util.List;

/**
 * Walks a list of fields in declaration order, emitting
 * {@code StartField(i)} / {@code EndField(i)} around each field's value
 * events. Bit state is left to the underlying {@link BitSource} so it carries
 * across the field boundaries as the spec requires.
 *
 * <p>If a field's decoder never completes (e.g. a {@code Stream}) the struct
 * decoder also never completes — no {@code EndField} is emitted, matching
 * "no {@code EndField(i)} is emitted — the stream runs until the enclosing
 * transport ends" in TYPES.md.
 */
public final class StructDecoder implements ValueDecoder {
    private final List<Type> fieldTypes;
    private int fieldIndex = 0;
    private ValueDecoder currentField;
    private boolean startEmitted = false;
    private boolean fieldComplete = false;

    public StructDecoder(List<Type> fieldTypes) {
        this.fieldTypes = fieldTypes;
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        while (fieldIndex < fieldTypes.size()) {
            if (!startEmitted) {
                if (sink.writableEvents() <= 0) {
                    return false;
                }
                sink.put(new Event.StartField(fieldIndex));
                startEmitted = true;
                currentField = fieldTypes.get(fieldIndex).createDecoder();
            }
            if (!fieldComplete) {
                if (!currentField.parse(bits, sink)) {
                    return false;
                }
                fieldComplete = true;
                currentField = null;
            }
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.EndField(fieldIndex));
            fieldIndex++;
            startEmitted = false;
            fieldComplete = false;
        }
        return true;
    }
}
