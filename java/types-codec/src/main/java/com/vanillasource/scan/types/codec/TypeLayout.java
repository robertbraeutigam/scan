package com.vanillasource.scan.types.codec;

import java.util.OptionalInt;

/**
 * Codec-side guard for array element types not yet supported by the iteration-5
 * encoder/decoder. Lives here (rather than on {@link Type.Array}) because it is a
 * codec-implementation status, not a permanent property of the type — when later
 * iterations add packed-bit and varint-element arrays, this method goes away.
 */
final class TypeLayout {
    private TypeLayout() {
    }

    static void requireArrayElementSupported(Type element) {
        if (element instanceof Type.VariableLengthInteger) {
            throw new UnsupportedOperationException(
                    "Array of VariableLengthInteger not supported in iteration 5");
        }
        OptionalInt staticBits = element.staticBitSize();
        if (staticBits.isPresent()) {
            int b = staticBits.getAsInt();
            if (b >= 1 && b <= 4) {
                throw new UnsupportedOperationException(
                        "packed-bit array items not supported in iteration 5 (element size " + b + " bits)");
            }
        }
    }
}
