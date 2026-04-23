# SCAN Type System

*Draft Version*

The SCAN Type System is a protocol description language and related tools created for SCAN.
Other than supporting some peculiar features maybe not found in other protocol description languages,
it is a generic system, independent of the SCAN protocol itself.

## Components

This type system consist of following parts:
* A human-readable textual representation
* A binary representation of types
* A binary representation of values
* An algorithm to determine if a type defines a subset of values as another type
* A human-readable transformation language to transform values to other values
* A binary representation of a transformation

## Type System Textual Language

A type definition has the form:

```
<name>(parameters) = <constructor 1> | ... | <constructor N>
```

A constructor is one of:

* a **bare identifier** carrying no data (e.g. `None`), or
* a **named structure** — a sequence of name/type pairs (e.g. `Some { value: T }`).

A type may list any number of constructors separated by `|`. If a type has exactly one constructor, the `<name> =` prefix may be omitted; the type then takes the constructor's name. For example:

```
LogLine {
   severity: Severity,
   time: Timestamp,
   line: String
}
```

is shorthand for `LogLine = LogLine { severity: Severity, time: Timestamp, line: String }`.

Types and constructors live in separate namespaces. A constructor name is a tag used to discriminate within its type and cannot be used on its own as a type elsewhere.

### Built-in "Primitive" Types

This type system defines these built-in types:

* Unit
* FloatingPoint(sizeInBytes)
* UnsignedInteger(sizeInBytes)
* SignedInteger(sizeInBytes)
* VariableLengthInteger(maxSizeInBytes)

`Unit` is a type with exactly one value and carries no other data.

The `FloatingPoint` type has a size parameter, which is either 4 or 8, corresponding to the standard IEEE float and double.

The `UnsignedInteger` and `SignedInteger` numbers may have sizes of 1, 2, 4 or 8, corresponding to the usual number types:
byte, word, int, long.

The `VariableLengthInteger` is a number stored as a variable number of bytes. On each byte except the last the highest bit indicates that a byte still follows, which means the last byte may use the high bit for representing the value itself — it does not have to be 0. This is useful when lower numbers are much more likely, resulting in more efficient packing. The `maxSizeInBytes` parameter bounds the wire size (1 to 8 inclusive); the actual number of bytes used varies by value. A VLI with `maxSizeInBytes=1` is just a normal unsigned byte.

All values are stored in big-endian ordering.

### Built-In "Aggregate" Types

The type system defines following built-in aggregate types:
* Array
* DynamicArray
* Set
* Stream

An `Array` is an ordered aggregation of multiple values of the given type with a compile-time fixed length. It is written like this:

```
HourlyMeasurements = Array(Measurement, size=24)
```

The `Array` type is useful, because the number of items do not need to be written to the wire. However, if the number of items is not known a-priori there's the `DynamicArray`, which can have a different size in each value.

```
Events = DynamicArray(Event)
```

The downside is, that the length needs to be written onto the wire.

