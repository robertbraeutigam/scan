package com.vanillasource.scan.types.codec;

import java.util.List;
import java.util.Objects;

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
    default boolean containsStream() {
        if (this instanceof Stream) {
            return true;
        }
        if (this instanceof Struct s) {
            return anyFieldContainsStream(s.fields());
        }
        if (this instanceof Union u) {
            for (Constructor c : u.constructors()) {
                if (anyFieldContainsStream(c.fields())) {
                    return true;
                }
            }
            return false;
        }
        if (this instanceof Array a) {
            return a.element().containsStream();
        }
        if (this instanceof Set s) {
            return s.element().containsStream();
        }
        return false;
    }

    record Unit() implements Type {}

    record UnsignedInteger(int byteSize) implements Type {
        public UnsignedInteger {
            requireByteSize1to8(byteSize);
        }
    }

    record SignedInteger(int byteSize) implements Type {
        public SignedInteger {
            requireByteSize1to8(byteSize);
        }
    }

    record VariableLengthInteger(int maxBytes) implements Type {
        public VariableLengthInteger {
            requireByteSize1to8(maxBytes);
        }
    }

    record FloatingPoint(int byteSize) implements Type {
        public FloatingPoint {
            if (byteSize != 4 && byteSize != 8) {
                throw new IllegalArgumentException("FloatingPoint byteSize must be 4 or 8: " + byteSize);
            }
        }
    }

    record Struct(List<Field> fields) implements Type {
        public Struct {
            fields = List.copyOf(fields);
            requireStreamLastIfPresent(fields);
        }
    }

    record Union(List<Constructor> constructors) implements Type {
        public Union {
            constructors = List.copyOf(constructors);
            if (constructors.isEmpty()) {
                throw new IllegalArgumentException("Union must have at least one constructor");
            }
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
    }

    record Set(Type element, SizeConstraint size) implements Type {
        public Set {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(size, "size");
            if (element.containsStream()) {
                throw new IllegalArgumentException("Set element must not contain a Stream");
            }
        }
    }

    record Stream(Type element) implements Type {
        public Stream {
            Objects.requireNonNull(element, "element");
            if (element.containsStream()) {
                throw new IllegalArgumentException("Stream element must not contain a Stream");
            }
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
}
