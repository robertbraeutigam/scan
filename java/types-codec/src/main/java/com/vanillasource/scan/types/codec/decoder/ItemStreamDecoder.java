package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Per-item stream decode path: the {@code Stream}-with-non-byte-primitive-element
 * branch of {@link com.vanillasource.scan.types.codec.type.Stream}'s dispatch.
 * Composes the stream's phases via {@link ValueDecoder#andThen}: emit
 * {@code StartStream} once on entry, then iterate items
 * ({@code StartItem}, item events, {@code EndItem}) for as long as bytes are
 * available and the sink has room. The item-loop phase never completes — a
 * stream has no terminator on the wire, so completion is declared externally
 * by the transport.
 */
public final class ItemStreamDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public ItemStreamDecoder(Type itemType) {
        pipeline = ValueDecoder.writeEvent(new Event.StartStream()).andThen(itemLoop(itemType));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
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
                        current = itemType.createDecoder()
                                .between(new Event.StartItem(), new Event.EndItem());
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
