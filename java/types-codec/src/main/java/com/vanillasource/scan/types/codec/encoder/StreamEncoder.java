package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Composes the stream's phases via {@link ValueEncoder#andThen}: consume a
 * single {@code StartStream} event on entry, then iterate items
 * ({@code StartItem}, item events, {@code EndItem}) for as long as events are
 * available and the sink has room. The item-loop phase never completes — a
 * stream has no terminator on the wire, so completion is declared externally
 * by the transport.
 */
public final class StreamEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public StreamEncoder(Type itemType) {
        pipeline = consumeOne().andThen(itemLoop(itemType));
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder consumeOne() {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            events.read();
            return true;
        };
    }

    private static ValueEncoder oneItem(Type itemType) {
        return consumeOne()
                .andThen(itemType.createEncoder())
                .andThen(consumeOne());
    }

    private static ValueEncoder itemLoop(Type itemType) {
        return new ValueEncoder() {
            private ValueEncoder current;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                while (true) {
                    if (current == null) {
                        current = oneItem(itemType);
                    }
                    if (!current.generate(events, sink)) {
                        return false;
                    }
                    current = null;
                }
            }
        };
    }
}
