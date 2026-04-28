package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.IntegerDecoder;
import com.vanillasource.scan.types.codec2.encoder.IntegerEncoder;

/** A fixed-width big-endian unsigned integer. */
public final class UnsignedInteger implements Type {
    private final int byteSize;

    public UnsignedInteger(int byteSize) {
        this.byteSize = byteSize;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new IntegerDecoder(byteSize, false);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new IntegerEncoder(byteSize);
    }
}
