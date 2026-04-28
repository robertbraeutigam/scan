package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.VariableLengthIntegerDecoder;
import com.vanillasource.scan.types.codec2.encoder.VariableLengthIntegerEncoder;

/** An unsigned variable-length integer with a configurable maximum byte length. */
public final class VariableLengthInteger implements Type {
    private final int maxBytes;

    public VariableLengthInteger(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new VariableLengthIntegerDecoder(maxBytes);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new VariableLengthIntegerEncoder(maxBytes);
    }
}
