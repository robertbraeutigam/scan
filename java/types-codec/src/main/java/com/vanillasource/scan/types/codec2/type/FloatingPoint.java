package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.FloatingPointDecoder;
import com.vanillasource.scan.types.codec2.encoder.FloatingPointEncoder;

/** A 32- or 64-bit big-endian IEEE 754 floating-point value. */
public final class FloatingPoint implements Type {
    private final int byteSize;

    public FloatingPoint(int byteSize) {
        this.byteSize = byteSize;
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
