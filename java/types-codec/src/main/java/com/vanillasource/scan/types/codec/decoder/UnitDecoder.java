package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/** Zero-byte decoder: emits a single {@link Event.UnitScalar} and completes. */
public final class UnitDecoder implements ValueDecoder {
    public UnitDecoder() {
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        if (sink.writableEvents() <= 0) {
            return false;
        }
        sink.put(new Event.UnitScalar());
        return true;
    }
}
