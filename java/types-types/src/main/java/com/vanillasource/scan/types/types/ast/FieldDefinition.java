package com.vanillasource.scan.types.types.ast;

/** A named field inside a {@link ConstructorDefinition}. */
public record FieldDefinition(String name, Expression type) {
}
