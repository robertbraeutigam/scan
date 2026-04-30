package com.vanillasource.scan.types.types;

import com.vanillasource.scan.types.types.ast.*;
import com.vanillasource.scan.types.types.codec.AstType;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public final class AstCodecTests {

    @Test
    public void roundTripsBoolean() {
        TypeDefinition booleanDef = new TypeDefinition(
                "Boolean",
                List.of(
                        new ConstructorDefinition("False"),
                        new ConstructorDefinition("True")));

        TypeDefinition restored = roundTrip(booleanDef);

        assertEquals(restored, booleanDef);
    }

    @Test
    public void roundTripsOption() {
        TypeDefinition optionDef = new TypeDefinition(
                "Option",
                List.of(new ParameterDefinition(
                        "contentType",
                        new Expression.Invocation("Type"))),
                List.of(
                        new ConstructorDefinition("None"),
                        new ConstructorDefinition(
                                "Some",
                                new FieldDefinition(
                                        "value",
                                        new Expression.Invocation("contentType")))));

        assertEquals(roundTrip(optionDef), optionDef);
    }

    @Test
    public void roundTripsParameterDefaultValue() {
        TypeDefinition stringDef = new TypeDefinition(
                "String",
                List.of(new ParameterDefinition(
                        "size",
                        new Expression.Invocation("Constraint"),
                        Optional.of(new Expression.Invocation("All")))),
                List.of(new ConstructorDefinition(
                        "String",
                        new FieldDefinition(
                                "bytes",
                                new Expression.Invocation("Array",
                                        new Expression.Invocation("Byte"),
                                        new Expression.Invocation("size"))))));

        assertEquals(roundTrip(stringDef), stringDef);
    }

    @Test
    public void roundTripsConstraintWithIntegerLiteral() {
        TypeDefinition def = new TypeDefinition(
                "BoundedString",
                List.of(),
                List.of(new ConstructorDefinition(
                        "BoundedString",
                        new FieldDefinition(
                                "bytes",
                                new Expression.Invocation("Array",
                                        new Expression.Invocation("Byte"),
                                        new Expression.Invocation("MaxInclusive",
                                                new Expression.IntegerLiteral(128)))))));

        assertEquals(roundTrip(def), def);
    }

    @Test
    public void roundTripsAllExpressionVariants() {
        TypeDefinition def = new TypeDefinition(
                "AllVariants",
                List.of(),
                List.of(new ConstructorDefinition(
                        "AllVariants",
                        new FieldDefinition("a", new Expression.IntegerLiteral(-42)),
                        new FieldDefinition("b", new Expression.FloatLiteral(3.5)),
                        new FieldDefinition("c", new Expression.StringLiteral("hello")),
                        new FieldDefinition("d", new Expression.Invocation("Nested",
                                new Expression.Invocation("Inner"),
                                new Expression.IntegerLiteral(0))))));

        assertEquals(roundTrip(def), def);
    }

    @Test
    public void roundTripsUtf8NameWithMultiByteChars() {
        TypeDefinition def = new TypeDefinition(
                "Über∑",
                List.of(),
                List.of(new ConstructorDefinition("Δ")));

        assertEquals(roundTrip(def), def);
    }

    @Test
    public void selfConsistentMetaRoundTrip() {
        TypeDefinition restored = roundTrip(AstType.META);

        assertEquals(restored, AstType.META);
    }

    @Test
    public void distinctTypeDefsProduceDistinctBytes() {
        TypeDefinition a = new TypeDefinition(
                "A", List.of(),
                List.of(new ConstructorDefinition("A")));
        TypeDefinition b = new TypeDefinition(
                "B", List.of(),
                List.of(new ConstructorDefinition("B")));

        assertNotEquals(a.serialize(), b.serialize());
    }

    private static TypeDefinition roundTrip(TypeDefinition def) {
        byte[] bytes = def.serialize();
        return TypeDefinition.deserialize(bytes);
    }
}
