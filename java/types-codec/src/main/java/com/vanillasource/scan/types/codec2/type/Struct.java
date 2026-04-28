package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.StructDecoder;
import com.vanillasource.scan.types.codec2.encoder.StructEncoder;

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

    @Override
    public ValueDecoder createDecoder() {
        return new StructDecoder(fieldTypes);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new StructEncoder(fieldTypes);
    }
}
