package com.vanillasource.scan.types.codec;

import java.io.OutputStream;
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
 * <p>The encoder holds a single root {@link EncoderFrame}; aggregate frames own
 * and delegate to their own children. Each user write call delegates to the root
 * frame and propagates down to the active leaf via per-frame {@code currentChild}
 * references.
 *
 * <p>When the root completes, any dangling bit byte is flushed to the delegate
 * (its unused slots remain {@code 0}, per TYPES.md §"Encoder and Decoder State").
 * The delegate is not closed.
 */
public final class ValueEncoder {
    private final BitWriter bits;
    private EncoderFrame root;
    private boolean complete;

    public ValueEncoder(Type rootType, OutputStream out) {
        Objects.requireNonNull(rootType, "rootType");
        Objects.requireNonNull(out, "out");
        this.bits = new BitWriter(out);
        this.root = rootType.createEncodeFrame();
        if (root == null) {
            bits.closeBitByte();
            complete = true;
        } else {
            apply(root.onEntered(bits));
        }
    }

    public void writeInteger(long value) {
        ensureWritable();
        apply(root.writeInteger(bits, value));
    }

    public void writeFloat(double value) {
        ensureWritable();
        apply(root.writeFloat(bits, value));
    }

    public void writeConstructor(int index) {
        ensureWritable();
        apply(root.writeConstructor(bits, index));
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
        apply(root.startArray(bits, count));
    }

    public boolean isComplete() {
        return complete;
    }

    private void ensureWritable() {
        if (complete) {
            throw new IllegalStateException("encoder is complete");
        }
    }

    private void apply(EncoderFrame.Result r) {
        if (r instanceof EncoderFrame.Result.Done) {
            bits.closeBitByte();
            complete = true;
            root = null;
        }
    }
}
