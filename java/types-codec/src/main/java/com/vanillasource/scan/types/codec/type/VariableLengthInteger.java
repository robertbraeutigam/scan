package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.VariableLengthIntegerDecoder;
import com.vanillasource.scan.types.codec.encoder.VariableLengthIntegerEncoder;

/** An unsigned variable-length integer with a configurable maximum byte length. */
public final class VariableLengthInteger implements Type {
    private final int maxBytes;

    public VariableLengthInteger(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int maxBytes() {
        return maxBytes;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new VariableLengthIntegerDecoder(maxBytes);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new VariableLengthIntegerEncoder(maxBytes);
    }

    @Override
    public boolean isPrimitiveByte() {
        return maxBytes == 1;
    }
}
