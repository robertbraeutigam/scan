package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.IntegerDecoder;
import com.vanillasource.scan.types.codec2.encoder.IntegerEncoder;

/** A fixed-width big-endian two's-complement signed integer. */
public final class SignedInteger implements Type {
    private final int byteSize;

    public SignedInteger(int byteSize) {
        this.byteSize = byteSize;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new IntegerDecoder(byteSize, true);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new IntegerEncoder(byteSize);
    }
}
