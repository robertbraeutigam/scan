package com.vanillasource.scan.types.types;

import java.util.List;

/** A named constructor (one of the arms of a {@link TypeDefinition}'s sum). */
public record ConstructorDefinition(String name, List<FieldDefinition> fields) {
    public ConstructorDefinition {
        fields = List.copyOf(fields);
    }

    public ConstructorDefinition(String name, FieldDefinition... fields) {
        this(name, List.of(fields));
    }
}
