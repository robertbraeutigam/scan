package com.vanillasource.scan.types.codec;

/**
 * Operations an {@link EncoderFrame} or a {@link Type#startEncode(EncoderContext)}
 * implementation calls back into the {@link ValueEncoder} with. Package-private —
 * the dispatch surface that lets {@link Type} variants and frame variants stay
 * polymorphic without each holding a reference to the concrete encoder.
 */
interface EncoderContext {
    BitWriter bits();

    /** Push a child frame onto the stack — it becomes the new top, awaiting the next user write. */
    void push(EncoderFrame frame);

    /** Pop the top frame without notifying its parent. Used when a frame transforms itself. */
    void pop();

    /**
     * Notify the current top frame that one of its children just finished. The
     * encoder cascades: each frame whose {@link EncoderFrame#onChildCompleted}
     * returns {@code true} is popped, and its own parent is then notified. When the
     * stack drains, the encoder closes any dangling bit byte and marks itself complete.
     */
    void childCompleted();
}