A `Set` is an **unordered** collection of **distinct** values of the given type. Any element type is allowed. When the element type contains no data (thus whether it's present in the set can be described by a single bit), a `Set` wire-encodes naturally as a bitmask; otherwise it is encoded as a deduplicated variable-length sequence. The concrete encoding is defined in *Values Binary Representation* below.

```
Flags = IPv4 | IPv6

EnabledProtocols = Set(Flags)
```

A `Stream` is a potentially infinite sequence of values of the given type — for example, a live video stream:

```
VideoContent = Stream(Byte)
```

Because a `Stream` has no length terminator on the wire, nothing can follow it. Consequently **a type may contain at most one `Stream`, transitively** — whether on the top level or any sub-structure.

### Type Parameters

Types can take parameters, which may be values or other types. Parameters are declared by name with a type, and may have defaults:

```
Measurement(unit: String) { value: Double }

Option(contentType: Type) = None | Some { value: contentType }

Buffer(size: UnsignedInteger = 256) { data: Array(Byte, size) }
```

At the call site, arguments may be passed positionally or by name:

```
HourlyMeasurements = Array(Measurement, size=24)
```

When defaults are present, positional arguments bind left-to-right; to override a later default without restating the earlier ones, switch to the named form.

A parameter whose value is bound at definition time (e.g. `unit` in `Measurement`) is a compile-time constant and is **not** encoded with the value on the wire.

### Type Aliases

An alias gives a new name to an existing type without introducing a new constructor:

```
<name> = <type reference>
```

The right-hand side is a single type reference. Examples:

```
Word     = UnsignedInteger(2)
Bytes    = DynamicArray(Byte)
PortNumber = UnsignedInteger(2, constraint = Range(min = 1, max = 65535))
```

(The `Byte`, `Long`, `Double`, and `String` aliases used elsewhere in this document are defined once and for all in *Library Types* below.)

An alias is distinguished from a type definition by its right-hand side. If the RHS contains `|`, or ends in a `{ ... }` struct body, it defines a new type; otherwise it is an alias. The collision case — an RHS that is a bare identifier, which could in principle be read as a type definition with a single no-data constructor — is always read as an alias, since such a type would be isomorphic to `Unit` and carries no information. The collapse form (`Foo { ... }`) still covers the useful struct-definition cases.

### Value Instantiation

The textual language is also used to write values, not just types. A value is constructed by naming a constructor and supplying its fields:

```
<Constructor>(<field> = <value>, ...)
```

Arguments may be passed positionally or by name; named arguments use `=` (distinct from the `:` used in type-definition fields, which annotates a field with a type). For a type with multiple constructors, the constructor name is what selects the variant:

```
Some(value = 42)
None
MinInclusive(bound = 0)
```

For a single-constructor type the constructor name and the type name coincide, so the same syntax constructs values of struct-shaped types directly:

```
Measurement(unit = "V", value = 12.5)

LogLine(severity = Error, time = now, line = "disk full")
```

Bare-identifier constructors carry no fields and are written without parentheses (`None`, `All`, `Error`).

### Constraints

A `Constraint` narrows the set of legal values admitted by a type. The type system defines it as a built-in ADT over numeric literals:

```
Constraint = All
           | MinInclusive  { bound: Number }
           | MinExclusive  { bound: Number }
           | MaxInclusive  { bound: Number }
           | MaxExclusive  { bound: Number }
           | Range         { min: Number,   max: Number   }
           | Values        { allowed: Set(Number)         }
           | MultipleOf    { divisor: Number              }
           | Union         { a: Constraint, b: Constraint }
           | Intersection  { a: Constraint, b: Constraint }
           | Not           { inner: Constraint            }
```

`Number` stands for any numeric literal. `Constraint` is not generic: at each use site the compiler validates that the literals inside fit the target numeric type (e.g. `0.5` is rejected where an integer type is expected).

`Range(min, max)` is equivalent to `Intersection(MinInclusive(min), MaxInclusive(max))`; the compiler treats it as sugar for that form, so it needs no separate implication rule and does not appear distinctly on the wire.

**Constraints are just parameter values.** A `Constraint` is passed to a type parameter like any other argument; nothing in the surface syntax marks it as special. The only thing that makes constraints useful is that certain **built-in** types accept `Constraint`-typed parameters and hardcode their effect on the value set. User-defined types do not interpret constraints themselves; if a user type wants its values constrainable, it declares a `Constraint`-typed parameter and threads it into a built-in that does.

Consequently there is no way to "constrain an arbitrary user type" from outside. If you want a subset of a user-defined sum type, define a narrower sum type — *Subset Determination* will recognise the relationship structurally.

#### Where constraints are interpreted

Each built-in below declares the `Constraint`-typed parameters it accepts and fixes what they mean:

* `UnsignedInteger(sizeInBytes, constraint: Constraint = All)`, `SignedInteger(sizeInBytes, constraint: Constraint = All)`, `VariableLengthInteger(maxSizeInBytes, constraint: Constraint = All)` — `constraint` narrows the integer value. All `Constraint` forms are accepted; literals must be integers fitting the declared size.
* `FloatingPoint(sizeInBytes, constraint: Constraint = All)` — `constraint` narrows the float value. All forms accepted **except** `Values` and `MultipleOf`: both rest on value equality, which is unreliable for floating-point representations.
* `DynamicArray(elementType, length: Constraint = All)` — `length` narrows the number of elements. All forms accepted; literals must be non-negative integers.
* `Set(elementType, size: Constraint = All)` — `size` narrows set cardinality (same rules as `DynamicArray`).
* `Array(elementType, size)`, `Stream(elementType)`, `Unit` — declare no `Constraint` parameters. (`Array`'s length is already fixed; `Stream` is unbounded by definition; `Unit` has exactly one value.)

#### Worked examples

A percentage and a table leg index, using the `Range` sugar:

```
Percentage     = UnsignedInteger(1, constraint = Range(min = 0, max = 100))
TableLegNumber = UnsignedInteger(1, constraint = Range(min = 1, max = 4))
```

"1 to 100, excluding 50" — composition with `Not`:

```
OddPercent = UnsignedInteger(1, constraint =
    Intersection(Range(min = 1, max = 100), Not(Values(50))))
```

A thermostat setpoint in 0.5° increments, between 15° and 30° inclusive — combining a range with `MultipleOf`, where the target type must be integer since `MultipleOf` is not accepted on `FloatingPoint`. One way is to represent the setpoint as tenths of a degree:

```
Setpoint = UnsignedInteger(2, constraint =
    Intersection(Range(min = 150, max = 300), MultipleOf(5)))
```

A `Measurement` that wants its value constrainable declares a `Constraint` parameter and threads it into its value field. Exclusive upper bound here:

```
Measurement(unit: String, valueConstraint: Constraint = All) {
    value: FloatingPoint(8, constraint = valueConstraint)
}

Voltage = Measurement("V", Intersection(MinInclusive(0.0), MaxExclusive(48.0)))
```

Note that `Measurement` does not interpret `valueConstraint` — it only forwards the value to `FloatingPoint`, which does.

A length-bounded byte string:

```
Handshake = DynamicArray(UnsignedInteger(1), length = Range(min = 0, max = 128))
```

Subsetting a sum type is done by defining a narrower sum type; no constraint is involved. This also handles cases like "a set of only some Severity constructors" — use the narrower type as the element type:

```
Severity          = Error | Warning | Info | Debug
DisplaySeverity   = Error | Warning                  // DisplaySeverity ⊆ Severity structurally
DisplaySeverities = Set(DisplaySeverity)
```

#### Implication

Subset determination across constrained built-ins reduces to an implication check over the `Constraint` ADT: for `X(constraint = C₁)` to be a subset of `X(constraint = C₂)`, every value satisfying `C₁` must satisfy `C₂`. The rules fall out case-by-case from the constructors:

* `MinInclusive(a) ⟹ MinInclusive(b)` iff `a ≥ b`; `MaxInclusive(a) ⟹ MaxInclusive(b)` iff `a ≤ b`; exclusive variants analogous.
* Cross-cases between inclusive and exclusive follow directly, e.g. `MinInclusive(a) ⟹ MinExclusive(b)` iff `a > b`.
* `Values(S₁) ⟹ Values(S₂)` iff `S₁ ⊆ S₂`.
* `MultipleOf(m) ⟹ MultipleOf(n)` iff `n` divides `m`.
* `Union`, `Intersection`, `Not` distribute as expected.

Any new `Constraint` form added later must come with an implication rule, or the subset algorithm cannot accept it.

## Library Types

The following types are defined by the type system itself and are available without import in every program that uses TYPES. They are not built-ins in the language sense — they are ordinary user-level types — but every implementation ships them so the rest of the system can rely on them.

### Numeric Aliases

```
Byte   = UnsignedInteger(1)
Long   = UnsignedInteger(8)
Double = FloatingPoint(8)
```

### Boolean

```
Boolean = True | False
```

`True` and `False` are bare-identifier constructors; the type carries one bit of information.

### Option

```
Option(contentType: Type) = None | Some { value: contentType }
```

The standard "value or absence" sum type. Used everywhere a field may be missing.

### String

```
String(length: Constraint = All) = DynamicArray(Byte, length)
```

A length-bounded byte string. The `length` parameter forwards to the underlying `DynamicArray`, so the same `Constraint` forms are accepted (e.g. `String(MaxInclusive(128))` for an "at most 128 bytes" string, `String(Range(min = 1, max = 64))` for "between 1 and 64 bytes inclusive"). Unconstrained `String` is unbounded.

### DynamicValue

```
DynamicValue
```

A value of any type. Wire-encoded as the bytes of the value with no embedded type tag — the receiver must know the expected type from context (typically from a sibling field or from configuration) and apply it to consume the right number of bytes. `DynamicValue` exists for protocols where the type of a field depends on a separately-carried discriminator (for example, the value carried by a SCAN modality state, whose type is determined by the modality's declared `outputType`/`inputType`).

### Type

`Type` is the type of types. It is the same `Type` already used to declare type parameters (e.g. `Option(contentType: Type)`), promoted to first-class status: a field declared `someField: Type` accepts any type as its value, and is instantiated by writing the type name directly (`someField = String`, `someField = DeviceInformation`). This lets a type be carried as data — for example, a SCAN modality declares `outputType: Type` and is instantiated with `outputType = DeviceInformation`. The binary representation of a `Type` value is defined in the *Types Binary Representation* section.

## Types Binary Representation

This binary represenation is actually used by devices during normal operations, so it is parsed
dynamically runtime by devices. This information is usually read once, then not used anymore. It is therefore more important to have an easy
parsing instead versus an efficient encoding.

TODO

## Values Binary Representation

Values are sent between devices during runtime. It is what the network is designed for, therefore it is important
to have the most space efficient encoding possible.

TODO

## Subset Determination

When invoking commands with some data value, possibly a transformed one, it is important to be able to tell whether
that value fits the type the command expects. These rules define when that is the case.

TODO

## Transformation Language

Since SCAN does not define devices at all, the transformation language's goal is to make devices compatible, by
being able to join current data and transform them into a proper format for modalilties. The textual language,
similar to the type textual language, is only designed to interact with at the administrative interface. Devices
do not have to parse or understand it.

TODO

## Transformation Language Binary Representation

The transformation program is sent to the devices dynamically and can be updated by the user at any time. All devices must support
a VM to run these transformation programs in memory. The point of the binary representation is therefore to enable a very
small VM implementation. Since these program are "just" statelessly transforming values, efficiency is less important than
fitting small microcontrollers.

TODO

## Type Reference

