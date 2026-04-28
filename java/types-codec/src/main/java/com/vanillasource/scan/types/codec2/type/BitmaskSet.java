package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.BitmaskSetDecoder;
import com.vanillasource.scan.types.codec2.encoder.BitmaskSetEncoder;

/**
 * Bitmask-form Set: for an element type whose constructors are all bare
 * identifiers (a fixed value space of {@code memberCount} elements). Encoded
 * as {@code ceil(memberCount/8)} bytes; bit {@code i} (MSB-first within byte
 * {@code i / 8}) is 1 iff constructor {@code i} is a member. No length prefix.
 *
 * <p>The codec produces the same {@code StartContainer(SET, count)} /
 * {@code StartItem} / {@code Constructor(i)} / {@code EndItem} /
 * {@code EndContainer(SET)} event sequence as {@link Set}, so a downstream
 * consumer cannot tell the two encodings apart at the event level.
 */
public final class BitmaskSet implements Type {
    private final int memberCount;

    public BitmaskSet(int memberCount) {
        if (memberCount <= 0) {
            throw new IllegalArgumentException("memberCount must be positive: " + memberCount);
        }
        this.memberCount = memberCount;
    }

    public int memberCount() {
        return memberCount;
    }

    public int byteSize() {
        return (memberCount + 7) / 8;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new BitmaskSetDecoder(memberCount);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new BitmaskSetEncoder(memberCount);
    }
}
