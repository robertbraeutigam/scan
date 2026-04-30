package com.vanillasource.scan.types.types;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.types.ast.TypeDefinition;

import java.util.List;
import java.util.Map;

/**
 * Runtime materialization of a value in the type system, used as the
 * binding form for {@link TypeDefinition#toType(Map)}. Closed sum over
 * the kinds of values a type-parameter slot can hold:
 *
 * <ul>
 *   <li>{@link OfType} for {@code Type}-valued parameters (the dominant
 *       case in SCAN — every {@code Subscribe(keyType: Type)},
 *       {@code State(keyType, valueType)} etc.).</li>
 *   <li>{@link OfInteger} / {@link OfFloat} / {@link OfString} for the
 *       primitive literals of TYPES.md §"Types Binary Representation".</li>
 *   <li>{@link OfStruct} / {@link OfUnion} / {@link OfArray} / {@link OfUnit}
 *       for aggregate values — including the built-in {@code Constraint}
 *       ADT, encoded as {@code OfUnion("MaxInclusive", OfStruct({bound: OfInteger(128)}))}
 *       and similar.</li>
 * </ul>
 *
 * <p>This ADT is the binding API only; the codec read/write surface in
 * {@code types-codec} remains event-driven and never materializes a value.
 * Parameter bindings are bounded by construction (no {@code Stream}
 * binding makes sense), so the in-memory cost is acceptable here.
 */
public sealed interface Value {

    record OfType(Type type) implements Value {}

    record OfInteger(long value) implements Value {}

    record OfFloat(double value) implements Value {}

    record OfString(String value) implements Value {}

    record OfArray(List<Value> elements) implements Value {
        public OfArray {
            elements = List.copyOf(elements);
        }

        public OfArray(Value... elements) {
            this(List.of(elements));
        }
    }

    record OfStruct(Map<String, Value> fields) implements Value {
        public OfStruct {
            fields = Map.copyOf(fields);
        }
    }

    record OfUnion(String constructor, Value content) implements Value {}

    record OfUnit() implements Value {}
}
