package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.IntegerDecoder;
import com.vanillasource.scan.types.codec.encoder.IntegerEncoder;

/** A fixed-width big-endian two's-complement signed integer. */
public final class SignedInteger implements Type {
    private final int byteSize;

    public SignedInteger(int byteSize) {
        this.byteSize = byteSize;
    }

    public int byteSize() {
        return byteSize;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new IntegerDecoder(byteSize, true);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new IntegerEncoder(byteSize);
    }

    @Override
    public boolean isPrimitiveByte() {
        return byteSize == 1;
    }
}
