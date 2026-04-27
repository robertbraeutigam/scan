package com.vanillasource.scan.types.codec;

/**
 * One stack entry of a {@link ValueDecoder}'s walk through a {@link Type} tree.
 * {@code step} drains as much of the buffered bits/bytes as the current position
 * allows; {@code onChildCompleted} is called when a child frame has just finished.
 * Both return a {@link Result} describing the next stack action; the decoder
 * carries it out — frames never mutate the stack themselves.
 *
 * <p>Concrete implementations live in
 * {@code com.vanillasource.scan.types.codec.types}.
 */
public interface DecoderFrame {

    /** What the decoder should do next. */
    sealed interface Result {
        /** Frame stays on top; decoder pauses until more bytes arrive. */
        record WaitForInput() implements Result {}
        /** Pop this frame; cascade {@code onChildCompleted} on the new top. */
        record Done() implements Result {}
        /** Push a new child frame on top; the decoder will step it next. */
        record Push(DecoderFrame child) implements Result {}
        /** Pop this frame, push {@code next} in its place; the decoder will step the replacement next. */
        record Replace(DecoderFrame next) implements Result {}
    }

    /** Try to make progress with the bits currently available. */
    Result step(BitReader bits, DecodingEventHandler events);

    /** Called when a child frame has just finished. */
    Result onChildCompleted(BitReader bits, DecodingEventHandler events);
}
