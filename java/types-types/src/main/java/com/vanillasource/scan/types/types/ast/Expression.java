package com.vanillasource.scan.types.types.ast;

import java.util.List;

/**
 * AST node for the right-hand side of a field type, parameter type, or
 * default-value expression. Mirrors TYPES.md §"Types Binary Representation":
 *
 * <pre>
 * Expression = Invocation { name: String, arguments: Array(Expression) }
 *            | Integer    { value: SignedInteger(8) }
 *            | Float      { value: FloatingPoint(8) }
 *            | StringLit  { value: String }
 * </pre>
 */
public sealed interface Expression {

    record Invocation(String name, List<Expression> arguments) implements Expression {
        public Invocation {
            arguments = List.copyOf(arguments);
        }

        public Invocation(String name, Expression... arguments) {
            this(name, List.of(arguments));
        }
    }

    record IntegerLiteral(long value) implements Expression {}

    record FloatLiteral(double value) implements Expression {}

    record StringLiteral(String value) implements Expression {}
}
