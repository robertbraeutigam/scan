package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.FloatingPointDecoder;
import com.vanillasource.scan.types.codec.encoder.FloatingPointEncoder;

/** A 32- or 64-bit big-endian IEEE 754 floating-point value. */
public final class FloatingPoint implements Type {
    private final int byteSize;

    public FloatingPoint(int byteSize) {
        this.byteSize = byteSize;
    }

    public int byteSize() {
        return byteSize;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new FloatingPointDecoder(byteSize);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new FloatingPointEncoder(byteSize);
    }
}
