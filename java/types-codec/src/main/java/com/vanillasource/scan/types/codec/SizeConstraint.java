package com.vanillasource.scan.types.codec;

/**
 * Element-count constraint for {@link Type.Array} and {@link Type.Set}. Determines
 * whether a count is written on the wire and how many bytes it occupies — see
 * TYPES.md §"Per-Type Encoding" / Array.
 */
public sealed interface SizeConstraint {

    /**
     * True when the constraint admits exactly one length — the encoder omits the
     * count and the decoder reads the fixed number of items straight from the type.
     */
    boolean isFixed();

    /**
     * Smallest count this constraint admits. The wire encodes {@code count - lowerBound()}
     * as a {@link Type.VariableLengthInteger} of {@link #countVarintSize()} bytes.
     */
    int lowerBound();

    /**
     * {@code maxBytes} of the {@link Type.VariableLengthInteger} used for the
     * on-wire count. Sized to fit every admitted length per TYPES.md
     * §"Per-Type Encoding" / Array. Not meaningful for {@link #isFixed()} constraints
     * (no count is written), but defined for both variants for uniformity.
     */
    int countVarintSize();

    record Range(int min, int max) implements SizeConstraint {
        public Range {
            if (min < 0) {
                throw new IllegalArgumentException("min must be non-negative: " + min);
            }
            if (max < min) {
                throw new IllegalArgumentException("max " + max + " is less than min " + min);
            }
        }

        @Override
        public boolean isFixed() {
            return min == max;
        }

        @Override
        public int lowerBound() {
            return min;
        }

        @Override
        public int countVarintSize() {
            long range = (long) max - min;
            int bitsNeeded = (range == 0) ? 1 : (64 - Long.numberOfLeadingZeros(range));
            for (int v = 1; v <= 8; v++) {
                int cap = 7 * (v - 1) + 8;
                if (cap >= bitsNeeded) {
                    return v;
                }
            }
            throw new IllegalStateException("size range too large for VarInt(8): " + range);
        }
    }

    record All() implements SizeConstraint {
        @Override
        public boolean isFixed() {
            return false;
        }

        @Override
        public int lowerBound() {
            return 0;
        }

        @Override
        public int countVarintSize() {
            return 8;
        }
    }

    static SizeConstraint exact(int size) {
        return new Range(size, size);
    }
}
