package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueEncoder;

/**
 * Consumes a single {@code StartStream} event on entry, then iterates items
 * ({@code StartItem}, item events, {@code EndItem}) for as long as events are
 * available and the sink has room. Always returns {@code false} from
 * {@link #generate}: a stream has no terminator on the wire, so completion is
 * declared externally by the transport.
 */
public final class StreamEncoder implements ValueEncoder {
    private final Type itemType;
    private boolean startConsumed = false;
    private ValueEncoder currentItem;
    private int itemPhase = PHASE_BEFORE_START_ITEM;

    private static final int PHASE_BEFORE_START_ITEM = 0;
    private static final int PHASE_BODY = 1;
    private static final int PHASE_BEFORE_END_ITEM = 2;

    public StreamEncoder(Type itemType) {
        this.itemType = itemType;
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (!startConsumed) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            events.read(); // StartStream
            sink.closeBits();
            startConsumed = true;
        }
        while (true) {
            if (itemPhase == PHASE_BEFORE_START_ITEM) {
                if (events.availableEvents() <= 0) {
                    return false;
                }
                events.read(); // StartItem
                currentItem = itemType.createEncoder();
                itemPhase = PHASE_BODY;
            }
            if (itemPhase == PHASE_BODY) {
                if (!currentItem.generate(events, sink)) {
                    return false;
                }
                currentItem = null;
                itemPhase = PHASE_BEFORE_END_ITEM;
            }
            if (itemPhase == PHASE_BEFORE_END_ITEM) {
                if (events.availableEvents() <= 0) {
                    return false;
                }
                events.read(); // EndItem
                itemPhase = PHASE_BEFORE_START_ITEM;
            }
        }
    }
}
