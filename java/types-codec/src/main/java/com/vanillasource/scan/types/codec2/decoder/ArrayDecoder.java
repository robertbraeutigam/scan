package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitSource;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.ContainerKind;
import com.vanillasource.scan.types.codec2.EventSink;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/**
 * Composes the array's phases via {@link ValueDecoder#andThen}: read (or skip)
 * the count, emit {@code StartContainer(ARRAY, count)}, iterate {@code count}
 * items (each wrapped in {@code StartItem}/{@code EndItem} around a fresh
 * decoder from the given {@link Type}), then emit {@code EndContainer(ARRAY)}.
 *
 * <p>{@code countVarintBytes == 0} means no count is on the wire — the count
 * is fixed at {@code min}. Otherwise the wire carries
 * {@code VariableLengthInteger(countVarintBytes)} and {@code min} is added
 * back to its value to recover the actual count.
 */
public final class ArrayDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public ArrayDecoder(int min, int countVarintBytes, Type itemType) {
        long[] countHolder = new long[1];
        pipeline = captureCount(min, countVarintBytes, countHolder)
                .andThen(emitStartContainer(countHolder))
                .andThen(items(countHolder, itemType))
                .andThen(emit(new Event.EndContainer(ContainerKind.ARRAY)));
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
            sink.put(event);
            return true;
        };
    }

    private static ValueDecoder emitStartContainer(long[] countHolder) {
        return (bits, sink) -> {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.StartContainer(ContainerKind.ARRAY, countHolder[0]));
            return true;
        };
    }

    private static ValueDecoder captureCount(int min, int countVarintBytes, long[] target) {
        if (countVarintBytes == 0) {
            return (bits, sink) -> {
                target[0] = min;
                return true;
            };
        }
        ValueDecoder vli = new VariableLengthIntegerDecoder(countVarintBytes);
        EventSink capture = new EventSink() {
            @Override
            public int writableEvents() {
                return Integer.MAX_VALUE;
            }

            @Override
            public void put(Event event) {
                if (event instanceof Event.IntegerScalar is) {
                    target[0] = is.value() + min;
                }
            }
        };
        return (bits, sink) -> vli.parse(bits, capture);
    }

    private static ValueDecoder oneItem(Type itemType) {
        return emit(new Event.StartItem())
                .andThen(itemType.createDecoder())
                .andThen(emit(new Event.EndItem()));
    }

    private static ValueDecoder items(long[] countHolder, Type itemType) {
        return new ValueDecoder() {
            private long remaining = -1;
            private ValueDecoder current;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                if (remaining < 0) {
                    remaining = countHolder[0];
                }
                while (remaining > 0) {
                    if (current == null) {
                        current = oneItem(itemType);
                    }
                    if (!current.parse(bits, sink)) {
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
