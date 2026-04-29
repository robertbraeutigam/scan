package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Composes the stream's phases via {@link ValueDecoder#andThen}: emit
 * {@code StartStream} once on entry, then iterate items
 * ({@code StartItem}, item events, {@code EndItem}) for as long as bytes are
 * available and the sink has room. The item-loop phase never completes — a
 * stream has no terminator on the wire, so completion is declared externally
 * by the transport.
 */
public final class StreamDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public StreamDecoder(Type itemType) {
        pipeline = emit(new Event.StartStream()).andThen(itemLoop(itemType));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder emit(Event event) {
        return (bits, sink) -> {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.write(event);
            return true;
        };
    }

    private static ValueDecoder oneItem(Type itemType) {
        return emit(new Event.StartItem())
                .andThen(itemType.createDecoder())
                .andThen(emit(new Event.EndItem()));
    }

    private static ValueDecoder itemLoop(Type itemType) {
        return new ValueDecoder() {
            private ValueDecoder current;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                while (true) {
                    if (current == null) {
                        if (bits.availableBytes() <= 0 && bits.availableBits() <= 0) {
                            return false;
                        }
                        current = oneItem(itemType);
                    }
                    if (!current.parse(bits, sink)) {
                        return false;
                    }
                    current = null;
                }
            }
        };
    }
}
