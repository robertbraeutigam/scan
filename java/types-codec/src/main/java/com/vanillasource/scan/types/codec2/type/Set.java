package com.vanillasource.scan.types.codec2.type;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.decoder.SetDecoder;
import com.vanillasource.scan.types.codec2.encoder.SetEncoder;

/**
 * Sequence-form Set: an unordered, deduplicated collection encoded identically
 * to {@link Array} except the wire/event marker is {@code SET}. Uniqueness of
 * elements is the producer's responsibility — the codec does not enforce it.
 *
 * <p>For element types whose constructors are all bare-identifier (so the
 * value space is fixed and finite), use {@link BitmaskSet} instead — it
 * encodes as a fixed-size bitmask without a length prefix.
 */
public final class Set implements Type {
    private final int countMaxBytes;
    private final Type itemType;

    public Set(int countMaxBytes, Type itemType) {
        this.countMaxBytes = countMaxBytes;
        this.itemType = itemType;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new SetDecoder(countMaxBytes, itemType);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new SetEncoder(countMaxBytes, itemType);
    }
}
