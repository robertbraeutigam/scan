package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.UnitDecoder;
import com.vanillasource.scan.types.codec.encoder.UnitEncoder;

/** A zero-byte unit value. */
public final class Unit implements Type {
    public Unit() {
    }

    @Override
    public ValueDecoder createDecoder() {
        return new UnitDecoder();
    }

    @Override
    public ValueEncoder createEncoder() {
        return new UnitEncoder();
    }
}
