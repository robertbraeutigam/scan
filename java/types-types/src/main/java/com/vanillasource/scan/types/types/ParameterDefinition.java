package com.vanillasource.scan.types.types;

import java.util.Optional;

/**
 * A type-level parameter declared on a {@link TypeDefinition}. The
 * parameter's own type (e.g. {@code Type}, {@code Constraint},
 * {@code UnsignedInteger}) is itself an {@link Expression}.
 */
public record ParameterDefinition(String name, Expression type, Optional<Expression> defaultValue) {

    public ParameterDefinition(String name, Expression type) {
        this(name, type, Optional.empty());
    }
}
