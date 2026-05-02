package com.vanillasource.scan.types.types.value;

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
import com.vanillasource.scan.types.types.codec.LazyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lowers a {@link TypeDefinition} together with parameter {@link Value}
 * bindings into a runtime {@link Type} that the {@code types-codec} module
 * can drive.
 *
 * <p>Iteration 5 — adds a registry-aware overload that resolves
 * cross-{@link TypeDefinition} references. Names appearing in field-position
 * {@link Expression.Invocation}s that are neither parameter references nor
 * built-in library types (e.g. {@code Array}, {@code Option}) are looked up
 * in the supplied {@code Map<String, TypeDefinition>}; the looked-up
 * definition is recursively instantiated, with the invocation's arguments
 * lowered into {@link Value} bindings according to each parameter's declared
 * type. {@code Set(enumType)} now lowers to the codec's
 * {@link com.vanillasource.scan.types.codec.type.Set} given an enum-shaped
 * registry entry: no parameters, every constructor field-free; the codec's
 * {@code memberCount} is the constructor count.
 *
 * <p>Iteration 6 — full constraint coverage. Constraints are compile-time
 * metadata: the codec consumes nothing from constraints on numeric
 * primitives ({@code UnsignedInteger}, {@code SignedInteger},
 * {@code VariableLengthInteger}, {@code FloatingPoint} accept and discard
 * their second argument), and projects {@code Array}'s constraint argument
 * down to a {@code (min, max)} length envelope. Every spec'd
 * {@code Constraint} form is recognized — {@code All},
 * {@code MinInclusive}, {@code MinExclusive}, {@code MaxInclusive},
 * {@code MaxExclusive}, {@code Range}, {@code Values},
 * {@code MultipleOf}, {@code Union}, {@code Intersection}, {@code Not}.
 * Constraint arguments forwarded through user-type parameters
 * ({@code Constraint}-typed parameter binding) are materialized to
 * {@code Value.OfUnion} during argument resolution.
 *
 * <p>Iteration 7 — recursive {@link TypeDefinition} references via
 * {@link LazyType} forward-reference. Cycles are detected by an in-flight
 * map keyed on {@code (definition.name(), effectiveBindings)}; on
 * re-encounter the in-flight {@link LazyType} placeholder is returned and
 * later set when the outer instantiation completes. Direct self-recursion
 * ({@code List = Cons head tail | Nil}), recursion through {@code Array} /
 * {@code Stream}, mutual recursion across multiple registry entries, and
 * parametric recursion ({@code List(t) = Cons(t, List(t)) | Nil}) all work.
 * Recursions whose bindings differ at each level (genuine infinite
 * polymorphic recursion) still don't terminate — there's no value-level
 * fixpoint detection.
 */
public final class TypeInstantiation {
    private static final Set<Integer> FIXED_INT_BYTE_SIZES = Set.of(1, 2, 4, 8);
    private static final Set<Integer> VLI_BYTE_SIZES = Set.of(1, 2, 3, 4, 5, 6, 7, 8);
    private static final Set<Integer> FLOAT_BYTE_SIZES = Set.of(4, 8);

    private TypeInstantiation() {
    }

    public static Type instantiate(TypeDefinition definition, Map<String, Value> bindings) {
        return instantiate(definition, bindings, Map.of());
    }

    public static Type instantiate(
            TypeDefinition definition,
            Map<String, Value> bindings,
            Map<String, TypeDefinition> registry) {
        return instantiateInternal(definition, bindings, registry, new HashMap<>());
    }

