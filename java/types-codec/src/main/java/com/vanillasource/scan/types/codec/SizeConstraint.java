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
    }

    record All() implements SizeConstraint {
        @Override
        public boolean isFixed() {
            return false;
        }
    }

    static SizeConstraint exact(int size) {
        return new Range(size, size);
    }
}
