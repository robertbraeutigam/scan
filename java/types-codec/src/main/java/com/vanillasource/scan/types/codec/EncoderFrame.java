package com.vanillasource.scan.types.codec;

/**
 * One stack entry of a {@link ValueEncoder}'s walk through a {@link Type} tree.
 * Each user write call dispatches to the top frame's matching method; the frame
 * returns a {@link Result} describing the next stack action, and the encoder
 * carries it out.
 *
 * <p>Frames never mutate the stack themselves — they only describe what should
 * happen next. Concrete implementations live in
 * {@code com.vanillasource.scan.types.codec.types}.
 */
public interface EncoderFrame {

    /** What the encoder should do next. */
    sealed interface Result {
        /** Frame stays on the stack, awaits the next user write. */
        record Wait() implements Result {}
        /** Pop this frame; cascade {@code onChildCompleted} on the new top. */
        record Done() implements Result {}
        /** Push a new child frame on top; then run its {@link EncoderFrame#onPushed(BitWriter)}. */
        record Push(EncoderFrame child) implements Result {}
        /** Pop this frame, push {@code next} in its place, then run {@code next.onPushed}. */
        record Replace(EncoderFrame next) implements Result {}
    }

    /** Human-readable position description for type-mismatch error messages. */
    String describe();

    /**
     * Directive immediately after this frame is pushed onto the stack. Default
     * is {@link Result.Wait} — the frame waits for the next user write. Frames
     * with implicit substructure (struct/constructor fields) override this to
     * descend into their first child immediately.
     */
    default Result onPushed(BitWriter bits) {
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

    /** Called when a child frame has just completed. */
    Result onChildCompleted(BitWriter bits);
}
