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
* An algorithm to determine if a value is a member of a type
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
* Set
* Stream

An `Array` is an ordered aggregation of multiple values of the given type. The number of items is bounded by a `Constraint` passed as the `size` parameter; an integer literal is sugar for a fixed count. When the constraint admits only a single length the count is fixed by the type and is not written on the wire; otherwise the count is written alongside the value.

```
HourlyMeasurements = Array(Measurement, size = 24)      // exactly 24, no count on the wire
Events             = Array(Event)                        // unbounded (size = All), count on the wire
Handshake          = Array(Byte, size = MaxInclusive(128)) // 0..128, count on the wire
```

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
Bytes    = Array(Byte)
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

`Range(min, max)` is equivalent to `Intersection(MinInclusive(min), MaxInclusive(max))`; the compiler treats it as sugar for that form, so it needs no separate evaluation rule and does not appear distinctly on the wire.

**Constraints are just parameter values.** A `Constraint` is passed to a type parameter like any other argument; nothing in the surface syntax marks it as special. The only thing that makes constraints useful is that certain **built-in** types accept `Constraint`-typed parameters and hardcode their effect on the value set. User-defined types do not interpret constraints themselves; if a user type wants its values constrainable, it declares a `Constraint`-typed parameter and threads it into a built-in that does.

Consequently there is no way to "constrain an arbitrary user type" from outside. If you want a narrower version of a user-defined sum type, define a narrower sum type — *Type Membership* recognises a value as belonging to any sum type whose constructors cover its shape, so no type-to-type relation is consulted.

#### Where constraints are interpreted

Each built-in below declares the `Constraint`-typed parameters it accepts and fixes what they mean:

* `UnsignedInteger(sizeInBytes, constraint: Constraint = All)`, `SignedInteger(sizeInBytes, constraint: Constraint = All)`, `VariableLengthInteger(maxSizeInBytes, constraint: Constraint = All)` — `constraint` narrows the integer value. All `Constraint` forms are accepted; literals must be integers fitting the declared size.
* `FloatingPoint(sizeInBytes, constraint: Constraint = All)` — `constraint` narrows the float value. All forms accepted **except** `Values` and `MultipleOf`: both rest on value equality, which is unreliable for floating-point representations.
* `Array(elementType, size: Constraint = All)` — `size` narrows the number of elements. All forms accepted; literals must be non-negative integers. An integer literal `n` is shorthand for `Values({n})` (exactly `n` elements).
* `Set(elementType, size: Constraint = All)` — `size` narrows set cardinality (same rules as `Array`).
* `Stream(elementType)`, `Unit` — declare no `Constraint` parameters. (`Stream` is unbounded by definition; `Unit` has exactly one value.)

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
Handshake = Array(UnsignedInteger(1), size = Range(min = 0, max = 128))
```

Narrowing a sum type is done by defining a smaller sum type; no constraint is involved. This also handles cases like "a set of only some Severity constructors" — use the narrower type as the element type:

```
Severity          = Error | Warning | Info | Debug
DisplaySeverity   = Error | Warning                  // DisplaySeverity ⊆ Severity structurally
DisplaySeverities = Set(DisplaySeverity)
```

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
String(size: Constraint = All) = Array(Byte, size)
```

A length-bounded byte string. The `size` parameter forwards to the underlying `Array`, so the same `Constraint` forms are accepted (e.g. `String(MaxInclusive(128))` for an "at most 128 bytes" string, `String(Range(min = 1, max = 64))` for "between 1 and 64 bytes inclusive"). Unconstrained `String` is unbounded.

Note, that strings are not bound by characters but by the overall bytes needed. This is specifically for protocol clarity.

### DynamicValue

```
DynamicValue
```

