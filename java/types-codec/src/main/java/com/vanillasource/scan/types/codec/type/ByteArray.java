package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.ByteArrayDecoder;
import com.vanillasource.scan.types.codec.encoder.ByteArrayEncoder;

/**
 * Finite, ordered run of raw bytes. Wire form per TYPES.md "ByteArray(size = C)"
 * coincides with {@code Array(UnsignedInteger(1), size = C)}: when {@code min ==
 * max}, no count is written; otherwise a {@code VariableLengthInteger(v)}
 * carries {@code count - min} where {@code v} is the smallest size in
 * {@code 1..8} whose VLI capacity covers {@code max - min}. Decoding emits
 * {@code Chunk(bytes)} events instead of per-byte item events, which is the
 * point of the dedicated type.
 */
public final class ByteArray implements Type {
    private final int min;
    private final int countVarintBytes;

    public ByteArray(int min, int max) {
        if (min < 0) {
            throw new IllegalArgumentException("min must be non-negative: " + min);
        }
        if (max < min) {
            throw new IllegalArgumentException("max " + max + " is less than min " + min);
        }
        this.min = min;
        this.countVarintBytes = computeCountVarintBytes(min, max);
    }

    @Override
    public ValueDecoder createDecoder() {
        return new ByteArrayDecoder(min, countVarintBytes);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new ByteArrayEncoder(min, countVarintBytes);
    }

    private static int computeCountVarintBytes(int min, int max) {
        long range = (long) max - min;
        if (range == 0) {
            return 0;
        }
        int bitsNeeded = 64 - Long.numberOfLeadingZeros(range);
        for (int v = 1; v <= 8; v++) {
            int cap = 7 * (v - 1) + 8;
            if (cap >= bitsNeeded) {
                return v;
            }
        }
        throw new IllegalStateException("range too large for VarInt(8): " + range);
    }
}
