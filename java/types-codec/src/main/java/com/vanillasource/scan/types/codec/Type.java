package com.vanillasource.scan.types.codec;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Static description of a SCAN value's shape, plus the codec-side dispatch
 * methods that drive {@link ValueEncoder} and {@link ValueDecoder}. Each variant
 * knows how to start its own encode/decode by pushing the right frame (or
 * emitting a trivial event); primitives also know how to write their own value
 * to a {@link BitWriter}.
 *
 * <p>Constraints (TYPES.md §Constraints) are not modelled here yet — they are added
 * when iteration 8 needs them for the L3 binary representation of type definitions.
 */
public sealed interface Type {

    /**
     * Returns true if this type contains a {@link Stream} anywhere in its tree.
     * Drives the structural rule that a stream-bearing type may only appear as the
     * last field of its enclosing struct or constructor (TYPES.md line 96: "a type
     * may contain at most one Stream, transitively").
     */
    boolean containsStream();

    /**
     * Static bit size of values of this type, if it has one. Empty for
     * {@link VariableLengthInteger}, {@link Stream}, {@link Array}, {@link Set},
     * and any {@link Struct} or {@link Union} that transitively contains one of
     * those (or, for {@code Union}, whose constructors disagree on size).
     */
    OptionalInt staticBitSize();

    /**
     * Begin decoding a value of this type. The implementation either pushes a
     * {@link DecoderFrame} or — for trivial types like {@link Unit} or an empty
     * {@link Struct} — emits its event(s) and notifies the parent immediately.
     */
    void startDecode(DecoderContext ctx);

    /**
     * Begin encoding a value of this type. The implementation either pushes an
     * {@link EncoderFrame} (awaiting the next user write) or — for trivial types
     * like {@link Unit} or an empty {@link Struct} — notifies the parent immediately.
     */
    void startEncode(EncoderContext ctx);

    /**
     * Write an integer value to {@code bits} using this type's encoding. Only
     * the integer types ({@link UnsignedInteger}, {@link SignedInteger},
     * {@link VariableLengthInteger}) override this; others throw to signal a
     * type mismatch from the encoder.
     */
    default void writeIntegerValue(BitWriter bits, long value) {
        throw new IllegalStateException("expected integer type, got " + this);
    }

    /**
     * Write a floating-point value to {@code bits} using this type's encoding.
     * Only {@link FloatingPoint} overrides this; others throw to signal a type
     * mismatch from the encoder.
     */
    default void writeFloatValue(BitWriter bits, double value) {
        throw new IllegalStateException("expected floating point type, got " + this);
    }

    /**
     * Codec-side guard for array element types not yet supported by the
     * iteration-5 encoder/decoder. Default rejects packed-bit primitives (1–4 bit
     * static size); {@link VariableLengthInteger} overrides to reject itself.
     * Goes away when later iterations add packed-bit and varint-element arrays.
     */
    default void requireSupportedAsArrayElement() {
        OptionalInt sb = staticBitSize();
        if (sb.isPresent()) {
            int b = sb.getAsInt();
            if (b >= 1 && b <= 4) {
                throw new UnsupportedOperationException(
                        "packed-bit array items not supported in iteration 5 (element size " + b + " bits)");
            }
        }
    }

    /**
     * Byte size of this type when it appears as a fixed-byte primitive array
     * element (the {@link DecoderFrame.ArrayFrame} chunk-emit fast path).
     * Present for {@link Unit}, {@link UnsignedInteger}, {@link SignedInteger},
     * {@link FloatingPoint}; empty otherwise (those go through the per-item
     * Start/EndItem flow).
     */
    default OptionalInt fixedPrimitiveByteSize() {
        return OptionalInt.empty();
    }

    record Unit() implements Type {
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(0); }
        @Override public OptionalInt fixedPrimitiveByteSize() { return OptionalInt.of(0); }

