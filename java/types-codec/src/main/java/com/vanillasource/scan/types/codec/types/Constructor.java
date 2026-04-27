package com.vanillasource.scan.types.codec.types;

import java.util.List;
import java.util.Objects;

public record Constructor(String name, List<Field> fields) {
    public Constructor {
        Objects.requireNonNull(name, "name");
        fields = List.copyOf(fields);
        Field.requireStreamLastIfPresent(fields);
    }
}
