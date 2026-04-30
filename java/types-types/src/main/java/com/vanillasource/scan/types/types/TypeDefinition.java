package com.vanillasource.scan.types.types;

import java.util.List;

/**
 * Top-level AST node — the wire form of a type. Mirrors TYPES.md
 * §"Types Binary Representation":
 *
 * <pre>
 * TypeDefinition {
 *     name: String,
 *     parameters: Array(ParameterDefinition),
 *     union: Array(ConstructorDefinition, size = MinInclusive(1))
 * }
 * </pre>
 *
 * <p>A definition with one constructor is struct-shaped; with multiple
 * constructors, union-shaped. The split is emergent from
 * {@code constructors.size()} — there is no separate {@code Struct} or
 * {@code Union} top-level form, matching the spec.
 */
public record TypeDefinition(
        String name,
        List<ParameterDefinition> parameters,
        List<ConstructorDefinition> constructors) {

    public TypeDefinition {
        if (constructors.isEmpty()) {
            throw new IllegalArgumentException(
                    "TypeDefinition '" + name + "' must declare at least one constructor");
        }
        parameters = List.copyOf(parameters);
        constructors = List.copyOf(constructors);
    }

    public TypeDefinition(String name, List<ConstructorDefinition> constructors) {
        this(name, List.of(), constructors);
    }
}