        @Override
        public void startDecode(DecoderContext ctx) {
            ctx.emit(new DecodingEvent.UnitScalar());
            ctx.childCompleted();
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            ctx.childCompleted();
        }
    }

    record UnsignedInteger(int byteSize) implements Type {
        public UnsignedInteger {
            requireByteSize1to8(byteSize);
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
        @Override public OptionalInt fixedPrimitiveByteSize() { return OptionalInt.of(byteSize); }

        @Override
        public void startDecode(DecoderContext ctx) {
            ctx.push(new DecoderFrame.UnsignedIntegerFrame(byteSize));
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            ctx.push(new EncoderFrame.PrimitiveFrame(this));
        }

        @Override
        public void writeIntegerValue(BitWriter bits, long value) {
            if (byteSize < 8) {
                long max = (1L << (byteSize * 8)) - 1;
                if (value < 0 || value > max) {
                    throw new IllegalArgumentException(
                            "value " + value + " does not fit in " + byteSize + " unsigned bytes");
                }
            }
            writeBigEndianBytes(bits, value, byteSize);
        }
    }

    record SignedInteger(int byteSize) implements Type {
        public SignedInteger {
            requireByteSize1to8(byteSize);
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
        @Override public OptionalInt fixedPrimitiveByteSize() { return OptionalInt.of(byteSize); }

        @Override
        public void startDecode(DecoderContext ctx) {
            ctx.push(new DecoderFrame.SignedIntegerFrame(byteSize));
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            ctx.push(new EncoderFrame.PrimitiveFrame(this));
        }

        @Override
        public void writeIntegerValue(BitWriter bits, long value) {
            if (byteSize < 8) {
                long max = (1L << (byteSize * 8 - 1)) - 1;
                long min = -(1L << (byteSize * 8 - 1));
                if (value < min || value > max) {
                    throw new IllegalArgumentException(
                            "value " + value + " does not fit in " + byteSize + " signed bytes");
                }
            }
            writeBigEndianBytes(bits, value, byteSize);
        }
    }

    record VariableLengthInteger(int maxBytes) implements Type {
        public VariableLengthInteger {
            requireByteSize1to8(maxBytes);
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }

        @Override
        public void startDecode(DecoderContext ctx) {
            ctx.push(new DecoderFrame.VariableLengthIntegerFrame(maxBytes));
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            ctx.push(new EncoderFrame.PrimitiveFrame(this));
        }

        @Override
        public void writeIntegerValue(BitWriter bits, long value) {
            writeVarInt(bits, value, maxBytes);
        }

        @Override
        public void requireSupportedAsArrayElement() {
            throw new UnsupportedOperationException(
                    "Array of VariableLengthInteger not supported in iteration 5");
        }
    }

    record FloatingPoint(int byteSize) implements Type {
        public FloatingPoint {
            if (byteSize != 4 && byteSize != 8) {
                throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
            }
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
        @Override public OptionalInt fixedPrimitiveByteSize() { return OptionalInt.of(byteSize); }

        @Override
        public void startDecode(DecoderContext ctx) {
            ctx.push(new DecoderFrame.FloatingPointFrame(byteSize));
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            ctx.push(new EncoderFrame.PrimitiveFrame(this));
        }

        @Override
        public void writeFloatValue(BitWriter bits, double value) {
            if (byteSize == 4) {
                writeBigEndianBytes(bits, Float.floatToRawIntBits((float) value) & 0xFFFFFFFFL, 4);
            } else {
                writeBigEndianBytes(bits, Double.doubleToRawLongBits(value), 8);
            }
        }
    }

    record Struct(List<Field> fields) implements Type {
        public Struct {
            fields = List.copyOf(fields);
            requireStreamLastIfPresent(fields);
        }
        @Override public boolean containsStream() {
            return anyFieldContainsStream(fields);
        }
        @Override public OptionalInt staticBitSize() {
            return sumFieldBitSizes(fields);
        }

        @Override
        public void startDecode(DecoderContext ctx) {
            if (fields.isEmpty()) {
                ctx.childCompleted();
            } else {
                ctx.push(new DecoderFrame.FieldsFrame(fields));
            }
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            if (fields.isEmpty()) {
                ctx.childCompleted();
            } else {
                EncoderFrame.FieldsFrame frame = new EncoderFrame.FieldsFrame(fields);
                ctx.push(frame);
                frame.enter(ctx);
            }
        }
    }

    record Union(List<Constructor> constructors) implements Type {
        public Union {
            constructors = List.copyOf(constructors);
            if (constructors.isEmpty()) {
                throw new IllegalArgumentException("Union must have at least one constructor");
            }
        }
        @Override public boolean containsStream() {
            for (Constructor c : constructors) {
                if (anyFieldContainsStream(c.fields())) {
                    return true;
                }
            }
            return false;
        }
        @Override public OptionalInt staticBitSize() {
            Integer ctorSize = null;
            for (Constructor c : constructors) {
                OptionalInt sub = sumFieldBitSizes(c.fields());
                if (sub.isEmpty()) {
                    return OptionalInt.empty();
                }
                int s = sub.getAsInt();
                if (ctorSize == null) {
                    ctorSize = s;
                } else if (ctorSize != s) {
                    return OptionalInt.empty();
                }
            }
            return OptionalInt.of(discriminatorBits() + (ctorSize == null ? 0 : ctorSize));
        }
        /** Bits the encoder writes / decoder reads for this union's discriminator. */
        public int discriminatorBits() {
            int n = constructors.size();
            if (n <= 1) {
                return 0;
            }
            return 32 - Integer.numberOfLeadingZeros(n - 1);
        }

        @Override
        public void startDecode(DecoderContext ctx) {
            ctx.push(new DecoderFrame.UnionFrame(this));
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            ctx.push(new EncoderFrame.UnionFrame(this));
        }
    }

    record Array(Type element, SizeConstraint size) implements Type {
        public Array {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(size, "size");
            if (element.containsStream()) {
                throw new IllegalArgumentException("Array element must not contain a Stream");
            }
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }

        @Override
        public void startDecode(DecoderContext ctx) {
            element.requireSupportedAsArrayElement();
            ctx.push(new DecoderFrame.ArrayFrame(this));
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            element.requireSupportedAsArrayElement();
            ctx.push(new EncoderFrame.ArrayFrame(this));
        }
    }

    record Set(Type element, SizeConstraint size) implements Type {
        public Set {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(size, "size");
            if (element.containsStream()) {
                throw new IllegalArgumentException("Set element must not contain a Stream");
            }
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }

        @Override
        public void startDecode(DecoderContext ctx) {
            throw new UnsupportedOperationException("type not supported in iteration 5: " + this);
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            throw new UnsupportedOperationException("type not supported in iteration 5: " + this);
        }
    }

    record Stream(Type element) implements Type {
        public Stream {
            Objects.requireNonNull(element, "element");
            if (element.containsStream()) {
                throw new IllegalArgumentException("Stream element must not contain a Stream");
            }
        }
        @Override public boolean containsStream() { return true; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }

        @Override
        public void startDecode(DecoderContext ctx) {
            throw new UnsupportedOperationException("type not supported in iteration 5: " + this);
        }

        @Override
        public void startEncode(EncoderContext ctx) {
            throw new UnsupportedOperationException("type not supported in iteration 5: " + this);
        }
    }

    record Field(String name, Type type) {
        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    record Constructor(String name, List<Field> fields) {
        public Constructor {
            Objects.requireNonNull(name, "name");
            fields = List.copyOf(fields);
            requireStreamLastIfPresent(fields);
        }
    }

    private static void requireByteSize1to8(int n) {
        if (n < 1 || n > 8) {
            throw new IllegalArgumentException("byteSize must be 1..8: " + n);
        }
    }

    private static void requireStreamLastIfPresent(List<Field> fields) {
        for (int i = 0; i < fields.size() - 1; i++) {
            if (fields.get(i).type().containsStream()) {
                throw new IllegalArgumentException(
                        "stream-bearing field must be last; found at index " + i);
            }
        }
    }

    private static boolean anyFieldContainsStream(List<Field> fields) {
        for (Field f : fields) {
            if (f.type().containsStream()) {
                return true;
            }
        }
        return false;
    }

    private static OptionalInt sumFieldBitSizes(List<Field> fields) {
        int total = 0;
        for (Field f : fields) {
            OptionalInt sub = f.type().staticBitSize();
            if (sub.isEmpty()) {
                return OptionalInt.empty();
            }
            total += sub.getAsInt();
        }
        return OptionalInt.of(total);
    }

    private static void writeBigEndianBytes(BitWriter bits, long value, int byteSize) {
        byte[] out = new byte[byteSize];
        for (int i = 0; i < byteSize; i++) {
            out[i] = (byte) ((value >>> ((byteSize - 1 - i) * 8)) & 0xFF);
        }
        bits.writeBytes(out);
    }

    private static void writeVarInt(BitWriter bits, long value, int maxN) {
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
}
