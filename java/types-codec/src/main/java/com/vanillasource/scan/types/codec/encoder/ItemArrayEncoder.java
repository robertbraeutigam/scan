package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Per-item array encode path: the {@code Array}-with-non-byte-primitive-element
 * branch of {@link com.vanillasource.scan.types.codec.type.Array}'s dispatch.
 * Composes the array's phases via {@link ValueEncoder#andThen}: consume
 * {@code StartContainer(ARRAY, count)} (validating the count against
 * {@code min} and the fixed-length contract), write the count (omitted when
 * {@code countVarintBytes == 0}, otherwise {@code count - min} as a
 * {@code VariableLengthInteger(countVarintBytes)}), iterate {@code count}
 * items (each consuming {@code StartItem}/{@code EndItem} around a fresh
 * encoder from the given {@link Type}), finally consume
 * {@code EndContainer(ARRAY)}.
 */
public final class ItemArrayEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public ItemArrayEncoder(int min, int countVarintBytes, Type itemType) {
        long[] countHolder = new long[1];
        pipeline = captureStartContainer(countHolder, min, countVarintBytes)
                .andThen(writeCount(min, countVarintBytes, countHolder))
                .andThen(items(countHolder, itemType))
                .andThen(ValueEncoder.skipEvent());
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder captureStartContainer(long[] target, int min, int countVarintBytes) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            long count = ((Event.StartContainer) events.read()).count();
            if (count < min) {
                throw new IllegalArgumentException(
                        "count " + count + " is below min " + min);
            }
            if (countVarintBytes == 0 && count != min) {
                throw new IllegalArgumentException(
                        "fixed-length array requires count == " + min + ", got " + count);
            }
            target[0] = count;
            return true;
        };
    }

    private static ValueEncoder writeCount(int min, int countVarintBytes, long[] countHolder) {
        if (countVarintBytes == 0) {
            return (events, sink) -> true;
        }
        return new ValueEncoder() {
            private ValueEncoder vli;
            private EventSource fixedSource;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                if (vli == null) {
                    vli = new VariableLengthIntegerEncoder(countVarintBytes);
                    long delta = countHolder[0] - min;
                    fixedSource = singletonSource(
                            new Event.IntegerScalar(delta, delta == 0 ? 0 : 1));
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
                        current = itemType.createEncoder().bracketed();
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
