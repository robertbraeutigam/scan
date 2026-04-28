package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEvent.ContainerKind;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/**
 * Emits {@code StartContainer(ARRAY)}, reads the count via a delegated
 * {@link VariableLengthIntegerDecoder}, then for each item emits {@code StartItem},
 * runs a fresh decoder from the given item {@link Type}, and emits {@code EndItem};
 * finally emits {@code EndContainer(ARRAY)}. The count value itself is not emitted.
 */
public final class ArrayDecoder implements ValueDecoder {
    private final Type itemType;
    private final ValueDecoder countDecoder;
    private final CountCapture countCapture = new CountCapture();
    private boolean started;
    private boolean countDone;
    private long remaining;
    private ValueDecoder currentItem;

    public ArrayDecoder(int countMaxBytes, Type itemType) {
        this.itemType = itemType;
        this.countDecoder = new VariableLengthIntegerDecoder(countMaxBytes);
    }

    @Override
    public boolean parse(BitReader bits, DecodingEventHandler handler) {
        if (!started) {
            handler.onEvent(new DecodingEvent.StartContainer(ContainerKind.ARRAY));
            started = true;
        }
        if (!countDone) {
            if (!countDecoder.parse(bits, countCapture)) {
                return false;
            }
            remaining = countCapture.value;
            countDone = true;
        }
        while (remaining > 0) {
            if (currentItem == null) {
                handler.onEvent(new DecodingEvent.StartItem());
                currentItem = itemType.createDecoder();
            }
            if (!currentItem.parse(bits, handler)) {
                return false;
            }
            handler.onEvent(new DecodingEvent.EndItem());
            currentItem = null;
            remaining--;
        }
        handler.onEvent(new DecodingEvent.EndContainer(ContainerKind.ARRAY));
        return true;
    }

    private static final class CountCapture implements DecodingEventHandler {
        long value;

        @Override
        public void onEvent(DecodingEvent event) {
            if (event instanceof DecodingEvent.IntegerScalar is) {
                value = is.value();
            }
        }
    }
}
