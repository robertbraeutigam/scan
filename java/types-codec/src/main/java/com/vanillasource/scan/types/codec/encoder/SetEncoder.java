package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Composes the set's phases via {@link ValueEncoder#andThen}: consume
 * {@code StartContainer(SET, count)}, then {@code count} items
 * ({@code StartItem}/{@code Constructor(i)}/{@code EndItem}) accumulating the
 * indices into a {@code memberCount}-bit bitmask, then consume
 * {@code EndContainer(SET)}, finally write the bitmask MSB-first per
 * TYPES.md §"Set(T)".
 */
public final class SetEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public SetEncoder(int memberCount) {
        byte[] bitmask = new byte[(memberCount + 7) / 8];
        int[] itemsRemainingHolder = new int[]{-1};
        pipeline = consumeStartContainer(itemsRemainingHolder)
                .andThen(consumeItems(itemsRemainingHolder, bitmask, memberCount))
                .andThen(consumeOne())
                .andThen(writeBitmask(bitmask, memberCount));
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

    private static ValueEncoder consumeStartContainer(int[] itemsRemainingHolder) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            Event.StartContainer sc = (Event.StartContainer) events.read();
            itemsRemainingHolder[0] = (int) sc.count();
            return true;
        };
    }

    private static ValueEncoder consumeConstructor(byte[] bitmask, int memberCount) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            int index = ((Event.Constructor) events.read()).index();
            if (index < 0 || index >= memberCount) {
                throw new IllegalArgumentException(
                        "constructor index " + index + " out of range for "
                                + memberCount + "-member set");
            }
            setBit(bitmask, index);
            return true;
        };
    }

    private static ValueEncoder oneItem(byte[] bitmask, int memberCount) {
        return consumeOne()
                .andThen(consumeConstructor(bitmask, memberCount))
                .andThen(consumeOne());
    }

    private static ValueEncoder consumeItems(int[] itemsRemainingHolder, byte[] bitmask, int memberCount) {
        return new ValueEncoder() {
            private ValueEncoder current;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                while (itemsRemainingHolder[0] > 0) {
                    if (current == null) {
                        current = oneItem(bitmask, memberCount);
                    }
                    if (!current.generate(events, sink)) {
                        return false;
                    }
                    current = null;
                    itemsRemainingHolder[0]--;
                }
                return true;
            }
        };
    }

    private static ValueEncoder writeBitmask(byte[] bitmask, int memberCount) {
        return new ValueEncoder() {
            private int bitsWritten = 0;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                while (bitsWritten < memberCount && sink.writableBits() > 0) {
                    int byteIndex = bitsWritten / 8;
                    int bitInByte = 7 - (bitsWritten % 8);
                    int bit = (bitmask[byteIndex] >>> bitInByte) & 1;
                    sink.writeBits(bit, 1);
                    bitsWritten++;
                }
                return bitsWritten == memberCount;
            }
        };
    }

    private static void setBit(byte[] mask, int i) {
        int byteIndex = i / 8;
        int bitInByte = 7 - (i % 8); // MSB-first
        mask[byteIndex] |= (byte) (1 << bitInByte);
    }
}
