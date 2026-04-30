package com.vanillasource.scan.types.types;

import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

public final class AstSmokeTests {

    @Test
    public void buildsOptionTypeDefinition() {
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

        assertEquals(optionDef.name(), "Option");
        assertEquals(optionDef.parameters().size(), 1);
        assertEquals(optionDef.constructors().size(), 2);
        assertEquals(optionDef.constructors().get(1).fields().size(), 1);
    }

    @Test
    public void buildsBooleanTypeDefinition() {
        TypeDefinition booleanDef = new TypeDefinition(
                "Boolean",
                List.of(
                        new ConstructorDefinition("False"),
                        new ConstructorDefinition("True")));

        assertEquals(booleanDef.parameters(), List.of());
        assertEquals(booleanDef.constructors().size(), 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsTypeDefinitionWithNoConstructors() {
        new TypeDefinition("Empty", List.of(), List.of());
    }

    @Test
    public void parameterDefaultDefaultsToEmpty() {
        ParameterDefinition p = new ParameterDefinition(
                "size",
                new Expression.Invocation("Constraint"));

        assertEquals(p.defaultValue(), Optional.empty());
    }

    @Test
    public void invocationVarargsConstructorMatchesListForm() {
        Expression.Invocation a = new Expression.Invocation("Array",
                new Expression.Invocation("Byte"),
                new Expression.IntegerLiteral(32));
        Expression.Invocation b = new Expression.Invocation("Array", List.of(
                new Expression.Invocation("Byte"),
                new Expression.IntegerLiteral(32)));

        assertEquals(a, b);
    }

    @Test
    public void buildsValueOfEachKind() {
        UnsignedInteger byteType = new UnsignedInteger(1);
        Value.OfType vType = new Value.OfType(byteType);
        assertSame(vType.type(), byteType);

        Value vInt = new Value.OfInteger(42);
        Value vFloat = new Value.OfFloat(3.14);
        Value vStr = new Value.OfString("hello");
        Value vUnit = new Value.OfUnit();

        Value vMaxInclusive = new Value.OfUnion(
                "MaxInclusive",
                new Value.OfStruct(Map.of("bound", new Value.OfInteger(128))));

        Value vArr = new Value.OfArray(vInt, vInt, vInt);

        assertEquals(((Value.OfInteger) vInt).value(), 42L);
        assertEquals(((Value.OfFloat) vFloat).value(), 3.14);
        assertEquals(((Value.OfString) vStr).value(), "hello");
        assertEquals(((Value.OfUnion) vMaxInclusive).constructor(), "MaxInclusive");
        assertEquals(((Value.OfArray) vArr).elements().size(), 3);
        assertEquals(vUnit, new Value.OfUnit());
    }
}
