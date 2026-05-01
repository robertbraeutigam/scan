package com.vanillasource.scan.types.types;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.FloatingPoint;
import com.vanillasource.scan.types.codec.type.SignedInteger;
import com.vanillasource.scan.types.codec.type.Stream;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.Unit;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import com.vanillasource.scan.types.codec.type.VariableLengthInteger;
import com.vanillasource.scan.types.types.ast.ConstructorDefinition;
import com.vanillasource.scan.types.types.ast.Expression;
import com.vanillasource.scan.types.types.ast.FieldDefinition;
import com.vanillasource.scan.types.types.ast.ParameterDefinition;
import com.vanillasource.scan.types.types.ast.TypeDefinition;
import com.vanillasource.scan.types.types.value.TypeInstantiation;
import com.vanillasource.scan.types.types.value.Value;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public final class TypeInstantiationTests {

    @Test
    public void instantiatesUnitAlias() {
        Type result = TypeInstantiation.instantiate(alias("MyUnit", invocation("Unit")), Map.of());

        assertTrue(result instanceof Unit, "expected Unit, got " + result.getClass());
    }

    @Test
    public void instantiatesUnsignedIntegerAlias() {
        Type result = TypeInstantiation.instantiate(
                alias("Word", invocation("UnsignedInteger", new Expression.IntegerLiteral(2))),
                Map.of());

        assertTrue(result instanceof UnsignedInteger, "expected UnsignedInteger, got " + result.getClass());
        assertTrue(result.isPrimitiveByte() == false, "byteSize 2 must not be primitive-byte");
    }

    @Test
    public void instantiatesByteAliasAsOneBytePrimitive() {
        Type result = TypeInstantiation.instantiate(
                alias("Byte", invocation("UnsignedInteger", new Expression.IntegerLiteral(1))),
                Map.of());

        assertTrue(result instanceof UnsignedInteger);
        assertTrue(result.isPrimitiveByte());
    }

    @Test
    public void instantiatesSignedIntegerAlias() {
        Type result = TypeInstantiation.instantiate(
                alias("Int", invocation("SignedInteger", new Expression.IntegerLiteral(4))),
                Map.of());

        assertTrue(result instanceof SignedInteger);
    }

    @Test
    public void instantiatesVariableLengthIntegerAlias() {
        Type result = TypeInstantiation.instantiate(
                alias("Vli", invocation("VariableLengthInteger", new Expression.IntegerLiteral(5))),
                Map.of());

        assertTrue(result instanceof VariableLengthInteger);
    }

    @Test
    public void instantiatesFloatingPointAlias() {
        Type result = TypeInstantiation.instantiate(
                alias("Double", invocation("FloatingPoint", new Expression.IntegerLiteral(8))),
                Map.of());

        assertTrue(result instanceof FloatingPoint);
    }

    @Test
    public void substitutesIntegerParameterIntoSizeSlot() {
        TypeDefinition def = new TypeDefinition(
                "Bag",
                List.of(new ParameterDefinition("size", invocation("UnsignedInteger"))),
                List.of(new ConstructorDefinition("Bag",
                        new FieldDefinition("value",
                                invocation("UnsignedInteger", invocation("size"))))));

        Type result = TypeInstantiation.instantiate(def, Map.of("size", new Value.OfInteger(4)));

        assertTrue(result instanceof UnsignedInteger);
    }

    @Test
    public void substitutesTypeParameterIntoFieldType() {
        TypeDefinition def = new TypeDefinition(
                "Wrap",
                List.of(new ParameterDefinition("inner", invocation("Type"))),
                List.of(new ConstructorDefinition("Wrap",
                        new FieldDefinition("value", invocation("inner")))));

        UnsignedInteger bound = new UnsignedInteger(2);
        Type result = TypeInstantiation.instantiate(def, Map.of("inner", new Value.OfType(bound)));

        assertSame(result, bound);
    }

    @Test
    public void instantiatesMultiFieldStruct() {
        TypeDefinition def = new TypeDefinition(
                "Pair",
                List.of(),
                List.of(new ConstructorDefinition("Pair",
                        new FieldDefinition("a", invocation("UnsignedInteger", new Expression.IntegerLiteral(1))),
                        new FieldDefinition("b", invocation("Unit")))));

        Type result = TypeInstantiation.instantiate(def, Map.of());

        assertTrue(result instanceof Struct, "expected Struct, got " + result.getClass());
    }

    @Test
    public void instantiatesZeroFieldStruct() {
        TypeDefinition def = new TypeDefinition(
                "Empty",
                List.of(),
                List.of(new ConstructorDefinition("Empty")));

        Type result = TypeInstantiation.instantiate(def, Map.of());

        assertTrue(result instanceof Struct, "expected Struct, got " + result.getClass());
    }

    @Test
    public void instantiatesMultiConstructorUnion() {
        TypeDefinition def = new TypeDefinition(
                "Bool",
                List.of(
                        new ConstructorDefinition("False"),
                        new ConstructorDefinition("True")));

        Type result = TypeInstantiation.instantiate(def, Map.of());

        assertTrue(result instanceof Union, "expected Union, got " + result.getClass());
        assertEquals(((Union) result).discriminatorBits(), 1);
    }

    @Test
    public void instantiatesUnionWithDataConstructors() {
        TypeDefinition def = new TypeDefinition(
                "Maybe",
                List.of(new ParameterDefinition("inner", invocation("Type"))),
                List.of(
                        new ConstructorDefinition("None"),
                        new ConstructorDefinition("Some",
                                new FieldDefinition("value", invocation("inner")))));

        Type result = TypeInstantiation.instantiate(def, Map.of("inner", new Value.OfType(new Unit())));

        assertTrue(result instanceof Union);
    }

    @Test
    public void instantiatesArrayWithoutSize() {
        Type result = TypeInstantiation.instantiate(
                alias("Bytes", invocation("Array",
                        invocation("UnsignedInteger", new Expression.IntegerLiteral(1)))),
                Map.of());

        assertTrue(result instanceof Array);
    }

    @Test
    public void instantiatesArrayWithFixedCount() {
        Type result = TypeInstantiation.instantiate(
                alias("Hours", invocation("Array",
                        invocation("UnsignedInteger", new Expression.IntegerLiteral(2)),
                        new Expression.IntegerLiteral(24))),
                Map.of());

        assertTrue(result instanceof Array);
    }

    @Test
    public void instantiatesArrayWithMaxInclusive() {
        Type result = TypeInstantiation.instantiate(
                alias("Handshake", invocation("Array",
                        invocation("UnsignedInteger", new Expression.IntegerLiteral(1)),
                        invocation("MaxInclusive", new Expression.IntegerLiteral(128)))),
                Map.of());

        assertTrue(result instanceof Array);
    }

    @Test
    public void instantiatesArrayWithMinInclusive() {
        Type result = TypeInstantiation.instantiate(
                alias("AtLeastOne", invocation("Array",
                        invocation("Unit"),
                        invocation("MinInclusive", new Expression.IntegerLiteral(1)))),
                Map.of());

        assertTrue(result instanceof Array);
    }

    @Test
    public void instantiatesArrayWithRange() {
        Type result = TypeInstantiation.instantiate(
                alias("Window", invocation("Array",
                        invocation("Unit"),
                        invocation("Range",
                                new Expression.IntegerLiteral(1),
                                new Expression.IntegerLiteral(64)))),
                Map.of());

        assertTrue(result instanceof Array);
    }

    @Test
    public void instantiatesStream() {
        Type result = TypeInstantiation.instantiate(
                alias("Samples", invocation("Stream",
                        invocation("FloatingPoint", new Expression.IntegerLiteral(4)))),
                Map.of());

        assertTrue(result instanceof Stream);
    }

    @Test
    public void honoursLiteralIntegerDefault() {
        TypeDefinition def = new TypeDefinition(
                "Buffer",
                List.of(new ParameterDefinition("size", invocation("UnsignedInteger"),
                        Optional.of(new Expression.IntegerLiteral(2)))),
                List.of(new ConstructorDefinition("Buffer",
                        new FieldDefinition("value",
                                invocation("UnsignedInteger", invocation("size"))))));

        Type result = TypeInstantiation.instantiate(def, Map.of());

        assertTrue(result instanceof UnsignedInteger);
    }

    @Test
    public void rejectsUnknownBindingKey() {
        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("MyUnit", invocation("Unit")),
                        Map.of("nope", new Value.OfInteger(0))));
        assertTrue(ex.getMessage().contains("nope"), ex.getMessage());
    }

    @Test
    public void rejectsMissingBinding() {
        TypeDefinition def = new TypeDefinition(
                "Bag",
                List.of(new ParameterDefinition("size", invocation("UnsignedInteger"))),
                List.of(new ConstructorDefinition("Bag",
                        new FieldDefinition("value",
                                invocation("UnsignedInteger", invocation("size"))))));

        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(def, Map.of()));
        assertTrue(ex.getMessage().contains("size"), ex.getMessage());
    }

    @Test
    public void rejectsNonLiteralDefault() {
        TypeDefinition def = new TypeDefinition(
                "T",
                List.of(new ParameterDefinition("c", invocation("Constraint"),
                        Optional.of(invocation("All")))),
                List.of(new ConstructorDefinition("T",
                        new FieldDefinition("v", invocation("Unit")))));

        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(def, Map.of()));
        assertTrue(ex.getMessage().contains("default"), ex.getMessage());
    }

    @Test
    public void rejectsBadByteSize() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("UnsignedInteger", new Expression.IntegerLiteral(3))),
                        Map.of()));
    }

    @Test
    public void rejectsBadFloatSize() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("FloatingPoint", new Expression.IntegerLiteral(2))),
                        Map.of()));
    }

    @Test
    public void rejectsConstraintArgumentForNumericPrimitive() {
        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("UnsignedInteger",
                                new Expression.IntegerLiteral(1),
                                invocation("All"))),
                        Map.of()));
        assertTrue(ex.getMessage().contains("1 argument"), ex.getMessage());
    }

    @Test
    public void rejectsUnknownTypeName() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("NotAType")),
                        Map.of()));
    }

    @Test
    public void rejectsTypeParameterBoundToWrongValueKind() {
        TypeDefinition def = new TypeDefinition(
                "Wrap",
                List.of(new ParameterDefinition("inner", invocation("Type"))),
                List.of(new ConstructorDefinition("Wrap",
                        new FieldDefinition("value", invocation("inner")))));

        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(def, Map.of("inner", new Value.OfInteger(1))));
    }

    @Test
    public void rejectsSizeParameterBoundToWrongValueKind() {
        TypeDefinition def = new TypeDefinition(
                "Bag",
                List.of(new ParameterDefinition("size", invocation("UnsignedInteger"))),
                List.of(new ConstructorDefinition("Bag",
                        new FieldDefinition("value",
                                invocation("UnsignedInteger", invocation("size"))))));

        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        def, Map.of("size", new Value.OfType(new Unit()))));
    }

    @Test
    public void instantiatesSetOverRegistryEnum() {
        TypeDefinition color = new TypeDefinition("Color", List.of(),
                List.of(new ConstructorDefinition("Red"),
                        new ConstructorDefinition("Green"),
                        new ConstructorDefinition("Blue")));

        Type result = TypeInstantiation.instantiate(
                alias("X", invocation("Set", invocation("Color"))),
                Map.of(),
                Map.of("Color", color));

        assertTrue(result instanceof com.vanillasource.scan.types.codec.type.Set);
        assertEquals(((com.vanillasource.scan.types.codec.type.Set) result).memberCount(), 3);
    }

    @Test
    public void rejectsSetWhenElementMissingFromRegistry() {
        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Set", invocation("Color"))),
                        Map.of()));
        assertTrue(ex.getMessage().contains("Color"), ex.getMessage());
    }

    @Test
    public void rejectsSetOverParametricElement() {
        TypeDefinition pColor = new TypeDefinition("PColor",
                List.of(new ParameterDefinition("t", invocation("Type"))),
                List.of(new ConstructorDefinition("Only")));

        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Set", invocation("PColor"))),
                        Map.of(),
                        Map.of("PColor", pColor)));
        assertTrue(ex.getMessage().contains("parameters"), ex.getMessage());
    }

    @Test
    public void rejectsSetOverElementWithFieldedConstructor() {
        TypeDefinition fielded = new TypeDefinition("Mixed", List.of(),
                List.of(new ConstructorDefinition("Empty"),
                        new ConstructorDefinition("WithValue",
                                new FieldDefinition("v", invocation("Unit")))));

        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Set", invocation("Mixed"))),
                        Map.of(),
                        Map.of("Mixed", fielded)));
        assertTrue(ex.getMessage().contains("WithValue"), ex.getMessage());
    }

    @Test
    public void rejectsSetWithMultipleArguments() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Set",
                                invocation("Color"),
                                invocation("Color"))),
                        Map.of()));
    }

    @Test
    public void rejectsSetArgumentThatIsLiteral() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Set", new Expression.IntegerLiteral(3))),
                        Map.of()));
    }

    @Test
    public void rejectsSetArgumentApplyingArguments() {
        TypeDefinition color = new TypeDefinition("Color", List.of(),
                List.of(new ConstructorDefinition("Red"),
                        new ConstructorDefinition("Green")));

        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Set",
                                invocation("Color", invocation("Unit")))),
                        Map.of(),
                        Map.of("Color", color)));
        assertTrue(ex.getMessage().contains("Color"), ex.getMessage());
    }

    @Test
    public void resolvesUserTypeReferenceFromRegistry() {
        TypeDefinition pair = new TypeDefinition("Pair", List.of(),
                List.of(new ConstructorDefinition("Pair",
                        new FieldDefinition("a", invocation("Unit")),
                        new FieldDefinition("b", invocation("Unit")))));

        Type result = TypeInstantiation.instantiate(
                alias("X", invocation("Array",
                        invocation("Pair"),
                        new Expression.IntegerLiteral(5))),
                Map.of(),
                Map.of("Pair", pair));

        assertTrue(result instanceof Array);
    }

    @Test
    public void resolvesParametricUserTypeWithTypeArgument() {
        TypeDefinition myOption = new TypeDefinition("MyOption",
                List.of(new ParameterDefinition("inner", invocation("Type"))),
                List.of(new ConstructorDefinition("None"),
                        new ConstructorDefinition("Some",
                                new FieldDefinition("value", invocation("inner")))));

        Type result = TypeInstantiation.instantiate(
                alias("X", invocation("MyOption",
                        invocation("UnsignedInteger", new Expression.IntegerLiteral(1)))),
                Map.of(),
                Map.of("MyOption", myOption));

        assertTrue(result instanceof Union);
    }

    @Test
    public void forwardsOuterParameterIntoUserTypeArgument() {
        TypeDefinition myOption = new TypeDefinition("MyOption",
                List.of(new ParameterDefinition("inner", invocation("Type"))),
                List.of(new ConstructorDefinition("None"),
                        new ConstructorDefinition("Some",
                                new FieldDefinition("value", invocation("inner")))));
        TypeDefinition outer = new TypeDefinition("Outer",
                List.of(new ParameterDefinition("t", invocation("Type"))),
                List.of(new ConstructorDefinition("Outer",
                        new FieldDefinition("opt",
                                invocation("MyOption", invocation("t"))))));

        Type result = TypeInstantiation.instantiate(
                outer,
                Map.of("t", new Value.OfType(new Unit())),
                Map.of("MyOption", myOption));

        assertTrue(result instanceof Union);
    }

    @Test
    public void resolvesUserTypeWithIntegerArgument() {
        TypeDefinition myWord = new TypeDefinition("MyWord",
                List.of(new ParameterDefinition("size", invocation("UnsignedInteger"))),
                List.of(new ConstructorDefinition("MyWord",
                        new FieldDefinition("value",
                                invocation("UnsignedInteger", invocation("size"))))));

        Type result = TypeInstantiation.instantiate(
                alias("X", invocation("MyWord", new Expression.IntegerLiteral(2))),
                Map.of(),
                Map.of("MyWord", myWord));

        assertTrue(result instanceof UnsignedInteger);
    }

    @Test
    public void rejectsTooManyArgumentsToUserType() {
        TypeDefinition empty = new TypeDefinition("Empty", List.of(),
                List.of(new ConstructorDefinition("Empty")));

        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Empty", invocation("Unit"))),
                        Map.of(),
                        Map.of("Empty", empty)));
    }

    @Test
    public void rejectsArrayWithoutArguments() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Array")),
                        Map.of()));
    }

    @Test
    public void rejectsArrayWithTooManyArguments() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Array",
                                invocation("Unit"),
                                invocation("All"),
                                invocation("All"))),
                        Map.of()));
    }

    @Test
    public void rejectsStreamWithoutArguments() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Stream")),
                        Map.of()));
    }

    @Test
    public void rejectsNegativeArrayBound() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Array",
                                invocation("Unit"),
                                invocation("MaxInclusive", new Expression.IntegerLiteral(-1)))),
                        Map.of()));
    }

    @Test
    public void rejectsRangeWithMinGreaterThanMax() {
        expectThrows(IllegalArgumentException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Array",
                                invocation("Unit"),
                                invocation("Range",
                                        new Expression.IntegerLiteral(10),
                                        new Expression.IntegerLiteral(2)))),
                        Map.of()));
    }

    @Test
    public void rejectsUnsupportedConstraintForm() {
        UnsupportedOperationException ex = expectThrows(UnsupportedOperationException.class,
                () -> TypeInstantiation.instantiate(
                        alias("X", invocation("Array",
                                invocation("Unit"),
                                invocation("MultipleOf", new Expression.IntegerLiteral(3)))),
                        Map.of()));
        assertTrue(ex.getMessage().contains("MultipleOf"), ex.getMessage());
    }

    private static Expression.Invocation invocation(String name, Expression... args) {
        return new Expression.Invocation(name, args);
    }

    private static TypeDefinition alias(String name, Expression rhs) {
        return new TypeDefinition(name, List.of(),
                List.of(new ConstructorDefinition(name,
                        new FieldDefinition("value", rhs))));
    }
}
