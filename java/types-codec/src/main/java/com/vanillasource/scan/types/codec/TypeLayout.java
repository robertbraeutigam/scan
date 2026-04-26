package com.vanillasource.scan.types.codec;

import java.util.OptionalInt;

/**
 * Static layout queries on {@link Type} used by both {@link ValueEncoder} and
 * {@link ValueDecoder}. Package-private; not part of the codec public surface.
 */
final class TypeLayout {
    private TypeLayout() {
    }

    static int ceilLog2(int n) {
        if (n <= 1) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    static int pickCountVarintSize(SizeConstraint sc) {
        if (sc instanceof SizeConstraint.All) {
            return 8;
        }
        if (sc instanceof SizeConstraint.Range r) {
            long range = (long) r.max() - r.min();
            int bitsNeeded = (range == 0) ? 1 : (64 - Long.numberOfLeadingZeros(range));
            for (int v = 1; v <= 8; v++) {
                int cap = 7 * (v - 1) + 8;
                if (cap >= bitsNeeded) {
                    return v;
                }
            }
            throw new IllegalStateException("size range too large for VarInt(8): " + range);
        }
        throw new IllegalStateException("unknown size constraint: " + sc);
    }

    static int lowerBound(SizeConstraint sc) {
        if (sc instanceof SizeConstraint.All) {
            return 0;
        }
        if (sc instanceof SizeConstraint.Range r) {
            return r.min();
        }
        throw new IllegalStateException("unknown size constraint: " + sc);
    }

    static OptionalInt staticBitSize(Type t) {
        if (t instanceof Type.Unit) {
            return OptionalInt.of(0);
        }
        if (t instanceof Type.UnsignedInteger u) {
            return OptionalInt.of(u.byteSize() * 8);
        }
        if (t instanceof Type.SignedInteger s) {
            return OptionalInt.of(s.byteSize() * 8);
        }
        if (t instanceof Type.FloatingPoint f) {
            return OptionalInt.of(f.byteSize() * 8);
        }
        if (t instanceof Type.VariableLengthInteger) {
            return OptionalInt.empty();
        }
        if (t instanceof Type.Struct s) {
            int total = 0;
            for (Type.Field f : s.fields()) {
                OptionalInt sub = staticBitSize(f.type());
                if (sub.isEmpty()) {
                    return OptionalInt.empty();
                }
                total += sub.getAsInt();
            }
            return OptionalInt.of(total);
        }
        if (t instanceof Type.Union u) {
            int discBits = ceilLog2(u.constructors().size());
            Integer ctorSize = null;
            for (Type.Constructor c : u.constructors()) {
                int total = 0;
                for (Type.Field f : c.fields()) {
                    OptionalInt sub = staticBitSize(f.type());
                    if (sub.isEmpty()) {
                        return OptionalInt.empty();
                    }
                    total += sub.getAsInt();
                }
                if (ctorSize == null) {
                    ctorSize = total;
                } else if (ctorSize != total) {
                    return OptionalInt.empty();
                }
            }
            return OptionalInt.of(discBits + (ctorSize == null ? 0 : ctorSize));
        }
        return OptionalInt.empty();
    }

    /**
     * Iteration 5 supports only byte-aligned item layouts; an element type whose
     * static encoding is 1..4 bits would need the packed layout (TYPES.md
     * §"Per-Type Encoding" / Array → packed item layout) and is rejected here so
     * a future iteration can add it without silently corrupting earlier wire data.
     * VarInt-element arrays are also deferred — supporting them on the decoder
     * needs item-level parsing of the chunk stream to stop at the right byte.
     */
    static void requireArrayElementSupported(Type element) {
        if (element instanceof Type.VariableLengthInteger) {
            throw new UnsupportedOperationException(
                    "Array of VariableLengthInteger not supported in iteration 5");
        }
        OptionalInt staticBits = staticBitSize(element);
        if (staticBits.isPresent()) {
            int b = staticBits.getAsInt();
            if (b >= 1 && b <= 4) {
                throw new UnsupportedOperationException(
                        "packed-bit array items not supported in iteration 5 (element size " + b + " bits)");
            }
        }
    }
}
