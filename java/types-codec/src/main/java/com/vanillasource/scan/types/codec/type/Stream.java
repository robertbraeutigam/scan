package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.ChunkStreamDecoder;
import com.vanillasource.scan.types.codec.decoder.ItemStreamDecoder;
import com.vanillasource.scan.types.codec.encoder.ChunkStreamEncoder;
import com.vanillasource.scan.types.codec.encoder.ItemStreamEncoder;

/**
 * Open-ended sequence of items of a given {@link Type}. Per TYPES.md
 * "Per-Type Encoding" §"Stream(T)": no count and no terminator on the wire,
 * runs until the enclosing transport ends.
 *
 * <p>The codec consequently never reports completion: its
 * {@code parse} / {@code generate} methods always return {@code false} (they
 * do still consume bytes / events and emit events / write bytes whenever they
 * can make progress). The orchestrator declares the stream finished when its
 * transport closes.
 *
 * <p>When the element type is a 1-byte primitive (per
 * {@link Type#isPrimitiveByte()}), payload bytes are delivered as
 * {@code Chunk} events instead of per-item {@code StartItem}/{@code EndItem}
 * pairs — see TYPES.md §"Incremental Consumption". The wire form is identical
 * either way.
 */
public final class Stream implements Type {
    private final Type itemType;

    public Stream(Type itemType) {
        this.itemType = itemType;
    }

    public Type itemType() {
        return itemType;
    }

    @Override
    public ValueDecoder createDecoder() {
        if (itemType.isPrimitiveByte()) {
            return new ChunkStreamDecoder();
        }
        return new ItemStreamDecoder(itemType);
    }

    @Override
    public ValueEncoder createEncoder() {
        if (itemType.isPrimitiveByte()) {
            return new ChunkStreamEncoder();
        }
        return new ItemStreamEncoder(itemType);
    }
}
