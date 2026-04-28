package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.SetDecoder;
import com.vanillasource.scan.types.codec2.encoder.SetEncoder;

/**
 * Bitmask-encoded set over an element type whose constructors are all bare
 * identifiers (a fixed value space of {@code memberCount} elements). Encoded
 * as {@code ceil(memberCount/8)} bytes; bit {@code i} (MSB-first within byte
 * {@code i / 8}) is 1 iff constructor {@code i} is a member. No length prefix.
 *
 * <p>Per TYPES.md "Per-Type Encoding" §"Set(T)", this is the only encoding the
 * type system defines for sets — collections of structured (parameterized) values
 * are expressed as {@link Array} instead.
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

    public int byteSize() {
        return (memberCount + 7) / 8;
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