    private static Type instantiateInternal(
            TypeDefinition definition,
            Map<String, Value> bindings,
            Map<String, TypeDefinition> registry,
            Map<InstantiationKey, LazyType> inFlight) {
        Map<String, ParameterDefinition> parametersByName = new LinkedHashMap<>();
        for (ParameterDefinition parameter : definition.parameters()) {
            parametersByName.put(parameter.name(), parameter);
        }

        for (String key : bindings.keySet()) {
            if (!parametersByName.containsKey(key)) {
                throw new IllegalArgumentException(
                        "binding '" + key + "' does not match any declared parameter of '"
                                + definition.name() + "'");
            }
        }

        Map<String, Value> effectiveBindings = new LinkedHashMap<>(bindings);
        for (ParameterDefinition parameter : definition.parameters()) {
            if (effectiveBindings.containsKey(parameter.name())) {
                continue;
            }
            Optional<Value> fromDefault = literalDefault(parameter);
            if (fromDefault.isPresent()) {
                effectiveBindings.put(parameter.name(), fromDefault.get());
                continue;
            }
            throw new IllegalArgumentException(
                    "missing binding for parameter '" + parameter.name() + "' of '"
                            + definition.name() + "'"
                            + (parameter.defaultValue().isPresent()
                                    ? " (non-literal default expressions are deferred to a later iteration)"
                                    : ""));
        }

        InstantiationKey key = new InstantiationKey(definition.name(), Map.copyOf(effectiveBindings));
        LazyType existing = inFlight.get(key);
        if (existing != null) {
            return existing;
        }

        LazyType placeholder = new LazyType();
        inFlight.put(key, placeholder);
        Type result;
        try {
            Context ctx = new Context(parametersByName.keySet(), effectiveBindings, registry, inFlight);
            result = resolveDefinition(definition, ctx);
        } finally {
            inFlight.remove(key);
        }
        placeholder.set(result);
        return result;
    }

    private static Type resolveDefinition(TypeDefinition definition, Context ctx) {
        List<ConstructorDefinition> ctors = definition.constructors();
        if (ctors.size() == 1) {
            ConstructorDefinition only = ctors.get(0);
            if (only.fields().size() == 1) {
                return resolveType(only.fields().get(0).type(), ctx);
            }
            return new Struct(resolveFields(only.fields(), ctx));
        }
        List<List<Type>> ctorFields = new ArrayList<>(ctors.size());
        for (ConstructorDefinition ctor : ctors) {
            ctorFields.add(resolveFields(ctor.fields(), ctx));
        }
        return new Union(ctorFields);
    }

    private static List<Type> resolveFields(List<FieldDefinition> fields, Context ctx) {
        List<Type> result = new ArrayList<>(fields.size());
        for (FieldDefinition field : fields) {
            result.add(resolveType(field.type(), ctx));
        }
        return result;
    }

    private static Type resolveType(Expression expression, Context ctx) {
        if (!(expression instanceof Expression.Invocation invocation)) {
            throw new IllegalArgumentException(
                    "type expression must be an Invocation, got "
                            + expression.getClass().getSimpleName());
        }

        String name = invocation.name();
        if (ctx.parameterNames().contains(name)) {
            if (!invocation.arguments().isEmpty()) {
                throw new IllegalArgumentException(
                        "parameter reference '" + name + "' must take no arguments");
            }
            Value bound = ctx.binding(name);
            if (!(bound instanceof Value.OfType ofType)) {
                throw new IllegalArgumentException(
                        "parameter '" + name + "' used in a Type position must be bound to OfType, got "
                                + bound.getClass().getSimpleName());
            }
            return ofType.type();
        }

        switch (name) {
            case "Unit":
                if (!invocation.arguments().isEmpty()) {
                    throw new IllegalArgumentException("Unit takes no arguments");
                }
                return new Unit();
            case "UnsignedInteger":
                return new UnsignedInteger(numericArgument(invocation, "UnsignedInteger",
                        FIXED_INT_BYTE_SIZES, ctx));
            case "SignedInteger":
                return new SignedInteger(numericArgument(invocation, "SignedInteger",
                        FIXED_INT_BYTE_SIZES, ctx));
            case "VariableLengthInteger":
                return new VariableLengthInteger(numericArgument(invocation, "VariableLengthInteger",
                        VLI_BYTE_SIZES, ctx));
            case "FloatingPoint":
                return new FloatingPoint(numericArgument(invocation, "FloatingPoint",
                        FLOAT_BYTE_SIZES, ctx));
            case "Array":
                return resolveArray(invocation, ctx);
            case "Stream":
                return resolveStream(invocation, ctx);
            case "Set":
                return resolveSet(invocation, ctx);
            default:
                return resolveRegistered(invocation, ctx);
        }
    }

