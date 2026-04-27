package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.Type;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record Field(String name, Type type) {
    public Field {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    static void requireStreamLastIfPresent(List<Field> fields) {
        for (int i = 0; i < fields.size() - 1; i++) {
            if (fields.get(i).type().containsStream()) {
                throw new IllegalArgumentException(
                        "stream-bearing field must be last; found at index " + i);
            }
        }
    }

    static boolean anyContainsStream(List<Field> fields) {
        for (Field f : fields) {
            if (f.type().containsStream()) {
                return true;
            }
        }
        return false;
    }

    static OptionalInt sumBitSizes(List<Field> fields) {
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
