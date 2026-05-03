package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.StructDecoder;
import com.vanillasource.scan.types.codec.encoder.StructEncoder;

import java.util.List;

/**
 * Single-constructor type whose fields are encoded in declaration order.
 * Bit state carries across fields per TYPES.md "Per-Type Encoding"
 * §"Single-constructor type".
 */
public final class Struct implements Type {
    private final List<Type> fieldTypes;

    public Struct(List<Type> fieldTypes) {
        this.fieldTypes = List.copyOf(fieldTypes);
    }

    public Struct(Type... fieldTypes) {
        this.fieldTypes = List.of(fieldTypes);
    }

    public List<Type> fieldTypes() {
        return fieldTypes;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new StructDecoder(fieldTypes);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new StructEncoder(fieldTypes);
    }
}
