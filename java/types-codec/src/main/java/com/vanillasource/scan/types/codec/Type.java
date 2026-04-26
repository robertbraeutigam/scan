package com.vanillasource.scan.types.codec;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Static description of a SCAN value's shape. Pure data; encoders and decoders walk
 * a {@code Type} in lockstep with the bytes on the wire.
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

    record Unit() implements Type {
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(0); }
    }

    record UnsignedInteger(int byteSize) implements Type {
        public UnsignedInteger {
            requireByteSize1to8(byteSize);
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
    }

    record SignedInteger(int byteSize) implements Type {
        public SignedInteger {
            requireByteSize1to8(byteSize);
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
    }

    record VariableLengthInteger(int maxBytes) implements Type {
        public VariableLengthInteger {
            requireByteSize1to8(maxBytes);
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.empty(); }
    }

    record FloatingPoint(int byteSize) implements Type {
        public FloatingPoint {
            if (byteSize != 4 && byteSize != 8) {
                throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
            }
        }
        @Override public boolean containsStream() { return false; }
        @Override public OptionalInt staticBitSize() { return OptionalInt.of(byteSize * 8); }
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
}
