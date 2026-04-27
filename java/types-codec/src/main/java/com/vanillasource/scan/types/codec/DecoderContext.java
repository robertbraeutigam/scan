package com.vanillasource.scan.types.codec;

/**
 * Operations a {@link DecoderFrame} or a {@link Type#startDecode(DecoderContext)}
 * implementation calls back into the {@link ValueDecoder} with. Package-private —
 * the dispatch surface that lets {@link Type} variants and frame variants stay
 * polymorphic without each holding a reference to the concrete decoder.
 */
interface DecoderContext {
    BitReader bits();

    void emit(DecodingEvent event);

    /** Push a child frame onto the stack — it becomes the new top and runs next. */
    void push(DecoderFrame frame);

    /** Pop the top frame without notifying its parent. Used when a frame transforms itself. */
    void pop();

    /**
     * Notify the current top frame that one of its children just finished. The
     * decoder cascades: each frame whose {@link DecoderFrame#onChildCompleted}
     * returns {@code true} is popped, and its own parent is then notified.
     */
    void childCompleted();

    /** Signal that the current frame cannot make progress until more bytes arrive. */
    void waitForInput();
}
