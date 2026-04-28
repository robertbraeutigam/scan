package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.ByteStreamDecoder;
import com.vanillasource.scan.types.codec.encoder.ByteStreamEncoder;

/**
 * Open-ended counterpart of {@link ByteArray}: a potentially infinite run of
 * raw bytes. No count and no terminator on the wire — chunks continue until
 * the enclosing transport ends. Decoder emits {@code StartContainer(BYTE_STREAM)}
 * once and then streams {@code Chunk(bytes)} events; no {@code EndContainer}
 * is ever emitted.
 */
public final class ByteStream implements Type {
    public ByteStream() {
    }

    @Override
    public ValueDecoder createDecoder() {
        return new ByteStreamDecoder();
    }

    @Override
    public ValueEncoder createEncoder() {
        return new ByteStreamEncoder();
    }
}
