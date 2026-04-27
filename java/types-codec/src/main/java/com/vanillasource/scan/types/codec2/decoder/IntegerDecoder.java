package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/** Reads a fixed-width signed big-endian integer with sign extension. */
final class IntegerDecoder implements ValueDecoder {
    private int remainingBytes;
    private final boolean signed;
    private long currentValue = 0;

    IntegerDecoder(int byteSize, boolean signed) {
        this.remainingBytes = byteSize;
        this.signed=signed;
    }

    @Override
    public boolean parse(BitReader bits, DecodingEventHandler handler) {
        return false; // TODO
    }
}
