package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.ValueEncoder;

/** Zero-byte encoder: consumes a single {@link com.vanillasource.scan.types.codec2.Event.UnitScalar} and completes. */
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
