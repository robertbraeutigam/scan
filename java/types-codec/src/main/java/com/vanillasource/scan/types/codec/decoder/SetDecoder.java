package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Composes the set's phases via {@link ValueDecoder#andThen}: read
 * {@code memberCount} bits (MSB-first) from the bit stream per TYPES.md
 * §"Set(T)" into a bitmask, then emit {@code StartContainer(SET, popcount)}
 * followed by {@code StartItem}/{@code Constructor(i)}/{@code EndItem} for
 * each set bit (in declaration order) and finally {@code EndContainer(SET)}.
 */
public final class SetDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public SetDecoder(int memberCount) {
        byte[] bitmask = new byte[(memberCount + 7) / 8];
        int[] popCountHolder = new int[1];
        pipeline = readBits(memberCount, bitmask, popCountHolder)
                .andThen(ValueDecoder.writeEvent(() -> new Event.StartContainer(ContainerKind.SET, popCountHolder[0])))
                .andThen(emitItems(bitmask, memberCount))
                .andThen(ValueDecoder.writeEvent(new Event.EndContainer(ContainerKind.SET)));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder readBits(int memberCount, byte[] bitmask, int[] popCountHolder) {
        return new ValueDecoder() {
            private int bitsRead = 0;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                while (bitsRead < memberCount && bits.availableBits() > 0) {
                    int bit = bits.readBits(1);
                    if (bit == 1) {
                        int byteIndex = bitsRead / 8;
                        int bitInByte = 7 - (bitsRead % 8);
                        bitmask[byteIndex] |= (byte) (1 << bitInByte);
                    }
                    bitsRead++;
                }
                if (bitsRead < memberCount) {
                    return false;
                }
                popCountHolder[0] = countBits(bitmask, memberCount);
                return true;
            }
        };
    }

    private static ValueDecoder oneItem(int index) {
        return ValueDecoder.writeEvent(new Event.Constructor(index))
                .between(new Event.StartItem(), new Event.EndItem());
    }

    private static ValueDecoder emitItems(byte[] bitmask, int memberCount) {
        return new ValueDecoder() {
            private int currentBit = 0;
            private ValueDecoder current;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                while (true) {
                    if (current == null) {
                        while (currentBit < memberCount && !isBitSet(bitmask, currentBit)) {
                            currentBit++;
                        }
                        if (currentBit >= memberCount) {
                            return true;
                        }
                        current = oneItem(currentBit);
                    }
                    if (!current.parse(bits, sink)) {
                        return false;
                    }
                    current = null;
                    currentBit++;
                }
            }
        };
    }

    private static int countBits(byte[] mask, int memberCount) {
        int count = 0;
        for (int i = 0; i < memberCount; i++) {
            if (isBitSet(mask, i)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isBitSet(byte[] mask, int i) {
        int byteIndex = i / 8;
        int bitInByte = 7 - (i % 8); // MSB-first
        return ((mask[byteIndex] >>> bitInByte) & 1) != 0;
    }
}
