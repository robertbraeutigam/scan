package com.vanillasource.scan.types.codec2;

import java.util.Arrays;

/**
 * One step in the lockstep walk of the type tree by the decoder, delivered to a
 * {@link DecodingEventHandler}. The consumer interprets each event in the context
 * of its own walk of the same type — no event self-identifies its position.
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

    record IntegerScalar(long value, int sign) implements DecodingEvent {}

    record FloatingPointScalar(double value) implements DecodingEvent {}

    record UnitScalar() implements DecodingEvent {}

    record StartField(int index) implements DecodingEvent {}

    record EndField(int index) implements DecodingEvent {}

    record Constructor(int index) implements DecodingEvent {}

    record StartContainer(ContainerKind kind) implements DecodingEvent {}

    record EndContainer(ContainerKind kind) implements DecodingEvent {}

    record StartItem() implements DecodingEvent {}

    record EndItem() implements DecodingEvent {}

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
