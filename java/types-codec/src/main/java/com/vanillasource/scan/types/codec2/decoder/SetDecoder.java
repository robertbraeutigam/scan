package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitSource;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.ContainerKind;
import com.vanillasource.scan.types.codec2.EventSink;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/**
 * Sequence-form Set decoder: structurally identical to
 * {@link ArrayDecoder} but emits {@code StartContainer(SET, count)} /
 * {@code EndContainer(SET)} markers instead of {@code ARRAY}.
 */
public final class SetDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public SetDecoder(int countMaxBytes, Type itemType) {
        long[] countHolder = new long[1];
        pipeline = closeBits()
                .andThen(captureCount(countMaxBytes, countHolder))
                .andThen(emitStartContainer(countHolder))
                .andThen(items(countHolder, itemType))
                .andThen(closeBits())
                .andThen(emit(new Event.EndContainer(ContainerKind.SET)));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder closeBits() {
        return (bits, sink) -> {
            bits.closeBits();
            return true;
        };
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
            sink.put(new Event.StartContainer(ContainerKind.SET, countHolder[0]));
            return true;
        };
    }

    private static ValueDecoder captureCount(int maxBytes, long[] target) {
        ValueDecoder vli = new VariableLengthIntegerDecoder(maxBytes);
        EventSink capture = new EventSink() {
            @Override
            public int writableEvents() {
                return Integer.MAX_VALUE;
            }

            @Override
            public void put(Event event) {
                if (event instanceof Event.IntegerScalar is) {
                    target[0] = is.value();
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
