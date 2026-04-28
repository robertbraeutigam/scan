package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueEncoder;

/**
 * Composes the array's phases via {@link ValueEncoder#andThen}: consume
 * {@code StartContainer(ARRAY, count)} and capture {@code count} into a holder, write the
 * count via a delegated {@link VariableLengthIntegerEncoder} fed from a synthetic
 * single-event source, then iterate {@code count} items (each consuming
 * {@code StartItem}/{@code EndItem} around a fresh encoder from the given {@link Type}),
 * finally consume {@code EndContainer(ARRAY)}.
 */
public final class ArrayEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public ArrayEncoder(int countMaxBytes, Type itemType) {
        long[] countHolder = new long[1];
        pipeline = captureStartContainer(countHolder)
                .andThen(writeCount(countMaxBytes, countHolder))
                .andThen(items(countHolder, itemType))
                .andThen(consumeOne());
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

    private static ValueEncoder captureStartContainer(long[] target) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            target[0] = ((Event.StartContainer) events.read()).count();
            return true;
        };
    }

    private static ValueEncoder writeCount(int maxBytes, long[] countHolder) {
        return new ValueEncoder() {
            private ValueEncoder vli;
            private EventSource fixedSource;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                if (vli == null) {
                    vli = new VariableLengthIntegerEncoder(maxBytes);
                    long count = countHolder[0];
                    fixedSource = singletonSource(
                            new Event.IntegerScalar(count, count == 0 ? 0 : 1));
                }
                return vli.generate(fixedSource, sink);
            }
        };
    }

    private static EventSource singletonSource(Event event) {
        return new EventSource() {
            private int remaining = 1;

            @Override
            public int availableEvents() {
                return remaining;
            }

            @Override
            public Event read() {
                if (remaining == 0) {
                    throw new IllegalStateException("singleton source exhausted");
                }
                remaining--;
                return event;
            }
        };
    }

    private static ValueEncoder oneItem(Type itemType) {
        return consumeOne()
                .andThen(itemType.createEncoder())
                .andThen(consumeOne());
    }

    private static ValueEncoder items(long[] countHolder, Type itemType) {
        return new ValueEncoder() {
            private long remaining = -1;
            private ValueEncoder current;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                if (remaining < 0) {
                    remaining = countHolder[0];
                }
                while (remaining > 0) {
                    if (current == null) {
                        current = oneItem(itemType);
                    }
                    if (!current.generate(events, sink)) {
                        return false;
                    }
                    current = null;
                    remaining--;
                }
                return true;
            }
        };
    }
}
