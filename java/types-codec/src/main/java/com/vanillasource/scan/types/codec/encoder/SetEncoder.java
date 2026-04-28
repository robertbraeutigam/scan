package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Consumes the {@code StartContainer(SET, count)} /
 * {@code StartItem}/{@code Constructor(i)}/{@code EndItem} ... /
 * {@code EndContainer(SET)} event sequence, accumulates the constructor
 * indices into a {@code memberCount}-bit bitmask, then writes those bits into
 * the bit stream MSB-first per TYPES.md §"Set(T)".
 */
public final class SetEncoder implements ValueEncoder {
    private final int memberCount;
    private final byte[] bitmask;
    private int phase = PHASE_CONSUME_START;
    private int itemsRemaining = -1;
    private int itemSubPhase = 0; // 0=StartItem, 1=Constructor, 2=EndItem
    private int bitsWritten = 0;

    private static final int PHASE_CONSUME_START = 0;
    private static final int PHASE_CONSUME_ITEMS = 1;
    private static final int PHASE_CONSUME_END = 2;
    private static final int PHASE_WRITE_BITS = 3;
    private static final int PHASE_DONE = 4;

    public SetEncoder(int memberCount) {
        this.memberCount = memberCount;
        this.bitmask = new byte[(memberCount + 7) / 8];
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (phase == PHASE_CONSUME_START) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            Event.StartContainer sc = (Event.StartContainer) events.read();
            itemsRemaining = (int) sc.count();
            phase = PHASE_CONSUME_ITEMS;
        }
        while (phase == PHASE_CONSUME_ITEMS) {
            if (itemsRemaining == 0) {
                phase = PHASE_CONSUME_END;
                break;
            }
            if (itemSubPhase == 0) {
                if (events.availableEvents() <= 0) return false;
                events.read(); // StartItem
                itemSubPhase = 1;
            }
            if (itemSubPhase == 1) {
                if (events.availableEvents() <= 0) return false;
                int index = ((Event.Constructor) events.read()).index();
                if (index < 0 || index >= memberCount) {
                    throw new IllegalArgumentException(
                            "constructor index " + index + " out of range for "
                                    + memberCount + "-member set");
                }
                setBit(bitmask, index);
                itemSubPhase = 2;
            }
            if (itemSubPhase == 2) {
                if (events.availableEvents() <= 0) return false;
                events.read(); // EndItem
                itemSubPhase = 0;
                itemsRemaining--;
            }
        }
        if (phase == PHASE_CONSUME_END) {
            if (events.availableEvents() <= 0) return false;
            events.read(); // EndContainer
            phase = PHASE_WRITE_BITS;
        }
        if (phase == PHASE_WRITE_BITS) {
            while (bitsWritten < memberCount && sink.writableBits() > 0) {
                int byteIndex = bitsWritten / 8;
                int bitInByte = 7 - (bitsWritten % 8);
                int bit = (bitmask[byteIndex] >>> bitInByte) & 1;
                sink.writeBits(bit, 1);
                bitsWritten++;
            }
            if (bitsWritten < memberCount) {
                return false;
            }
            phase = PHASE_DONE;
        }
        return phase == PHASE_DONE;
    }

    private static void setBit(byte[] mask, int i) {
        int byteIndex = i / 8;
        int bitInByte = 7 - (i % 8); // MSB-first
        mask[byteIndex] |= (byte) (1 << bitInByte);
    }
}
