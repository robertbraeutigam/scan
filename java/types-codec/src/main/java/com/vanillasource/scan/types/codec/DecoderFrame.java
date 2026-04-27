package com.vanillasource.scan.types.codec;

/**
 * One node of a {@link ValueDecoder}'s walk through a {@link Type} tree.
 * {@code step} drains as much of the buffered bits/bytes as the current position
 * allows. Aggregate frames own and step their own children directly: when a
 * child returns {@link Result.Done}, the parent runs its closing work inline
 * and continues to the next child; when a child returns
 * {@link Result.WaitForInput}, the parent stores it and propagates the same
 * result so the decoder can return to its caller and wait for more bytes.
 *
 * <p>Concrete implementations live in
 * {@code com.vanillasource.scan.types.codec.types}.
 */
public interface DecoderFrame {

    /** What the decoder should do next. */
    sealed interface Result {
        /** Frame stays active; decoder pauses until more bytes arrive. */
        record WaitForInput() implements Result {}
        /** Frame is finished; its parent (or the decoder, if root) takes over. */
        record Done() implements Result {}
    }

    /** Try to make progress with the bits currently available. */
    Result step(BitReader bits, DecodingEventHandler events);
}
