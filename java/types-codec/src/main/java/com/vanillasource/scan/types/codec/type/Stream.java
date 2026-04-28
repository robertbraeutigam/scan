package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.StreamDecoder;
import com.vanillasource.scan.types.codec.encoder.StreamEncoder;

/**
 * Open-ended sequence of items of a given {@link Type}. Per TYPES.md
 * "Per-Type Encoding" §"Stream(T)": no count and no terminator on the wire,
 * runs until the enclosing transport ends. Bit state resets at the stream's
 * byte boundary (entry).
 *
 * <p>The codec consequently never reports completion: its
 * {@code parse} / {@code generate} methods always return {@code false} (they
 * do still consume bytes / events and emit events / write bytes whenever they
 * can make progress). The orchestrator declares the stream finished when its
 * transport closes.
 */
public final class Stream implements Type {
    private final Type itemType;

    public Stream(Type itemType) {
        this.itemType = itemType;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new StreamDecoder(itemType);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new StreamEncoder(itemType);
    }
}
