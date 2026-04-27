package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/** Reads a fixed-width signed big-endian integer with sign extension. */
final class SignedIntegerDecoder implements ValueDecoder {
    private int remainingBytes;
    private long currentValue = 0;

    SignedIntegerDecoder(int byteSize) {
        this.remainingBytes = byteSize;
    }

    @Override
    public boolean parse(BitReader bits, DecodingEventHandler handler) {
        return false; // TODO
    }
}
