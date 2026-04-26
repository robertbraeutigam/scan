package com.vanillasource.scan.types.codec;

/**
 * One step in the lockstep walk of the type tree by the decoder, delivered to a
 * {@link DecodingEventHandler}. The consumer interprets each event in the context
 * of its own walk of the same type — no event self-identifies its position.
 *
 * <p>Iteration 3 ships only the primitive variants. Container, field, and stream
 * variants land in subsequent iterations as the encoder/decoder learn aggregates.
 */
public sealed interface DecodingEvent {

    /**
     * Emitted for {@link Type.UnsignedInteger}, {@link Type.SignedInteger}, and
     * {@link Type.VariableLengthInteger} positions. The consumer interprets the
     * value's signedness and width from the type it knows it is at.
     */
    record IntegerScalar(long value) implements DecodingEvent {}

    /**
     * Emitted for {@link Type.FloatingPoint} positions. The consumer interprets
     * width (binary32 vs binary64) from the type it knows it is at.
     */
    record FloatingPointScalar(double value) implements DecodingEvent {}

    /**
     * Emitted for {@link Type.Unit} positions — carries no value, marks position.
     */
    record UnitScalar() implements DecodingEvent {}
}
