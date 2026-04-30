package com.vanillasource.scan.types.types;

/** A named field inside a {@link ConstructorDefinition}. */
public record FieldDefinition(String name, Expression type) {
}
