package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.UnitDecoder;
import com.vanillasource.scan.types.codec2.encoder.UnitEncoder;

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
