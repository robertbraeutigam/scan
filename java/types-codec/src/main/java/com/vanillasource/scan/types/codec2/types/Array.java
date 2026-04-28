package com.vanillasource.scan.types.codec2.types;

import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.decoder.ArrayDecoder;

/**
 * An array of items of a given {@link Type}, prefixed by a variable-length integer
 * count whose maximum byte length is configurable.
 */
public final class Array implements Type {
    private final int countMaxBytes;
    private final Type itemType;

    public Array(int countMaxBytes, Type itemType) {
        this.countMaxBytes = countMaxBytes;
        this.itemType = itemType;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new ArrayDecoder(countMaxBytes, itemType);
    }
}
