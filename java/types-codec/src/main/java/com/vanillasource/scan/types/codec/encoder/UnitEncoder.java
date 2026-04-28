package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/** Zero-byte encoder: consumes a single {@link com.vanillasource.scan.types.codec.Event.UnitScalar} and completes. */
public final class UnitEncoder implements ValueEncoder {
    public UnitEncoder() {
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (events.availableEvents() <= 0) {
            return false;
        }
        events.read();
        return true;
    }
}
