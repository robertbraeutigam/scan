package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.decoder.ArrayDecoder;
import com.vanillasource.scan.types.codec.encoder.ArrayEncoder;

/**
 * Ordered aggregation of items of a given {@link Type} whose count lies in
 * {@code [min, max]} per TYPES.md "Per-Type Encoding" §"Array(T, size = C)":
 *
 * <ul>
 *   <li>If {@code min == max} (the constraint admits a single length), no
 *       count is written on the wire — the decoder reads exactly that many
 *       items straight from the type.</li>
 *   <li>Otherwise the count is written first as
 *       {@code VariableLengthInteger(v)}, where {@code v} is the smallest size
 *       in {@code 1..8} whose VLI capacity covers {@code max - min}. The
 *       value written is {@code count - min}; the decoder adds {@code min}
 *       back to recover the actual count.</li>
 * </ul>
 *
 * <p>The codec receives {@code min} and the precomputed VLI byte count
 * ({@code 0} when no count is written, otherwise the {@code v} above), so it
 * never has to consult {@code max} or recompute the sizing.
 */
public final class Array implements Type {
    private final int min;
    private final int countVarintBytes;
    private final Type itemType;

    public Array(int min, int max, Type itemType) {
        if (min < 0) {
            throw new IllegalArgumentException("min must be non-negative: " + min);
        }
        if (max < min) {
            throw new IllegalArgumentException("max " + max + " is less than min " + min);
        }
        this.min = min;
        this.countVarintBytes = computeCountVarintBytes(min, max);
        this.itemType = itemType;
    }

    @Override
    public ValueDecoder createDecoder() {
        return new ArrayDecoder(min, countVarintBytes, itemType);
    }

    @Override
    public ValueEncoder createEncoder() {
        return new ArrayEncoder(min, countVarintBytes, itemType);
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
