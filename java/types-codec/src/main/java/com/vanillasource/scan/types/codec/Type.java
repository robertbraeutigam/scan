package com.vanillasource.scan.types.codec;

import java.util.OptionalInt;

/**
 * Static description of a SCAN value's shape (TYPES.md §"Values Binary
 * Representation"). Concrete type variants — {@code Unit}, {@code UnsignedInteger},
 * {@code Struct}, {@code Union}, etc. — live in
 * {@code com.vanillasource.scan.types.codec.types}.
 *
 * <p>Each variant knows how to build its own encode and decode frame; per-type
 * write logic for primitives also lives on the variant via
 * {@link #writeIntegerValue(BitWriter, long)} / {@link #writeFloatValue(BitWriter, double)}.
 *
 * <p>Constraints (TYPES.md §Constraints) are not modelled here yet — they are added
 * when iteration 8 needs them for the L3 binary representation of type definitions.
 */
public interface Type {

    /**
     * Returns true if this type contains a Stream anywhere in its tree. Drives the
     * structural rule that a stream-bearing type may only appear as the last field
     * of its enclosing struct or constructor (TYPES.md line 96: "a type may
     * contain at most one Stream, transitively").
     */
    boolean containsStream();

    /**
     * Static bit size of values of this type, if it has one. Empty for variable-length
     * types (varints, streams, arrays, sets) and for any aggregate that transitively
     * contains one of those (or, for a union, whose constructors disagree on size).
     */
    OptionalInt staticBitSize();

    /**
     * Build the {@link DecoderFrame} that drives decoding of a value of this type.
     * Returns {@code null} for positions that contribute no frame (e.g. an empty
     * struct); the caller cascades {@code childCompleted} in that case.
     */
    DecoderFrame createDecodeFrame();

    /**
     * Build the {@link EncoderFrame} that drives encoding of a value of this type.
     * Returns {@code null} for positions that contribute no frame — i.e. {@code Unit}
     * (no bytes, no event) and an empty struct; the caller cascades
     * {@code childCompleted} in that case.
     */
    EncoderFrame createEncodeFrame();

    /**
     * Write an integer value to {@code bits} using this type's encoding. Only
     * the integer types override this; others throw to signal a type mismatch.
     */
    default void writeIntegerValue(BitWriter bits, long value) {
        throw new IllegalStateException("expected integer type, got " + this);
    }

    /**
     * Write a floating-point value to {@code bits} using this type's encoding.
     * Only floating-point types override this; others throw.
     */
    default void writeFloatValue(BitWriter bits, double value) {
        throw new IllegalStateException("expected floating point type, got " + this);
    }

    /**
     * Codec-side guard for array element types not yet supported by the iteration-5
     * encoder/decoder. Default rejects packed-bit primitives (1–4 bit static size);
     * variable-length elements override to reject themselves. Goes away when later
     * iterations add packed-bit and varint-element arrays.
     */
    default void requireSupportedAsArrayElement() {
        OptionalInt sb = staticBitSize();
        if (sb.isPresent()) {
            int b = sb.getAsInt();
            if (b >= 1 && b <= 4) {
                throw new UnsupportedOperationException(
                        "packed-bit array items not supported in iteration 5 (element size " + b + " bits)");
            }
        }
    }

    /**
     * Byte size of this type when it appears as a fixed-byte primitive array element
     * (the chunk-emit fast path). Present for {@code Unit}, fixed-width integer, and
     * floating-point types; empty otherwise (those go through the per-item
     * Start/EndItem flow).
     */
    default OptionalInt fixedPrimitiveByteSize() {
        return OptionalInt.empty();
    }
}
