package com.vanillasource.scan.types.codec;

import java.util.Arrays;

/**
 * One step in the lockstep walk of the type tree by the decoder, delivered to a
 * {@link DecodingEventHandler}. The consumer interprets each event in the context
 * of its own walk of the same type — no event self-identifies its position.
 *
 * <p>Iteration 5 adds {@link StartContainer}, {@link EndContainer}, {@link StartItem},
 * {@link EndItem}, and {@link Chunk} for {@link Type.Array} (and, in later
 * iterations, {@link Type.Set} / {@link Type.Stream}).
 */
public sealed interface DecodingEvent {

    /**
     * Tags a {@link StartContainer} / {@link EndContainer} pair with the kind of
     * aggregate it delimits. {@link #STREAM} pairs are open-ended — no
     * {@link EndContainer} is emitted for a stream.
     */
    enum ContainerKind {
        ARRAY,
        SET,
        STREAM
    }

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

    /**
     * Emitted on entry to an {@link Type.Array}, {@link Type.Set}, or
     * {@link Type.Stream}. The on-wire item count (if any) has already been
     * consumed by the decoder; the consumer learns how many items follow only by
     * counting events until the matching {@link EndContainer}.
     */
    record StartContainer(ContainerKind kind) implements DecodingEvent {}

    /**
     * Emitted on exit from an {@link Type.Array} or {@link Type.Set}.
     * {@link Type.Stream} containers do not emit this event (the stream runs
     * until the enclosing transport ends — TYPES.md §"Per-Type Event Sequences").
     */
    record EndContainer(ContainerKind kind) implements DecodingEvent {}

    /**
     * Marks the start of one item in a complex-item container (item type is a
     * struct, union, or nested aggregate). Paired with {@link EndItem}.
     */
    record StartItem() implements DecodingEvent {}

    /**
     * Marks the end of one item in a complex-item container.
     */
    record EndItem() implements DecodingEvent {}

    /**
     * One byte run from the contents of a primitive-item container — {@link Type.Array}
     * or {@link Type.Set} whose element type is a single primitive. Multiple
     * {@code Chunk} events may be emitted between {@link StartContainer} and
     * {@link EndContainer}; the consumer parses items out of the concatenated
     * bytes using the declared element type.
     */
    record Chunk(byte[] bytes) implements DecodingEvent {
        @Override
        public boolean equals(Object o) {
            return o instanceof Chunk c && Arrays.equals(this.bytes, c.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "Chunk[bytes=" + Arrays.toString(bytes) + "]";
        }
    }
}
