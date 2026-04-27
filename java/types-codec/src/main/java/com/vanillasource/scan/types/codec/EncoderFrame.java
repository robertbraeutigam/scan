package com.vanillasource.scan.types.codec;

/**
 * One node of a {@link ValueEncoder}'s walk through a {@link Type} tree.
 * Each user write call dispatches to the root frame; aggregate frames own
 * their {@code currentChild} and delegate writes down the chain to the active
 * leaf, then run their own advance/close work inline when the child returns
 * {@link Result.Done}.
 *
 * <p>Concrete implementations live in
 * {@code com.vanillasource.scan.types.codec.types}.
 */
public interface EncoderFrame {

    /** What the encoder should do next. */
    sealed interface Result {
        /** Frame is active and awaits the next user write. */
        record Wait() implements Result {}
        /** Frame is finished; its parent (or the encoder, if root) takes over. */
        record Done() implements Result {}
    }

    /** Human-readable position description for type-mismatch error messages. */
    String describe();

    /**
     * Directive immediately after this frame becomes active. Default is
     * {@link Result.Wait} — the frame waits for the next user write. Frames
     * with implicit substructure (struct/constructor fields) override this to
     * descend into their first child immediately.
     */
    default Result onEntered(BitWriter bits) {
        return new Result.Wait();
    }

    default Result writeInteger(BitWriter bits, long value) {
        throw new IllegalStateException("expected " + describe() + ", not an integer");
    }

    default Result writeFloat(BitWriter bits, double value) {
        throw new IllegalStateException("expected " + describe() + ", not a float");
    }

    default Result writeConstructor(BitWriter bits, int index) {
        throw new IllegalStateException("expected " + describe() + ", not a constructor");
    }

    default Result startArray(BitWriter bits, int count) {
        throw new IllegalStateException("expected " + describe() + ", not an array");
    }
}
