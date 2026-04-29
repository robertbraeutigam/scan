package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Emits {@code StartContainer(STREAM, 0)} once on entry, then iterates items
 * ({@code StartItem}, item events, {@code EndItem}) for as long as bytes are
 * available and the sink has room. Always returns {@code false} from
 * {@link #parse}: a stream has no terminator on the wire, so completion is
 * declared externally by the transport. The {@code count} field of the Start
 * event is meaningless for streams and is set to {@code 0}.
 */
public final class StreamDecoder implements ValueDecoder {
    private final Type itemType;
    private boolean startEmitted = false;
    private ValueDecoder currentItem;
    private int itemPhase = PHASE_BEFORE_START_ITEM;

    private static final int PHASE_BEFORE_START_ITEM = 0;
    private static final int PHASE_BODY = 1;
    private static final int PHASE_BEFORE_END_ITEM = 2;

    public StreamDecoder(Type itemType) {
        this.itemType = itemType;
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        if (!startEmitted) {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.StartContainer(ContainerKind.STREAM, 0L));
            startEmitted = true;
        }
        while (true) {
            if (itemPhase == PHASE_BEFORE_START_ITEM) {
                if (sink.writableEvents() <= 0) {
                    return false;
                }
                if (bits.availableBytes() <= 0 && bits.availableBits() <= 0) {
                    return false;
                }
                sink.put(new Event.StartItem());
                currentItem = itemType.createDecoder();
                itemPhase = PHASE_BODY;
            }
            if (itemPhase == PHASE_BODY) {
                if (!currentItem.parse(bits, sink)) {
                    return false;
                }
                currentItem = null;
                itemPhase = PHASE_BEFORE_END_ITEM;
            }
            if (itemPhase == PHASE_BEFORE_END_ITEM) {
                if (sink.writableEvents() <= 0) {
                    return false;
                }
                sink.put(new Event.EndItem());
                itemPhase = PHASE_BEFORE_START_ITEM;
            }
        }
    }
}