    private static Type resolveRegistered(Expression.Invocation invocation, Context ctx) {
        TypeDefinition referenced = ctx.registry().get(invocation.name());
        if (referenced == null) {
            throw new IllegalArgumentException("unknown type '" + invocation.name() + "'");
        }
        if (invocation.arguments().size() > referenced.parameters().size()) {
            throw new IllegalArgumentException(
                    "type '" + referenced.name() + "' expects at most "
                            + referenced.parameters().size() + " argument(s), got "
                            + invocation.arguments().size());
        }
        Map<String, Value> subBindings = new LinkedHashMap<>();
        for (int i = 0; i < invocation.arguments().size(); i++) {
            ParameterDefinition param = referenced.parameters().get(i);
            Value value = resolveArgumentValue(param, invocation.arguments().get(i), ctx);
            subBindings.put(param.name(), value);
        }
        return instantiateInternal(referenced, subBindings, ctx.registry(), ctx.inFlight());
    }

    private static Value resolveArgumentValue(ParameterDefinition param, Expression arg, Context ctx) {
        Expression paramType = param.type();
        if (!(paramType instanceof Expression.Invocation pti)) {
            throw new IllegalArgumentException(
                    "parameter '" + param.name() + "' has non-Invocation type expression");
        }
        switch (pti.name()) {
            case "Type":
                return new Value.OfType(resolveType(arg, ctx));
            case "UnsignedInteger":
            case "SignedInteger":
            case "VariableLengthInteger":
                return new Value.OfInteger(resolveIntegerArgument(arg, param.name(), ctx));
            case "Constraint":
                return expressionToConstraintValue(arg, ctx, param.name());
            default:
                throw new UnsupportedOperationException(
                        "parameter type '" + pti.name() + "' for argument binding "
                                + "lands in a later iteration");
        }
    }

    private static long resolveIntegerArgument(Expression arg, String paramName, Context ctx) {
        if (arg instanceof Expression.IntegerLiteral lit) {
            return lit.value();
        }
        if (arg instanceof Expression.Invocation inv && ctx.parameterNames().contains(inv.name())) {
            if (!inv.arguments().isEmpty()) {
                throw new IllegalArgumentException(
                        "parameter reference '" + inv.name() + "' must take no arguments");
            }
            Value bound = ctx.binding(inv.name());
            if (!(bound instanceof Value.OfInteger oi)) {
                throw new IllegalArgumentException(
                        "parameter '" + inv.name() + "' used as integer argument for '"
                                + paramName + "' must be bound to OfInteger, got "
                                + bound.getClass().getSimpleName());
            }
            return oi.value();
        }
        throw new IllegalArgumentException(
                "argument for '" + paramName + "' must be an integer literal or parameter reference, got "
                        + arg.getClass().getSimpleName());
    }

