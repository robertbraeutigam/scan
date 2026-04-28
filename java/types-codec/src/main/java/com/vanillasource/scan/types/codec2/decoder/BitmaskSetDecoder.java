package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitSource;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.ContainerKind;
import com.vanillasource.scan.types.codec2.EventSink;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/**
 * Reads {@code ceil(memberCount/8)} bytes of bitmask, then emits
 * {@code StartContainer(SET, popcount)} followed by
 * {@code StartItem}/{@code Constructor(i)}/{@code EndItem} for each set bit
 * (in declaration order, MSB-first within each byte) and finally
 * {@code EndContainer(SET)}.
 */
public final class BitmaskSetDecoder implements ValueDecoder {
    private final int memberCount;
    private final int byteSize;
    private final byte[] bitmask;
    private int bytesRead = 0;
    private int popCount = -1;
    private int emittedItems = 0;
    private int currentBit = 0;
    private int phase = PHASE_READ_BYTES;

    private static final int PHASE_READ_BYTES = 0;
    private static final int PHASE_EMIT_START = 1;
    private static final int PHASE_EMIT_ITEMS = 2;
    private static final int PHASE_EMIT_END = 3;
    private static final int PHASE_DONE = 4;

    private int itemSubPhase = 0; // 0=StartItem, 1=Constructor, 2=EndItem

    public BitmaskSetDecoder(int memberCount) {
        this.memberCount = memberCount;
        this.byteSize = (memberCount + 7) / 8;
        this.bitmask = new byte[byteSize];
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        bits.closeBits();
        if (phase == PHASE_READ_BYTES) {
            while (bytesRead < byteSize && bits.availableBytes() > 0) {
                bitmask[bytesRead++] = (byte) bits.readUnsignedByte();
            }
            if (bytesRead < byteSize) {
                return false;
            }
            popCount = countBits(bitmask, memberCount);
            phase = PHASE_EMIT_START;
        }
        if (phase == PHASE_EMIT_START) {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.StartContainer(ContainerKind.SET, popCount));
            phase = PHASE_EMIT_ITEMS;
        }
        while (phase == PHASE_EMIT_ITEMS) {
            if (emittedItems >= popCount) {
                phase = PHASE_EMIT_END;
                break;
            }
            // Find next set bit starting from currentBit
            while (currentBit < memberCount && !isBitSet(bitmask, currentBit)) {
                currentBit++;
            }
            if (currentBit >= memberCount) {
                phase = PHASE_EMIT_END;
                break;
            }
            // Emit StartItem, Constructor, EndItem for currentBit
            if (itemSubPhase == 0) {
                if (sink.writableEvents() <= 0) return false;
                sink.put(new Event.StartItem());
                itemSubPhase = 1;
            }
            if (itemSubPhase == 1) {
                if (sink.writableEvents() <= 0) return false;
                sink.put(new Event.Constructor(currentBit));
                itemSubPhase = 2;
            }
            if (itemSubPhase == 2) {
                if (sink.writableEvents() <= 0) return false;
                sink.put(new Event.EndItem());
                itemSubPhase = 0;
                emittedItems++;
                currentBit++;
            }
        }
        if (phase == PHASE_EMIT_END) {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.EndContainer(ContainerKind.SET));
            bits.closeBits();
            phase = PHASE_DONE;
        }
        return phase == PHASE_DONE;
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
