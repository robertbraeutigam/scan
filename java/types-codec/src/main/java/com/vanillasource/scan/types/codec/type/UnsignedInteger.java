package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.IntegerDecoder;
import com.vanillasource.scan.types.codec.encoder.IntegerEncoder;

/** A fixed-width big-endian unsigned integer. */
public final class UnsignedInteger implements Type {
    private final int byteSize;

    public UnsignedInteger(int byteSize) {
        this.byteSize = byteSize;
    }

    public int byteSize() {
        return byteSize;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new IntegerDecoder(byteSize, false);
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
