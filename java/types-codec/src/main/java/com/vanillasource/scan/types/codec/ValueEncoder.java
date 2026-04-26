package com.vanillasource.scan.types.codec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Writes a single value of a given root {@link Type} to a byte array, in lockstep
 * with the type's structure (TYPES.md §"Values Binary Representation"). Iteration 4
 * supports primitives, {@link Type.Struct}, and {@link Type.Union}; aggregates and
 * streams arrive in subsequent iterations.
 *
 * <p>The user supplies leaf values in declaration order — {@code writeInteger},
 * {@code writeFloat} for primitives, and {@code writeConstructor} for unions. The
 * encoder advances through the type tree automatically; struct boundaries are
 * implicit. {@link Type.Unit} positions are filled without any user call.
 *
 * <p>If the root type is itself complete-with-zero-bytes (e.g. {@code Unit} or an
 * empty {@code Struct}), the encoder is {@link #isComplete() complete} at
 * construction and {@link #toByteArray()} returns an empty array.
 */
public final class ValueEncoder {
    private final Type rootType;
    private final BitWriter bits;
    private final Deque<Frame> stack;
    private boolean complete;

    public ValueEncoder(Type rootType) {
        this.rootType = Objects.requireNonNull(rootType, "rootType");
        this.bits = new BitWriter();
        this.stack = new ArrayDeque<>();
        descendInto(rootType);
    }

    public void writeInteger(long value) {
        ensureWritable();
        Frame top = stack.peek();
        if (!(top instanceof PrimitiveFrame p)) {
            throw new IllegalStateException("expected " + describe(top) + ", not an integer");
        }
        if (p.type instanceof Type.UnsignedInteger u) {
            writeUnsignedBigEndian(value, u.byteSize());
        } else if (p.type instanceof Type.SignedInteger s) {
            writeSignedBigEndian(value, s.byteSize());
        } else if (p.type instanceof Type.VariableLengthInteger v) {
            writeVarInt(value, v.maxBytes());
        } else {
            throw new IllegalStateException("expected integer type, got " + p.type);
        }
        stack.pop();
        childCompleted();
    }

    public void writeFloat(double value) {
        ensureWritable();
        Frame top = stack.peek();
        if (!(top instanceof PrimitiveFrame p) || !(p.type instanceof Type.FloatingPoint f)) {
            throw new IllegalStateException("expected " + describe(top) + ", not a float");
        }
        if (f.byteSize() == 4) {
            writeBigEndianBytes(Float.floatToRawIntBits((float) value) & 0xFFFFFFFFL, 4);
        } else {
            writeBigEndianBytes(Double.doubleToRawLongBits(value), 8);
        }
        stack.pop();
        childCompleted();
    }

    public void writeConstructor(int index) {
        ensureWritable();
        Frame top = stack.peek();
        if (!(top instanceof UnionFrame u)) {
            throw new IllegalStateException("expected " + describe(top) + ", not a constructor");
        }
        List<Type.Constructor> ctors = u.union.constructors();
        if (index < 0 || index >= ctors.size()) {
            throw new IllegalArgumentException(
                    "constructor index " + index + " out of range [0," + ctors.size() + ")");
        }
        int k = TypeLayout.ceilLog2(ctors.size());
        if (k > 0) {
            bits.writeBits(k, index);
        }
        stack.pop();
        Type.Constructor ctor = ctors.get(index);
        if (ctor.fields().isEmpty()) {
            childCompleted();
        } else {
            stack.push(new FieldsFrame(ctor.fields()));
            descendInto(ctor.fields().get(0).type());
        }
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
        Frame top = stack.peek();
        if (!(top instanceof ArrayFrame af)) {
            throw new IllegalStateException("expected " + describe(top) + ", not an array");
        }
        if (af.declared) {
            throw new IllegalStateException("array count already declared");
        }
        SizeConstraint sc = af.array.size();
        validateArraySize(count, sc);
        bits.closeBitByte();
        if (!sc.isFixed()) {
            int v = TypeLayout.pickCountVarintSize(sc);
            long encoded = (long) count - TypeLayout.lowerBound(sc);
            writeVarInt(encoded, v);
        }
        af.declaredCount = count;
        af.declared = true;
        if (count == 0) {
            bits.closeBitByte();
            stack.pop();
            childCompleted();
        } else {
            descendInto(af.array.element());
        }
    }

    public boolean isComplete() {
        return complete;
    }

    public byte[] toByteArray() {
        if (!complete) {
            throw new IllegalStateException("encoder is incomplete");
        }
        return bits.toByteArray();
    }

    private void ensureWritable() {
        if (complete) {
            throw new IllegalStateException("encoder is complete");
        }
    }

    private void descendInto(Type t) {
        if (t instanceof Type.Unit) {
            childCompleted();
            return;
        }
        if (t instanceof Type.Struct s) {
            if (s.fields().isEmpty()) {
                childCompleted();
                return;
            }
            stack.push(new FieldsFrame(s.fields()));
            descendInto(s.fields().get(0).type());
            return;
        }
        if (t instanceof Type.Union u) {
            stack.push(new UnionFrame(u));
            return;
        }
        if (t instanceof Type.Array a) {
            TypeLayout.requireArrayElementSupported(a.element());
            stack.push(new ArrayFrame(a));
            return;
        }
        if (t instanceof Type.Set || t instanceof Type.Stream) {
            throw new UnsupportedOperationException(
                    "type not supported in iteration 5: " + t);
        }
        stack.push(new PrimitiveFrame(t));
    }

    private void childCompleted() {
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top instanceof FieldsFrame f) {
                f.fieldIndex++;
                if (f.fieldIndex >= f.fields.size()) {
                    stack.pop();
                    continue;
                }
                descendInto(f.fields.get(f.fieldIndex).type());
                return;
            }
            if (top instanceof ArrayFrame af) {
                af.itemsCompleted++;
                bits.closeBitByte();
                if (af.itemsCompleted >= af.declaredCount) {
                    stack.pop();
                    continue;
                }
                descendInto(af.array.element());
                return;
            }
            throw new IllegalStateException("unexpected frame at child-complete: " + top);
        }
        complete = true;
    }

    private static String describe(Frame f) {
        if (f == null) {
            return "no further input";
        }
        if (f instanceof PrimitiveFrame p) {
            return "primitive " + p.type;
        }
        if (f instanceof UnionFrame u) {
            return "union with " + u.union.constructors().size() + " constructors";
        }
        if (f instanceof FieldsFrame ff) {
            return "field " + ff.fieldIndex + " of " + ff.fields.size();
        }
        if (f instanceof ArrayFrame af) {
            return af.declared
                    ? ("array item " + af.itemsCompleted + " of " + af.declaredCount)
                    : "array (count not yet declared)";
        }
        return f.toString();
    }

    private void writeUnsignedBigEndian(long value, int byteSize) {
        if (byteSize < 8) {
            long max = (1L << (byteSize * 8)) - 1;
            if (value < 0 || value > max) {
                throw new IllegalArgumentException(
                        "value " + value + " does not fit in " + byteSize + " unsigned bytes");
            }
        }
        writeBigEndianBytes(value, byteSize);
    }

    private void writeSignedBigEndian(long value, int byteSize) {
        if (byteSize < 8) {
            long max = (1L << (byteSize * 8 - 1)) - 1;
            long min = -(1L << (byteSize * 8 - 1));
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        "value " + value + " does not fit in " + byteSize + " signed bytes");
            }
        }
        writeBigEndianBytes(value, byteSize);
    }

    private void writeBigEndianBytes(long value, int byteSize) {
        byte[] out = new byte[byteSize];
        for (int i = 0; i < byteSize; i++) {
            out[i] = (byte) ((value >>> ((byteSize - 1 - i) * 8)) & 0xFF);
        }
        bits.writeBytes(out);
    }

    private void writeVarInt(long value, int maxN) {
        if (value < 0) {
            throw new IllegalArgumentException("VariableLengthInteger value must be non-negative: " + value);
        }
        int bitsNeeded = (value == 0) ? 1 : (64 - Long.numberOfLeadingZeros(value));
        int k = -1;
        for (int trial = 1; trial <= maxN; trial++) {
            int capacity = (trial < maxN) ? (7 * trial) : (7 * (trial - 1) + 8);
            if (capacity >= bitsNeeded) {
                k = trial;
                break;
            }
        }
        if (k < 0) {
            throw new IllegalArgumentException(
                    "value " + value + " does not fit in VariableLengthInteger(" + maxN + ")");
        }
        int totalBits = (k < maxN) ? (7 * k) : (7 * (k - 1) + 8);
        byte[] out = new byte[k];
        for (int i = 0; i < k; i++) {
            int groupBits = (i < k - 1) ? 7 : ((k < maxN) ? 7 : 8);
            int bitsAfter = totalBits - 7 * i - groupBits;
            int groupValue = (int) ((value >>> bitsAfter) & ((1L << groupBits) - 1));
            int byteValue = (i < k - 1) ? (0x80 | groupValue) : groupValue;
            out[i] = (byte) byteValue;
        }
        bits.writeBytes(out);
    }

    private static void validateArraySize(int count, SizeConstraint sc) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative: " + count);
        }
        if (sc instanceof SizeConstraint.Range r) {
            if (count < r.min() || count > r.max()) {
                throw new IllegalArgumentException(
                        "count " + count + " not in [" + r.min() + ", " + r.max() + "]");
            }
        }
    }

    private sealed interface Frame {}

    private static final class PrimitiveFrame implements Frame {
        final Type type;

        PrimitiveFrame(Type type) {
            this.type = type;
        }
    }

    private static final class UnionFrame implements Frame {
        final Type.Union union;

        UnionFrame(Type.Union union) {
            this.union = union;
        }
    }

    private static final class FieldsFrame implements Frame {
        final List<Type.Field> fields;
        int fieldIndex;

        FieldsFrame(List<Type.Field> fields) {
            this.fields = fields;
            this.fieldIndex = 0;
        }
    }

    private static final class ArrayFrame implements Frame {
        final Type.Array array;
        boolean declared;
        int declaredCount;
        int itemsCompleted;

        ArrayFrame(Type.Array array) {
            this.array = array;
        }
    }
}
