package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.ValueEncoder;

/**
 * Consumes the {@code StartContainer(SET, count)} /
 * {@code StartItem}/{@code Constructor(i)}/{@code EndItem} ... /
 * {@code EndContainer(SET)} event sequence, accumulates the constructor
 * indices into a bitmask of {@code ceil(memberCount/8)} bytes (MSB-first
 * within each byte), and writes the bitmask out at the end.
 */
public final class BitmaskSetEncoder implements ValueEncoder {
    private final int memberCount;
    private final int byteSize;
    private final byte[] bitmask;
    private int phase = PHASE_CONSUME_START;
    private int itemsRemaining = -1;
    private int itemSubPhase = 0; // 0=StartItem, 1=Constructor, 2=EndItem
    private int bytesWritten = 0;

    private static final int PHASE_CONSUME_START = 0;
    private static final int PHASE_CONSUME_ITEMS = 1;
    private static final int PHASE_CONSUME_END = 2;
    private static final int PHASE_WRITE_BYTES = 3;
    private static final int PHASE_DONE = 4;

    public BitmaskSetEncoder(int memberCount) {
        this.memberCount = memberCount;
        this.byteSize = (memberCount + 7) / 8;
        this.bitmask = new byte[byteSize];
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        sink.closeBits();
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
            phase = PHASE_WRITE_BYTES;
        }
        if (phase == PHASE_WRITE_BYTES) {
            while (bytesWritten < byteSize && sink.writableBytes() > 0) {
                sink.writeUnsignedByte(bitmask[bytesWritten] & 0xFF);
                bytesWritten++;
            }
            if (bytesWritten < byteSize) {
                return false;
            }
            sink.closeBits();
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
