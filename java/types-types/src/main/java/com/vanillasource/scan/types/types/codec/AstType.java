package com.vanillasource.scan.types.types.codec;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.FloatingPoint;
import com.vanillasource.scan.types.codec.type.SignedInteger;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import com.vanillasource.scan.types.types.ast.TypeDefinition;

import java.util.List;

/**
 * The meta-type — runtime {@link Type} corresponding to the AST in
 * TYPES.md §"Types Binary Representation". This schema is built into every
 * peer (it never travels on the wire); {@link AstCodec} drives user
 * {@link TypeDefinition} values through it.
 *
 * <p>Hand-built rather than derived from an AST self-description so the
 * codec has no chicken-and-egg dependency. Recursion in
 * {@code Expression.Invocation.arguments: Array(Expression)} is broken by a
 * {@link LazyType} forward-reference set after the union is built.
 */
public final class AstType {
    public static final Type META_TYPE = buildMetaType();

    private AstType() {
    }

    private static Type buildMetaType() {
        Type stringT = new Array(0, Integer.MAX_VALUE, new UnsignedInteger(1));

        LazyType expressionLazy = new LazyType();
        Type expressionT = new Union(List.of(
                List.of(stringT, new Array(0, Integer.MAX_VALUE, expressionLazy)),
                List.of(new SignedInteger(8)),
                List.of(new FloatingPoint(8)),
                List.of(stringT)));
        expressionLazy.set(expressionT);

        Type optionExpressionT = new Union(List.of(
                List.of(),
                List.of(expressionT)));

        Type fieldDefT = new Struct(stringT, expressionT);
        Type paramDefT = new Struct(stringT, expressionT, optionExpressionT);
        Type constructorDefT = new Struct(
                stringT,
                new Array(0, Integer.MAX_VALUE, fieldDefT));

        return new Struct(
                stringT,
                new Array(0, Integer.MAX_VALUE, paramDefT),
                new Array(1, Integer.MAX_VALUE, constructorDefT));
    }
}
