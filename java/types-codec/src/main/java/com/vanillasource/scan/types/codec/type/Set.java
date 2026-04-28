package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.SetDecoder;
import com.vanillasource.scan.types.codec.encoder.SetEncoder;

/**
 * Bitmask-encoded set over an element type whose constructors are all bare
 * identifiers (a fixed value space of {@code memberCount} elements). Encoded
 * as a {@code memberCount}-bit run in the bit stream (MSB-first), bit
 * {@code i} = 1 iff constructor {@code i} is a member. No length prefix and
 * no padding to a byte boundary — the run participates in the surrounding
 * bit state per TYPES.md §"Set(T)".
 *
 * <p>This is the only encoding the type system defines for sets — collections
 * of structured (parameterized) values are expressed as {@link Array} instead.
 */
public final class Set implements Type {
    private final int memberCount;

    public Set(int memberCount) {
        if (memberCount <= 0) {
            throw new IllegalArgumentException("memberCount must be positive: " + memberCount);
        }
        this.memberCount = memberCount;
    }

    public int memberCount() {
        return memberCount;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new SetDecoder(memberCount);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new SetEncoder(memberCount);
    }
}