A value of any type. Wire-encoded as the bytes of the value with no embedded type tag — the receiver must know the expected type from context (typically from a sibling field or from configuration) and apply it to consume the right number of bytes. `DynamicValue` exists for protocols where the type of a field depends on a separately-carried discriminator (for example, the value carried by a SCAN modality state, whose type is determined by the modality's declared `outputType`/`inputType`).

### Type

`Type` is the type of types. It is the same `Type` already used to declare type parameters (e.g. `Option(contentType: Type)`), promoted to first-class status: a field declared `someField: Type` accepts any type as its value, and is instantiated by writing the type name directly (`someField = String`, `someField = DeviceInformation`). This lets a type be carried as data — for example, a SCAN modality declares `outputType: Type` and is instantiated with `outputType = DeviceInformation`. The binary representation of a `Type` value is defined in the *Types Binary Representation* section.

## Types Binary Representation

A type is serialized as a value (using the *Values Binary Representation* below) of the following AST:

```
TypeDefinition {
    name: String,
    parameters: Array(ParameterDefinition),
    union: Array(ConstructorDefinition, size = MinInclusive(1))
}

ParameterDefinition {
    name: String,
    type: Expression,
    default: Option(Expression)
}

ConstructorDefinition {
    name: String,
    fields: Array(FieldDefinition)
}

FieldDefinition {
    name: String,
    type: Expression
}

Expression = Invocation { name: String, arguments: Array(Expression) }
           | Integer    { value: SignedInteger(8) }
           | Float      { value: FloatingPoint(8) }
           | String     { value: String }
```

An `Expression` is a self-describing term. `Invocation` covers every name-based form — type references, generic type applications, value-constructor calls, and bare-identifier constructors (`None`, `All`, `True`, `False` — zero-argument invocations). The three literal variants are the only non-invocation leaves that appear in the surface language.

`Invocation.arguments` are positional. The textual language allows named arguments and default values for ergonomics; the compiler resolves both into a canonical positional list before emission, so the wire AST needs no per-argument name wrapper.

Integer literals are bounded to the `SignedInteger(8)` range. A literal outside that range (e.g. a `MaxInclusive` bound at `UnsignedInteger(8)`'s maximum of 2⁶⁴−1) cannot be expressed in the AST and must be rewritten by the author.

A `Type` value (as declared in *Library Types*) is an `Expression` — specifically, an `Invocation`. Aliases are resolved during compilation and do not appear in the AST.

## Values Binary Representation

Values are sent between devices during runtime. It is what the network is designed for, therefore it is important
to have the most space efficient encoding possible.

The wire form carries only the value — nothing describes the type. Both sides are assumed to hold the same type definition and to walk it in lockstep, the encoder emitting bytes and bits in a fixed order derived from the type and the decoder reading them back by replaying the same walk. Multi-byte numeric primitives are big-endian (as already stated under *Built-in "Primitive" Types*).

### Principles

The encoding is byte-oriented for bulk data and bit-packed for sub-byte fields. Within a struct or union, sub-byte fields (union discriminators, `Boolean`s, bare-only bitmask `Set`s) share bytes with each other — consecutive sub-byte pieces fill up the same byte even when byte-aligned fields appear between them. Aggregates (`Array`, `Set`, `Stream`) always begin and end on a byte boundary and do not share bit state with their surroundings; items of 1..4 bits pack as a continuous bit stream across the aggregate's byte span, while larger items are byte-aligned one at a time.

### Encoder and Decoder State

Both sides keep:

* a **cursor** — current byte offset into the output or input, and
* an **active bit byte** — either `None`, or a pair (byte position, bits already used, `0..7`).

The state carries across fields of a struct, across the discriminator and body of a union, and into embedded structs and unions. It **resets to `None`** on entry to any aggregate and again on exit from it; the aggregate's own content is encoded/decoded with a fresh bit state that does not leak in either direction. When bit state is reset with an active bit byte that is only partially filled, the byte is simply closed in place — its unused slots remain `0`.

### Writing a k-bit Value

Used for union discriminators (`1..8` bits), `Boolean`s, and other sub-byte fields within a struct or union scope.

If the active bit byte has at least *k* free bits, the *k* bits are written into the next free slots, the most-significant bit of the value into the highest-significance free slot. The used-bit counter is incremented by *k*; when it reaches 8 the state returns to `None`. Otherwise, a fresh byte is allocated at the current cursor, the cursor advances by one, and that byte becomes the active bit byte with 0 bits used; the *k* bits are then written into it. For *k* > 8, the value is split into groups from the MSB end and each group is written by this rule.

### Writing Byte-Aligned Data

A byte-aligned value of *n* bytes is written at the current cursor; the cursor advances by *n*. **Byte-aligned writes do not close the active bit byte.** A bit written before and a bit written after such a field may share the same byte — the byte physically sits before the intervening bytes in the stream, but its remaining slots are still open. This is the lever that keeps a `Boolean` sitting between two integers from costing a whole extra byte.

### Per-Type Encoding

**Unit** — zero bytes.

**UnsignedInteger(n)**, **SignedInteger(n)** — *n* bytes, big-endian; two's complement for signed.

**FloatingPoint(n)** — *n* bytes, IEEE 754 (binary32 for *n* = 4, binary64 for *n* = 8), big-endian.

**VariableLengthInteger(maxN)** — 1 to *maxN* bytes, most-significant group first. Each non-final byte has its high bit set to 1 and carries 7 value bits. The final byte carries 8 value bits; it is the final byte either because its high bit is 0 or because *maxN* bytes have already been read/written.

**Single-constructor type** `T { ...fields }` — the fields are encoded in declaration order. Bit state carries across them.

**Multi-constructor type** `T = C₀ | ... | Cₙ₋₁` — a `⌈log₂ n⌉`-bit discriminator is written (via the bit-packing rule) carrying the zero-based index of the selected constructor in declaration order, followed by that constructor's fields. Bit state carries across both. If *n* = 1 the discriminator occupies 0 bits and nothing is written for it.

**Array(T, size = C)** — on entry the aggregate is aligned to a byte boundary (any in-progress bit byte is closed in place) and bit state becomes `None`. If *C* admits more than one length, the item count is written first as `VariableLengthInteger(v)`, where *v* is the smallest size in `1..8` such that `VariableLengthInteger(v)` can represent every length admitted by *C* (and *v* = 8 if *C* is `All`); when *C*'s lower bound is a positive number *m*, the value written is `actualCount − m` and the decoder adds *m* back. If *C* admits exactly one length, no count is written — the decoder reads that fixed number of items straight from the type. The count (if any) is followed by that many encodings of *T* under one of two layouts:

* **Packed item layout** — used when *T* has a statically fixed encoded size of 1, 2, 3, or 4 bits. The items form a continuous MSB-first bit stream within the aggregate's byte span; individual items may cross byte boundaries. The first item's most-significant bit occupies the top of the first byte of item data. If the total number of item bits is not a multiple of 8, the trailing slots of the last byte remain `0`.
* **Byte-aligned item layout** — used otherwise. Each item begins on a byte boundary and occupies `⌈item_bits / 8⌉` bytes; an item may itself use bit-packing internally (if it is a struct or union with sub-byte fields), but that bit state is contained within the item's byte span and does not carry across items.

On exit the aggregate ends on a byte boundary and the enclosing scope continues with bit state `None`.

**Set(T, size = C)** — takes one of two forms, selected by *T*:

* **Bitmask form**, when every constructor of *T* is a bare identifier (so *T* has a fixed value space of *K* members). The set is encoded as `⌈K / 8⌉` bytes of bitmask; within each byte the highest-significance bit is considered first, and bit *i* of the stream (so bit *i* mod 8 of byte *i* div 8, counting MSB-first) is 1 iff constructor *i*, in declaration order, is a member. No count is written. Entered/exited at byte boundaries like any aggregate.
* **Sequence form**, otherwise. Identical to `Array(T, size = C)` — including the conditional leading count and the packed / byte-aligned item layouts. Each value appears at most once; order is unspecified on the wire (encoders may sort for determinism).

**Stream(T)** — entered at a byte boundary. A concatenation of *T*-encodings under the same per-item rule as `Array`, running until the enclosing transport frames end. No count, no terminator.

## Type Membership

When a transformation produces a value and delivers it into a typed slot — a cluster member's value, a built-in's parameter, a field of a struct under construction — the implementation must decide whether the value belongs to that slot's type. This section defines that relation.

Membership is a purely value-level decision: given a value *v* and a type *T*, answer yes or no. No reasoning relates one type to another; there is no implication algebra, no subtyping relation, no variance. Each decision is a local walk of the value driven by the shape of the type, structurally analogous to decoding.

### Per-Type Rules

**Unit** — the sole value is a member.

**UnsignedInteger(n, constraint)**, **SignedInteger(n, constraint)**, **VariableLengthInteger(maxN, constraint)** — *v* is a member iff *v* fits the declared size and satisfies `constraint` (evaluated per *Constraint Evaluation* below).

**FloatingPoint(n, constraint)** — *v* is a member iff *v* is a finite binary32 (when *n* = 4) or binary64 (when *n* = 8) value and satisfies `constraint`.

**Single-constructor type** `T { f₁: T₁, ..., fₖ: Tₖ }` — *v* is a member iff *v* is built with *T*'s constructor and every field *fᵢ* is a member of *Tᵢ*.

**Multi-constructor type** `T = C₀ | ... | Cₙ₋₁` — *v* is a member iff the constructor used to build *v* appears among *C₀ … Cₙ₋₁* (matched by name and field signature) and, for that constructor's fields, each field value is a member of its declared field type in *T*. This is the mechanism by which a value of a narrower sum type is admitted into a slot typed by a wider sum type: no cross-type relation is consulted, only the value's shape.

**Array(T, size)** — *v* is a member iff its length satisfies `size` (evaluated as a constraint over a non-negative integer) and every element is a member of *T*.

**Set(T, size)** — as `Array`, with the additional requirement that elements are pairwise distinct.

**Stream(T)** — each element produced or consumed through the stream is a member iff it is a member of *T*. A stream has no terminal state, so there is no membership decision over the stream as a whole.

### Constraint Evaluation

A `Constraint` is evaluated against a candidate value by structural recursion:

* `All` — always satisfied.
* `MinInclusive(a)` — satisfied iff `v ≥ a`. `MinExclusive(a)` iff `v > a`. `MaxInclusive(a)` iff `v ≤ a`. `MaxExclusive(a)` iff `v < a`.
* `Range(min, max)` — evaluated as `Intersection(MinInclusive(min), MaxInclusive(max))`.
* `Values(S)` — satisfied iff `v ∈ S`.
* `MultipleOf(m)` — satisfied iff `v mod m = 0`.
* `Union(a, b)` — satisfied iff either sub-constraint is.
* `Intersection(a, b)` — satisfied iff both sub-constraints are.
* `Not(inner)` — satisfied iff `inner` is not.

Any new `Constraint` form added later must come with an evaluation rule, or the membership algorithm cannot accept it.

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

