package com.vanillasource.scan.types.codec;

import java.io.OutputStream;
import java.util.Objects;

/**
 * Push-style decoder exposed as an {@link OutputStream}: callers write the wire
 * bytes in, the decoder emits {@link DecodingEvent}s to its
 * {@link DecodingEventHandler} as decoding progresses. Iteration 5 supports
 * primitives, {@link Type.Struct}, {@link Type.Union}, and {@link Type.Array};
 * {@link Type.Set} and {@link Type.Stream} arrive in later iterations.
 *
 * <p>State persists across writes: a half-read varint, a half-emitted struct, and
 * a partially-consumed bit byte all survive a return from {@code write}. Bytes are
 * consumed only as full events become available, so writing a single byte at a
 * time produces the same event sequence as one bulk write.
 *
 * <p>The decoder holds a single root {@link DecoderFrame}; aggregate frames own
 * and step their own children. Each call to {@link #write} drives the root
 * forward until it returns {@link DecoderFrame.Result.Done} or
 * {@link DecoderFrame.Result.WaitForInput}.
 *
 * <p>{@link #close()} signals end-of-stream and throws if the value is truncated
 * (decoding has not reached the end of the root type).
 */
public final class ValueDecoder extends OutputStream {
    private final DecodingEventHandler handler;
    private final BitReader bits;
    private DecoderFrame root;
    private boolean complete;

    public ValueDecoder(Type rootType, DecodingEventHandler handler) {
        Objects.requireNonNull(rootType, "rootType");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.bits = new BitReader();
        this.root = rootType.createDecodeFrame();
        if (root == null) {
            complete = true;
        } else {
            tryProgress();
        }
    }

    @Override
    public void write(int b) {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes");
        if (length == 0) {
            return;
        }
        if (complete) {
            throw new IllegalStateException("decoder is complete; no more bytes accepted");
        }
        bits.feed(bytes, offset, length);
        tryProgress();
    }

    @Override
    public void flush() {
        // Decoder has no internal output buffer; nothing to flush.
    }

    /**
     * Signals end-of-stream. Throws {@link IllegalStateException} if decoding has
     * not yet reached the end of the root value (the wire bytes were truncated).
     */
    @Override
    public void close() {
        if (!complete) {
            throw new IllegalStateException("decoder closed before value completed");
        }
    }

    public boolean isComplete() {
        return complete;
    }

    private void tryProgress() {
        if (complete) {
            return;
        }
        DecoderFrame.Result r = root.step(bits, handler);
        if (r instanceof DecoderFrame.Result.Done) {
            complete = true;
            root = null;
        }
    }
}