    private static Type resolveSet(Expression.Invocation invocation, Context ctx) {
        if (invocation.arguments().size() != 1) {
            throw new IllegalArgumentException(
                    "Set expects exactly 1 argument, got " + invocation.arguments().size());
        }
        Expression arg = invocation.arguments().get(0);
        if (!(arg instanceof Expression.Invocation argInv)) {
            throw new IllegalArgumentException(
                    "Set element must be a type reference, got " + arg.getClass().getSimpleName());
        }
        if (!argInv.arguments().isEmpty()) {
            throw new IllegalArgumentException(
                    "Set element must reference an enum-shaped TypeDefinition with no parameters; got '"
                            + argInv.name() + "' applied with " + argInv.arguments().size() + " argument(s)");
        }
        TypeDefinition referenced = ctx.registry().get(argInv.name());
        if (referenced == null) {
            throw new IllegalArgumentException(
                    "Set element '" + argInv.name() + "' not found in type registry");
        }
        if (!referenced.parameters().isEmpty()) {
            throw new IllegalArgumentException(
                    "Set element '" + argInv.name() + "' must have no parameters, has "
                            + referenced.parameters().size());
        }
        for (ConstructorDefinition ctor : referenced.constructors()) {
            if (!ctor.fields().isEmpty()) {
                throw new IllegalArgumentException(
                        "Set element '" + argInv.name() + "' must be a no-field union; constructor '"
                                + ctor.name() + "' has " + ctor.fields().size() + " field(s)");
            }
        }
        return new com.vanillasource.scan.types.codec.type.Set(referenced.constructors().size());
    }

    private static Type resolveArray(Expression.Invocation invocation, Context ctx) {
        int argCount = invocation.arguments().size();
        if (argCount < 1 || argCount > 2) {
            throw new IllegalArgumentException(
                    "Array expects 1 or 2 arguments, got " + argCount);
        }
        Type element = resolveType(invocation.arguments().get(0), ctx);
        Bounds bounds = (argCount == 2)
                ? evaluateConstraint(invocation.arguments().get(1), ctx)
                : Bounds.ALL;
        return new Array(bounds.min(), bounds.max(), element);
    }

    private static Type resolveStream(Expression.Invocation invocation, Context ctx) {
        if (invocation.arguments().size() != 1) {
            throw new IllegalArgumentException(
                    "Stream expects exactly 1 argument, got " + invocation.arguments().size());
        }
        return new Stream(resolveType(invocation.arguments().get(0), ctx));
    }

