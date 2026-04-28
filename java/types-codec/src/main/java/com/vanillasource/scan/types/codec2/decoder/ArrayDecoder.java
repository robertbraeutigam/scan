package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEvent.ContainerKind;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/**
 * Composes the array's phases via {@link ValueDecoder#andThen}: emit
 * {@code StartContainer(ARRAY)}, read the count via a delegated
 * {@link VariableLengthIntegerDecoder} (count value not propagated as an event),
 * iterate {@code count} items (each wrapped in {@code StartItem}/{@code EndItem}
 * around a fresh decoder from the given {@link Type}), then emit
 * {@code EndContainer(ARRAY)}.
 */
public final class ArrayDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public ArrayDecoder(int countMaxBytes, Type itemType) {
        long[] countHolder = new long[1];
        pipeline = emit(new DecodingEvent.StartContainer(ContainerKind.ARRAY))
                .andThen(captureCount(countMaxBytes, countHolder))
                .andThen(items(countHolder, itemType))
                .andThen(emit(new DecodingEvent.EndContainer(ContainerKind.ARRAY)));
    }

    @Override
    public boolean parse(BitReader bits, DecodingEventHandler handler) {
        return pipeline.parse(bits, handler);
    }

    private static ValueDecoder emit(DecodingEvent event) {
        return (bits, handler) -> {
            handler.onEvent(event);
            return true;
        };
    }

    private static ValueDecoder captureCount(int maxBytes, long[] target) {
        ValueDecoder vli = new VariableLengthIntegerDecoder(maxBytes);
        DecodingEventHandler capture = e -> {
            if (e instanceof DecodingEvent.IntegerScalar is) {
                target[0] = is.value();
            }
        };
        return (bits, handler) -> vli.parse(bits, capture);
    }

    private static ValueDecoder oneItem(Type itemType) {
        return emit(new DecodingEvent.StartItem())
                .andThen(itemType.createDecoder())
                .andThen(emit(new DecodingEvent.EndItem()));
    }

    private static ValueDecoder items(long[] countHolder, Type itemType) {
        return new ValueDecoder() {
            private long remaining = -1;
            private ValueDecoder current;

            @Override
            public boolean parse(BitReader bits, DecodingEventHandler handler) {
                if (remaining < 0) {
                    remaining = countHolder[0];
                }
                while (remaining > 0) {
                    if (current == null) {
                        current = oneItem(itemType);
                    }
                    if (!current.parse(bits, handler)) {
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
