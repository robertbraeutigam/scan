package com.vanillasource.scan.types.codec;

import java.util.Arrays;

/**
 * One step in the lockstep walk of the type tree, exchanged between codecs and the
 * surrounding pipeline. The same vocabulary flows in both directions: a decoder
 * pushes events into an {@link EventSink}; an encoder consumes events from an
 * upstream source and writes bytes. Each event is interpreted in the context of
 * the consumer's own walk of the type — no event self-identifies its position.
 */
public sealed interface Event {
    /**
     * Tags a {@link StartContainer} / {@link EndContainer} pair with the kind of
     * finite aggregate it delimits. All container kinds are bounded — both Start
     * and End fire, and the Start carries the wire-declared count. The chunked
     * vs. itemized item-delivery split is determined by the element type's wire
     * footprint (see the spec), not by a separate kind. Open-ended streams use
     * {@link StartStream} instead and have no end event.
     */
    enum ContainerKind {
        ARRAY,
        SET
    }

    record IntegerScalar(long value, int sign) implements Event {}

    record FloatingPointScalar(double value) implements Event {}

    record UnitScalar() implements Event {}

    record StartField(int index) implements Event {}

    record EndField(int index) implements Event {}

    record Constructor(int index) implements Event {}

    /** Begins a definite-length aggregate; {@code count} carries the item count on the wire. */
    record StartContainer(ContainerKind kind, long count) implements Event {}

    record EndContainer(ContainerKind kind) implements Event {}

    /**
     * Begins an open-ended stream. No length precedes the items; no terminating
     * event is emitted — the stream runs until the enclosing transport ends.
     * Whether items follow as {@link StartItem}/{@link EndItem} pairs (itemized)
     * or as {@link Chunk} events (chunked) is determined by the element type's
     * wire footprint, exactly as for finite item-bearing containers.
     */
    record StartStream() implements Event {}

    record StartItem() implements Event {}

    record EndItem() implements Event {}

    record Chunk(byte[] bytes) implements Event {
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
