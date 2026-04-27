package com.vanillasource.scan.types.codec;

import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Writes a single value of a given root {@link Type} to an {@link OutputStream}, in
 * lockstep with the type's structure (TYPES.md §"Values Binary Representation").
 * Bytes drain to the delegate stream as soon as they are settled — once a bit byte
 * closes, both it and any byte-aligned data written after it are emitted. The
 * encoder never materializes the full value in memory.
 *
 * <p>The user supplies leaf values in declaration order — {@code writeInteger},
 * {@code writeFloat} for primitives, and {@code writeConstructor} for unions. The
 * encoder advances through the type tree automatically; struct boundaries are
 * implicit. {@link Type.Unit} positions are filled without any user call.
 *
 * <p>The encoder is a thin loop over a stack of polymorphic {@link EncoderFrame}s.
 * Each user write call delegates to the top frame's matching method; the frame
 * returns a {@link EncoderFrame.Result} that tells the encoder how to mutate the
 * stack. Per-type write logic lives on {@link Type} itself.
 *
 * <p>When the root completes, any dangling bit byte is flushed to the delegate
 * (its unused slots remain {@code 0}, per TYPES.md §"Encoder and Decoder State").
 * The delegate is not closed.
 */
public final class ValueEncoder {
    private final BitWriter bits;
    private final Deque<EncoderFrame> stack;
    private boolean complete;

    public ValueEncoder(Type rootType, OutputStream out) {
        Objects.requireNonNull(rootType, "rootType");
        Objects.requireNonNull(out, "out");
        this.bits = new BitWriter(out);
        this.stack = new ArrayDeque<>();
        EncoderFrame frame = rootType.createEncodeFrame();
        if (frame == null) {
            bits.closeBitByte();
            complete = true;
        } else {
            stack.push(frame);
            apply(frame.onPushed(bits));
        }
    }

    public void writeInteger(long value) {
        ensureWritable();
        apply(stack.peek().writeInteger(bits, value));
    }

    public void writeFloat(double value) {
        ensureWritable();
        apply(stack.peek().writeFloat(bits, value));
    }

    public void writeConstructor(int index) {
        ensureWritable();
        apply(stack.peek().writeConstructor(bits, index));
    }

    /**
     * Declares an {@link Type.Array}'s element count. The count is validated
     * against the array's {@link SizeConstraint}; for non-fixed constraints it is
     * also written to the wire as a {@link Type.VariableLengthInteger} sized to
     * fit every admitted length (TYPES.md §"Per-Type Encoding" / Array). Items
     * follow as ordinary primitive / struct / union writes.
     */
    public void startArray(int count) {
        ensureWritable();
        apply(stack.peek().startArray(bits, count));
    }

    public boolean isComplete() {
        return complete;
    }

    private void ensureWritable() {
        if (complete) {
            throw new IllegalStateException("encoder is complete");
        }
        if (stack.isEmpty()) {
            throw new IllegalStateException("encoder has no further input");
        }
    }

    private void apply(EncoderFrame.Result r) {
        while (true) {
            if (r instanceof EncoderFrame.Result.Wait) {
                return;
            } else if (r instanceof EncoderFrame.Result.Done) {
                stack.pop();
                if (stack.isEmpty()) {
                    bits.closeBitByte();
                    complete = true;
                    return;
                }
                r = stack.peek().onChildCompleted(bits);
            } else if (r instanceof EncoderFrame.Result.Push p) {
                stack.push(p.child());
                r = p.child().onPushed(bits);
            } else if (r instanceof EncoderFrame.Result.Replace rp) {
                stack.pop();
                stack.push(rp.next());
                r = rp.next().onPushed(bits);
            } else {
                throw new IllegalStateException("unknown result: " + r);
            }
        }
    }
}
