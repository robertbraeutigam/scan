package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.UnionDecoder;
import com.vanillasource.scan.types.codec.encoder.UnionEncoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-constructor type. The wire form is a {@code ceil(log2 n)}-bit
 * discriminator (n = number of constructors; zero bits for n = 1) followed by
 * the chosen constructor's fields. Bit state carries from the discriminator
 * into the body per TYPES.md "Per-Type Encoding" §"Multi-constructor type".
 */
public final class Union implements Type {
    private final List<List<Type>> constructorFields;

    public Union(List<List<Type>> constructorFields) {
        if (constructorFields.isEmpty()) {
            throw new IllegalArgumentException("Union must have at least one constructor");
        }
        List<List<Type>> copy = new ArrayList<>(constructorFields.size());
        for (List<Type> fields : constructorFields) {
            copy.add(List.copyOf(fields));
        }
        this.constructorFields = List.copyOf(copy);
    }

    public List<List<Type>> constructorFields() {
        return constructorFields;
    }

    /** Number of bits used for the discriminator on the wire. */
    public int discriminatorBits() {
        return discriminatorBits(constructorFields.size());
    }

    public static int discriminatorBits(int constructorCount) {
        if (constructorCount <= 1) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(constructorCount - 1);
    }

    @Override
    public ValueDecoder createDecoder() {
        return new UnionDecoder(constructorFields);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new UnionEncoder(constructorFields);
    }
}
