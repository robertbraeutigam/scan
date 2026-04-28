package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/** Zero-byte decoder: emits a single {@link DecodingEvent.UnitScalar} and completes. */
public final class UnitDecoder implements ValueDecoder {
    public UnitDecoder() {
    }

    @Override
    public boolean parse(BitReader bits, DecodingEventHandler handler) {
        handler.onEvent(new DecodingEvent.UnitScalar());
        return true;
    }
}
