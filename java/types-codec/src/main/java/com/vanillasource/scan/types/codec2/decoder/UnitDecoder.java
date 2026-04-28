package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.EventSink;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/** Zero-byte decoder: emits a single {@link Event.UnitScalar} and completes. */
public final class UnitDecoder implements ValueDecoder {
    public UnitDecoder() {
    }

    @Override
    public boolean parse(BitReader bits, EventSink sink) {
        if (sink.writableEvents() <= 0) {
            return false;
        }
        sink.put(new Event.UnitScalar());
        return true;
    }
}
