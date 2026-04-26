package com.vanillasource.scan.types.codec;

/**
 * One step in the lockstep walk of the type tree by the decoder, delivered to a
 * {@link DecodingEventHandler}. The consumer interprets each event in the context
 * of its own walk of the same type — no event self-identifies its position.
 *
 * <p>Iteration 4 adds {@link StartField}, {@link EndField}, and {@link Constructor}
 * for struct and union framing. Container and stream variants land in subsequent
 * iterations.
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

    /**
     * Emitted on entry to a struct or constructor field. {@code index} is the
     * field's declared position within its enclosing struct or constructor.
     */
    record StartField(int index) implements DecodingEvent {}

    /**
     * Emitted on exit from a struct or constructor field, paired with a prior
     * {@link StartField} of the same {@code index}.
     */
    record EndField(int index) implements DecodingEvent {}

    /**
     * Emitted once per {@link Type.Union} after its discriminator has been read.
     * {@code index} is the zero-based declaration order of the selected
     * constructor; the constructor's fields follow as further events.
     */
    record Constructor(int index) implements DecodingEvent {}
}