    private static int numericArgument(
            Expression.Invocation invocation,
            String typeName,
            Set<Integer> allowedSizes,
            Context ctx) {
        int argCount = invocation.arguments().size();
        if (argCount < 1 || argCount > 2) {
            throw new IllegalArgumentException(
                    typeName + " expects 1 or 2 arguments (size, constraint?), got " + argCount);
        }
        Expression arg = invocation.arguments().get(0);
        long value;
        if (arg instanceof Expression.IntegerLiteral literal) {
            value = literal.value();
        } else if (arg instanceof Expression.Invocation reference
                && ctx.parameterNames().contains(reference.name())) {
            if (!reference.arguments().isEmpty()) {
                throw new IllegalArgumentException(
                        "parameter reference '" + reference.name() + "' must take no arguments");
            }
            Value bound = ctx.binding(reference.name());
            if (!(bound instanceof Value.OfInteger ofInteger)) {
                throw new IllegalArgumentException(
                        "parameter '" + reference.name() + "' used as a numeric size must be bound to OfInteger, got "
                                + bound.getClass().getSimpleName());
            }
            value = ofInteger.value();
        } else {
            throw new IllegalArgumentException(
                    typeName + " size must be an integer literal or parameter reference, got "
                            + arg.getClass().getSimpleName());
        }

        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || !allowedSizes.contains((int) value)) {
            throw new IllegalArgumentException(
                    typeName + " size " + value + " is not allowed; expected one of " + allowedSizes);
        }
        return (int) value;
    }

    private static Bounds evaluateConstraint(Expression expression, Context ctx) {
        Value constraint = expressionToConstraintValue(expression, ctx, "<inline>");
        return boundsFromConstraintValue(constraint);
    }

    /**
     * Materializes a constraint {@link Expression} into a {@code Value.OfUnion}
     * matching the {@code Constraint} ADT in TYPES.md. Used both for
     * evaluating Array size constraints inline and for binding
     * {@code Constraint}-typed parameter arguments at user-type call sites.
     *
     * <p>Recognizes parameter references whose binding is already
     * {@code OfUnion} (passes through), all 11 constraint constructors
     * ({@code All}, {@code MinInclusive}, {@code MinExclusive},
     * {@code MaxInclusive}, {@code MaxExclusive}, {@code Range},
     * {@code Values}, {@code MultipleOf}, {@code Union}, {@code Intersection},
     * {@code Not}), and the integer-literal sugar (a bare {@code n} is
     * treated as {@code Values(n)} per TYPES.md §"Where constraints are
     * interpreted").
     */
    private static Value expressionToConstraintValue(Expression expression, Context ctx, String paramName) {
        if (expression instanceof Expression.IntegerLiteral literal) {
            return new Value.OfUnion("Values",
                    new Value.OfStruct(Map.of("allowed",
                            new Value.OfArray(new Value.OfInteger(literal.value())))));
        }
        if (!(expression instanceof Expression.Invocation invocation)) {
            throw new IllegalArgumentException(
                    "constraint argument for '" + paramName
                            + "' must be an integer literal or Invocation, got "
                            + expression.getClass().getSimpleName());
        }
        if (ctx.parameterNames().contains(invocation.name())) {
            if (!invocation.arguments().isEmpty()) {
                throw new IllegalArgumentException(
                        "parameter reference '" + invocation.name() + "' must take no arguments");
            }
            Value bound = ctx.binding(invocation.name());
            if (!(bound instanceof Value.OfUnion ou)) {
                throw new IllegalArgumentException(
                        "parameter '" + invocation.name() + "' used as a Constraint must be bound to OfUnion, got "
                                + bound.getClass().getSimpleName());
            }
            return ou;
        }

        String name = invocation.name();
        switch (name) {
            case "All":
                if (!invocation.arguments().isEmpty()) {
                    throw new IllegalArgumentException("All takes no arguments");
                }
                return new Value.OfUnion("All", new Value.OfUnit());
            case "MinInclusive":
            case "MinExclusive":
            case "MaxInclusive":
            case "MaxExclusive":
                return new Value.OfUnion(name,
                        new Value.OfStruct(Map.of("bound",
                                new Value.OfInteger(constraintIntArgument(invocation, name)))));
            case "MultipleOf":
                return new Value.OfUnion(name,
                        new Value.OfStruct(Map.of("divisor",
                                new Value.OfInteger(constraintIntArgument(invocation, name)))));
            case "Range": {
                if (invocation.arguments().size() != 2) {
                    throw new IllegalArgumentException(
                            "Range expects exactly 2 arguments, got " + invocation.arguments().size());
                }
                long min = constraintIntLiteral(invocation.arguments().get(0), "Range.min");
                long max = constraintIntLiteral(invocation.arguments().get(1), "Range.max");
                return new Value.OfUnion("Range",
                        new Value.OfStruct(Map.of(
                                "min", new Value.OfInteger(min),
                                "max", new Value.OfInteger(max))));
            }
            case "Values": {
                if (invocation.arguments().isEmpty()) {
                    throw new IllegalArgumentException("Values must have at least one element");
                }
                List<Value> elems = new ArrayList<>(invocation.arguments().size());
                for (Expression arg : invocation.arguments()) {
                    elems.add(new Value.OfInteger(constraintIntLiteral(arg, "Values element")));
                }
                return new Value.OfUnion("Values",
                        new Value.OfStruct(Map.of("allowed", new Value.OfArray(elems))));
            }
            case "Union":
            case "Intersection": {
                if (invocation.arguments().size() != 2) {
                    throw new IllegalArgumentException(
                            name + " expects exactly 2 arguments, got " + invocation.arguments().size());
                }
                Value a = expressionToConstraintValue(invocation.arguments().get(0), ctx, paramName);
                Value b = expressionToConstraintValue(invocation.arguments().get(1), ctx, paramName);
                return new Value.OfUnion(name, new Value.OfStruct(Map.of("a", a, "b", b)));
            }
            case "Not": {
                if (invocation.arguments().size() != 1) {
                    throw new IllegalArgumentException(
                            "Not expects exactly 1 argument, got " + invocation.arguments().size());
                }
                Value inner = expressionToConstraintValue(invocation.arguments().get(0), ctx, paramName);
                return new Value.OfUnion("Not", new Value.OfStruct(Map.of("inner", inner)));
            }
            default:
                throw new IllegalArgumentException(
                        "'" + name + "' is not a Constraint form");
        }
    }

    /**
     * Projects a constraint {@code Value.OfUnion} (matching the
     * {@code Constraint} ADT) onto the {@code (min, max)} length envelope
     * the codec consumes for {@code Array}. Constraints are compile-time
     * only — this is the lossy projection used for wire-encoding decisions
     * (count prefix width, count subtraction).
     *
     * <p>{@code Not} projects to {@link Bounds#ALL} (no useful tightening
     * via complement). {@code MultipleOf} likewise projects to
     * {@link Bounds#ALL} (it constrains the value, not the length envelope).
     * Empty {@code Intersection} envelopes are rejected.
     */
    private static Bounds boundsFromConstraintValue(Value value) {
        if (!(value instanceof Value.OfUnion ou)) {
            throw new IllegalArgumentException(
                    "expected Constraint OfUnion, got " + value.getClass().getSimpleName());
        }
        switch (ou.constructor()) {
            case "All":
                return Bounds.ALL;
            case "MinInclusive": {
                long n = singleIntField(ou, "bound");
                return new Bounds(nonNegativeArrayBound(n, "MinInclusive bound"), Integer.MAX_VALUE);
            }
            case "MinExclusive": {
                long n = singleIntField(ou, "bound");
                int min = nonNegativeArrayBound(n, "MinExclusive bound");
                if (min == Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "MinExclusive(" + n + ") is empty for non-negative Array length");
                }
                return new Bounds(min + 1, Integer.MAX_VALUE);
            }
            case "MaxInclusive": {
                long n = singleIntField(ou, "bound");
                return new Bounds(0, nonNegativeArrayBound(n, "MaxInclusive bound"));
            }
            case "MaxExclusive": {
                long n = singleIntField(ou, "bound");
                int max = nonNegativeArrayBound(n, "MaxExclusive bound");
                if (max == 0) {
                    throw new IllegalArgumentException(
                            "MaxExclusive(0) is empty for non-negative Array length");
                }
                return new Bounds(0, max - 1);
            }
            case "Range": {
                Value.OfStruct s = unionContent(ou);
                long min = intField(s, "min");
                long max = intField(s, "max");
                int minInt = nonNegativeArrayBound(min, "Range.min");
                int maxInt = nonNegativeArrayBound(max, "Range.max");
                if (minInt > maxInt) {
                    throw new IllegalArgumentException(
                            "Range.min " + minInt + " is greater than Range.max " + maxInt);
                }
                return new Bounds(minInt, maxInt);
            }
            case "Values": {
                Value.OfStruct s = unionContent(ou);
                Value allowedV = s.fields().get("allowed");
                if (!(allowedV instanceof Value.OfArray oa)) {
                    throw new IllegalArgumentException(
                            "Values.allowed must be OfArray, got "
                                    + (allowedV == null ? "<missing>" : allowedV.getClass().getSimpleName()));
                }
                if (oa.elements().isEmpty()) {
                    throw new IllegalArgumentException("Values must have at least one element");
                }
                int min = Integer.MAX_VALUE;
                int max = 0;
                for (Value elem : oa.elements()) {
                    if (!(elem instanceof Value.OfInteger oi)) {
                        throw new IllegalArgumentException(
                                "Values element must be OfInteger, got " + elem.getClass().getSimpleName());
                    }
                    int n = nonNegativeArrayBound(oi.value(), "Values element");
                    if (n < min) min = n;
                    if (n > max) max = n;
                }
                return new Bounds(min, max);
            }
            case "MultipleOf": {
                long divisor = singleIntField(ou, "divisor");
                if (divisor <= 0) {
                    throw new IllegalArgumentException(
                            "MultipleOf divisor must be positive, got " + divisor);
                }
                return Bounds.ALL;
            }
            case "Union": {
                Value.OfStruct s = unionContent(ou);
                Bounds left = boundsFromConstraintValue(s.fields().get("a"));
                Bounds right = boundsFromConstraintValue(s.fields().get("b"));
                return new Bounds(Math.min(left.min(), right.min()), Math.max(left.max(), right.max()));
            }
            case "Intersection": {
                Value.OfStruct s = unionContent(ou);
                Bounds left = boundsFromConstraintValue(s.fields().get("a"));
                Bounds right = boundsFromConstraintValue(s.fields().get("b"));
                int min = Math.max(left.min(), right.min());
                int max = Math.min(left.max(), right.max());
                if (min > max) {
                    throw new IllegalArgumentException(
                            "Intersection has empty length envelope (" + min + ".." + max + ")");
                }
                return new Bounds(min, max);
            }
            case "Not":
                return Bounds.ALL;
            default:
                throw new IllegalArgumentException(
                        "'" + ou.constructor() + "' is not a Constraint form");
        }
    }

    private static Value.OfStruct unionContent(Value.OfUnion ou) {
        Value content = ou.content();
        if (!(content instanceof Value.OfStruct s)) {
            throw new IllegalArgumentException(
                    "Constraint '" + ou.constructor() + "' content must be OfStruct, got "
                            + content.getClass().getSimpleName());
        }
        return s;
    }

    private static long singleIntField(Value.OfUnion ou, String fieldName) {
        return intField(unionContent(ou), fieldName);
    }

    private static long intField(Value.OfStruct s, String fieldName) {
        Value v = s.fields().get(fieldName);
        if (!(v instanceof Value.OfInteger oi)) {
            throw new IllegalArgumentException(
                    "expected OfInteger field '" + fieldName + "', got "
                            + (v == null ? "<missing>" : v.getClass().getSimpleName()));
        }
        return oi.value();
    }

    private static long constraintIntArgument(Expression.Invocation invocation, String label) {
        if (invocation.arguments().size() != 1) {
            throw new IllegalArgumentException(
                    label + " expects exactly 1 argument, got " + invocation.arguments().size());
        }
        return constraintIntLiteral(invocation.arguments().get(0), label);
    }

    private static long constraintIntLiteral(Expression argument, String label) {
        if (!(argument instanceof Expression.IntegerLiteral literal)) {
            throw new IllegalArgumentException(
                    label + " must be an integer literal, got " + argument.getClass().getSimpleName());
        }
        return literal.value();
    }

    private static int nonNegativeArrayBound(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative, got " + value);
        }
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " " + value + " exceeds Integer.MAX_VALUE");
        }
        return (int) value;
    }

    private static Optional<Value> literalDefault(ParameterDefinition parameter) {
        return parameter.defaultValue().flatMap(expr -> {
            if (expr instanceof Expression.IntegerLiteral literal) {
                return Optional.of(new Value.OfInteger(literal.value()));
            }
            if (expr instanceof Expression.FloatLiteral literal) {
                return Optional.of(new Value.OfFloat(literal.value()));
            }
            if (expr instanceof Expression.StringLiteral literal) {
                return Optional.of(new Value.OfString(literal.value()));
            }
            return Optional.empty();
        });
    }

    private record Bounds(int min, int max) {
        static final Bounds ALL = new Bounds(0, Integer.MAX_VALUE);
    }

    private record Context(
            Set<String> parameterNames,
            Map<String, Value> bindings,
            Map<String, TypeDefinition> registry,
            Map<InstantiationKey, LazyType> inFlight) {
        Value binding(String name) {
            return bindings.get(name);
        }
    }

    private record InstantiationKey(String name, Map<String, Value> bindings) {}
}
