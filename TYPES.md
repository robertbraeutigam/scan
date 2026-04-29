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

A `Set` is an **unordered** collection of **distinct** flag values. Its element type must be a sum of **bare-identifier constructors only** — i.e. a fixed value space whose every member can be represented by a single bit. A `Set` is encoded as a bitmask of `⌈K / 8⌉` bytes, where `K` is the number of constructors of the element type. Collections whose elements carry data are expressed as `Array(T)` (with deduplication enforced at the application layer if needed); the type system intentionally provides only the bitmask form here.

```
Flags = IPv4 | IPv6

EnabledProtocols = Set(Flags)
```

A `Stream` is a potentially infinite sequence of values of the given type:

```
Samples      = Stream(FloatingPoint(4))
VideoContent = Stream(Byte)
```

A `Stream` has no terminator on the wire, so it will never terminate explicitly. It can only terminate implicitly if the communication frame it is in ends. 
Consequently any data or information following a Stream in any structure will never be written nor read, so realistically any message may only contain
one `Stream` and only as the last position in its data structure.

Bulk byte content — file content, firmware images, UTF-8 strings, live media — is expressed as `Array(T, size)` or `Stream(T)` where *T* is a 1-byte primitive (`UnsignedInteger(1)`, `SignedInteger(1)`, `VariableLengthInteger(1)`). On the wire, each element is one byte interpreted per *T*; how a decoder delivers those bytes to its consumer is implementation-defined, but is expected to be in batches rather than one element at a time (see *Incremental Consumption*).

```
Firmware = Array(Byte, size = MaxInclusive(1048576))
```

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
           | Values        { allowed: Array(Number)       }
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
* `Set(elementType)` — declares no `Constraint` parameters. The element type is itself the constraint: it must be a sum of bare-identifier constructors, and every value of that type either is or is not in the set.
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
Boolean = False | True
```

`False` and `True` are bare-identifier constructors; the type carries one bit of information.

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

The encoding is byte-oriented for bulk data and bit-packed for sub-byte fields. Bit state is global to a value — sub-byte writes (union discriminators, `Boolean`s, members of a `Set`) share bytes across struct fields, across union discriminator-and-body, across array/stream/set items, and across aggregate boundaries. There is no byte-alignment forced by any structural boundary; the only forced flushes are the bit byte filling up and a buffer cap (see *Bit-byte buffer cap*) that bounds the encoder/decoder's working state.

### Encoder and Decoder State

Both sides keep:

* a **cursor** — current byte offset into the output or input, and
* an **active bit byte** — either `None`, or a pair (byte position, bits already used, `0..7`).

The state is global to a value — it carries across all fields, items, and aggregate boundaries. Encode and decode terminate when the type walk completes (or for `Stream`, when the transport ends); any bit byte still partially filled at termination is closed in place — its unused slots remain `0`.

### Writing a k-bit Value

Used for union discriminators (`1..8` bits), `Boolean`s, `Set` members, and other sub-byte writes.

If the active bit byte has at least *k* free bits, the *k* bits are written into the next free slots, the most-significant bit of the value into the highest-significance free slot. The used-bit counter is incremented by *k*; when it reaches 8 the byte is finalised and the state returns to `None`. Otherwise, a fresh byte is allocated at the current cursor, the cursor advances by one, and that byte becomes the active bit byte with 0 bits used; the *k* bits are then written into it. For *k* > 8, the value is split into groups from the MSB end and each group is written by this rule.

### Writing Byte-Aligned Data

A byte-aligned value of *n* bytes is written at the current cursor; the cursor advances by *n*. **Byte-aligned writes do not close the active bit byte.** A bit written before and a bit written after such a field may share the same byte — the byte physically sits before the intervening bytes in the stream, but its remaining slots are still open. This is the lever that keeps a `Boolean` sitting between two integers from costing a whole extra byte.

### Bit-byte buffer cap

Because byte-aligned writes do not close the active bit byte, the encoder must hold those bytes behind the open bit byte until it closes (so the bit byte can be emitted at its allocated position before the bytes that follow it in time but after it on the wire). To keep the encoder's working buffer bounded, the bit byte is **force-closed after 32 byte-aligned bytes** have been written while it is open: the active byte is emitted in place with its remaining slots `0`, the 32 buffered bytes flush out behind it, and writing resumes with no active bit byte.

The decoder follows the same rule symmetrically: it counts byte-aligned bytes consumed since the current bit byte was opened and, on the 32nd, treats the bit byte as closed (any unread slots are discarded). The cap is wire-visible, not just an implementation choice — both sides must agree.

The cap costs at most 7 wasted bits per 32 bytes (≈ 2.7 %) and only applies in pathological layouts where a sub-byte write is followed by a long run of byte-aligned writes with no further sub-byte writes. There is no marker; encoder and decoder agree by counting alone, and a desynchronisation between them corrupts the rest of the value unrecoverably — but the wire format already has no recovery story for any other corruption either.

### Per-Type Encoding

**Unit** — zero bytes.

**UnsignedInteger(n)**, **SignedInteger(n)** — *n* bytes, big-endian; two's complement for signed.

**FloatingPoint(n)** — *n* bytes, IEEE 754 (binary32 for *n* = 4, binary64 for *n* = 8), big-endian.

**VariableLengthInteger(maxN)** — 1 to *maxN* bytes, most-significant group first. Each non-final byte has its high bit set to 1 and carries 7 value bits. The final byte carries 8 value bits; it is the final byte either because its high bit is 0 or because *maxN* bytes have already been read/written.

**Single-constructor type** `T { ...fields }` — the fields are encoded in declaration order. Bit state carries across them.

**Multi-constructor type** `T = C₀ | ... | Cₙ₋₁` — a `⌈log₂ n⌉`-bit discriminator is written (via the bit-packing rule) carrying the zero-based index of the selected constructor in declaration order, followed by that constructor's fields. Bit state carries across both. If *n* = 1 the discriminator occupies 0 bits and nothing is written for it.

**Array(T, size = C)** — if *C* admits more than one length, the item count is written first as `VariableLengthInteger(v)`, where *v* is the smallest size in `1..8` such that `VariableLengthInteger(v)` can represent every length admitted by *C* (and *v* = 8 if *C* is `All`); when *C*'s lower bound is a positive number *m*, the value written is `actualCount − m` and the decoder adds *m* back. If *C* admits exactly one length, no count is written — the decoder reads that fixed number of items straight from the type. The count (if any) is followed by that many encodings of *T*, with bit state carrying through the count and across items just as it carries across struct fields.

**Set(T)** — *T*'s constructors are all bare identifiers, so *T* has a fixed value space of *K* members. The set is encoded as a *K*-bit run in the bit stream: bit *i* (counted MSB-first from the position at which the set begins) is 1 iff constructor *i*, in declaration order, is a member. No count is written and no padding to a byte boundary is added — the run of *K* bits participates in the surrounding bit state like any other sub-byte write.

**Stream(T)** — a concatenation of *T*-encodings, running until the enclosing transport frames end. No count, no terminator. Bit state carries across items as it does in an `Array`.

### Incremental Consumption

The encoding above is sequential — byte by byte for byte-aligned data, bit by bit within an active bit byte — and the decoder advances through the input cursor by cursor without ever needing to look ahead beyond the bounded primitive currently being read. Memory consumed by the decoder while traversing a value of type *T* is therefore bounded by `max(largest declared primitive in T, transport frame budget)`, independent of the value's size. A value containing a `Stream`, a long `Array`, or a long `String` can be processed end-to-end by a consumer with only that bounded working memory, by handling each piece of the value as the decoder produces it.

How a decoder presents these pieces to its caller — events, callbacks, an iterator, push or pull — is an implementation choice, not pinned by the protocol. In particular, runs of consecutive bytes within an `Array` or `Stream` whose element type is a 1-byte primitive (`UnsignedInteger(1)`, `SignedInteger(1)`, `VariableLengthInteger(1)`) are expected to be delivered in batches rather than one element at a time; this is the only practical way to relay bulk byte runs at line rate, but it has no effect on the wire form.

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

**Set(T)** — *v* is a member iff every element is a member of *T* (with *T* required to be a sum of bare-identifier constructors) and elements are pairwise distinct.

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
being able to join current data and transform them into a proper format for modalities. The textual language,
similar to the type textual language, is only designed for interaction at the administrative interface. Devices
do not parse or understand it; they execute only the compiled bytecode the admin tool produces (see
*Transformation Language Binary Representation*).

The split is deliberate: the textual surface is functional, expressive, and convenient to write; the compiled
form is a small per-event dispatch program for a tiny VM that fits on a constrained microcontroller. The compiler
sits in the admin tool — running on a large device with no memory constraints — and absorbs all the complexity of
bridging the two.

### Surface Language

The textual language is a pure functional expression language over typed values. There are no statements, no
mutation, no loops, no user-defined recursion, and no side effects of any kind. A program declares one
entry-point expression per cluster member: an expression of type `{ acc: accType, outputs: ClusterOutputs }` with
two free variables in scope, `input` of the member's declared `inputType` and `acc` of the cluster's declared
`accType` (see *Virtual Modalities* in `README.md` for both). The expression's two fields are the entire effect of
the transformation: the new accumulator and the per-member output map. Entry points may share helper definitions
(named expressions, generic functions over types from the SCAN Type System).

The language admits:

* Primitive literals and structured-value constructors (struct, union, array, set, stream).
* Named bindings (`let x = expr in expr`).
* Function definitions and applications, generic over types.
* Field projection on structured types (`v.f` for struct fields, `v as C` for union cases).
* Conditionals (`if cond then a else b`) and exhaustive match on union constructors.
* A standard library of stream-valued operations (see *Streamability Classification* below).

What it does not admit:

* User-defined recursion. All iteration goes through the standard library.
* Operations with no streamable lowering (see below).
* Side effects of any kind — there is no `emit`, no mutable cluster-state binding, no I/O, and no ambient input
  beyond `acc` and `input`. The transformation's effect on the world is exactly its return value.
* Reflection, dynamic dispatch on values, or any computation over types at runtime.

The exact concrete syntax is not yet fixed (TODO), but the semantics described here pin down the design space.

### Streams

`Stream(T)` is a first-class type in the source language as it is in the type system. Every transformation works,
ultimately, by consuming `input` step by step (as the decoder advances through it) and constructing `outputs`
step by step (which the encoder serializes as it advances). Stream-valued expressions name stages of the
dataflow; the compiler fuses them into a single structural traversal that walks the input and the output type
trees in lockstep.

A finite value (a struct, a primitive) can be treated as a degenerate stream: its structural traversal is a
complete walk of its shape. The streamability rules below apply uniformly.

### Streamability Classification

The standard library is closed under streamability: every primitive operation has a known step-by-step lowering,
every composition of streamable operations is streamable, and each operation's contribution to the enclosing
frame's size is fixed by its lowering. Operations not in this list are not in the library; programs that attempt
to realise them by composition are rejected at compile time.

* **Pointwise** — one input element produces exactly one output element with no cross-element dependency:
  `map(s, f)`, arithmetic (`+`, `-`, `*`, `/`), comparison (`<`, `==`, ...), boolean (`and`, `or`, `not`), type
  coercions. Lowering: per input element, evaluate `f` and emit one output element. Frame contribution: none.

* **Predicated pointwise** — zero or one output per input: `filter(s, p)`, `takeWhile(s, p)`. Lowering: per input
  element, evaluate the predicate; emit if true. Frame contribution: none.

* **Folded** — consume the whole stream, produce one value: `fold(s, f, z)`, `sum(s)`, `count(s)`, `max(s)`,
  `min(s)`, `any(s, p)`, `all(s, p)`. Lowering: maintain an accumulator in the enclosing aggregate's frame;
  update on each input element; on end-of-input the accumulator is the result, ready to be emitted or fed
  downstream. Frame contribution: one slot of the accumulator's type, in the frame of the aggregate being folded.

* **Field projection** — select a sub-tree of a structured value: struct-field access (`v.f`), union case
  selection (`v as C`). Lowering: forward the traversal steps that fall inside the projected sub-tree to the
  consumer; ignore steps outside it. Frame contribution: none — the structural depth tracking is the decoder's
  own.

* **Concatenation** — `concat(s1, s2)`. Lowering: dispatch s1's traversal to s1's handlers; on s1 end, switch to
  s2. Frame contribution: a small phase tag per concat node, in the enclosing frame.

* **Bounded windowing** — operations parameterised by a static integer *n*: `take(s, n)`, `drop(s, n)`,
  `chunkBy(s, n)`. Lowering: maintain a counter (`take`, `drop`) or a buffer of *n* elements (`chunkBy`).
  Frame contribution: one counter slot, or *n* element slots, in the enclosing frame.

Operations explicitly excluded because they have no streamable lowering: `reverse`, `sort` over arbitrary input,
last-element extraction without bound, `zip` over two streams unless one is statically bounded and small,
arbitrary indexing, second-pass scans. Their absence is the discipline that keeps every program one-pass.

A composition of streamable operations is streamable. Its memory cost is computed structurally from the input
type and the expression — see *Structural Alignment* below.

### Structural Alignment

Beyond per-operation streamability, a transformation must align *structurally*: the output traversal it must
produce, ordered by the output type, must be fillable from the input traversal the decoder drives, ordered by
the input type, with bounded look-ahead only. The look-ahead is exactly the run stack described in *Virtual
Modalities* in `README.md`.

The stack mirrors the input type's structural tree. As the decoder enters an aggregate sub-element (a struct, a
union variant, an array / set / stream item), the bytecode pushes a frame for that sub-element; on leaving, it
pops. A frame for sub-type *T* under expression *E* holds the bounded compiler-managed memory needed to keep the
output schedule fed while the input is inside *T*:

* **Captures** — values of input scalars (and bounded sub-aggregates) seen earlier in this frame whose value the
  output references at a structurally later position.
* **Fold accumulators** — slots for any fold operation consuming a stream or array under this frame.
* **Discriminator** — for a union's variant frame, the constructor tag.

The frame's size, written `frame(T, E)`, is computed structurally:

```
frame(Primitive, E)     = bytes needed to capture this scalar at this position (0 if not captured)
frame(Struct fs, E)     = sum of capture slots for fields whose value is read by structurally-later siblings
                        + max(frame(f.T, E_f) for f ∈ fs)         -- one child active at a time
                        + any fold-accumulator slot for this struct, if folded
frame(Union cs, E)      = 1 byte discriminator
                        + max(frame(c.T, E_c) for c ∈ cs)
frame(Array T', E)      = any fold-accumulator slot for the array, if folded
                        + frame(T', E')                            -- one item active at a time
frame(Stream T', E)     = same as Array
```

The total stack budget is `frame(T_in, E)` evaluated at the input type's root for the transformation expression
*E*. The maximum stack depth is the maximum nesting of the input type. Both are static properties of *T_in* and
*E* alone — readable off the types and the expression without any data-flow analysis.

A transformation is **structurally streamable** iff the alignment between input and output is satisfiable under
bounded captures only:

* **Bounded reordering allowed.** Output positions may be filled by input scalars (or bounded sub-aggregates)
  declared earlier in the input than the output position, by adding capture slots to the appropriate frames.
* **Stream reordering forbidden.** A stream-typed output position must be filled by a stream-typed input
  sub-element the bytecode is currently traversing under the active frame. The output schedule cannot "skip
  past" a stream to reach material that comes after it, because a stream may be unbounded and capturing it
  would require unbounded memory.
* **Folds bridge the gap.** A fold over a stream produces a bounded value that may be placed anywhere
  structurally later than the stream itself; the fold accumulator lives in the frame and is consumed after the
  stream has been fully walked.

A program that violates structural streamability is rejected at compile time with a precise diagnostic naming the
output position whose dependency is unsatisfiable, the input position whose data would have had to be buffered or
revisited, and which of the rules above was violated.

### Accumulator and Outputs

The two free variables `acc` and `input` are ordinary value bindings of the language: read-only, typed by the
cluster declaration, and used like any other. The transformation's only output is the record it returns:

```
{
   acc:     <expression of type accType>,
   outputs: <expression of type ClusterOutputs>
}
```

`ClusterOutputs` is auto-derived from the cluster declaration as a struct with one field per member, each typed
`Optional(member.outputType)`. A field set to `Some(value)` declares a new output for the corresponding member;
a field set to `None` indicates no output update for that member in this invocation. Constructing `Some(...)` is
itself the act of declaring new state — the host does no comparison against any prior output value (see
*Virtual Modalities* in `README.md`).

Cross-member coupling travels through `acc`; multi-member updates from a single input travel through `outputs`.

### Compiler Obligation

The compiler in the admin tool, given a cluster declaration and a member's transformation expression:

* Type-checks the expression against the bindings (`input : member.inputType`, `acc : accType`) and against the
  return type `{ acc: accType, outputs: ClusterOutputs }`.
* Verifies streamability of every operation on stream- and aggregate-typed sub-expressions by per-operation
  classification against the table in *Streamability Classification*.
* Verifies *Structural Alignment* (see above): the joint walk of input and output type trees is satisfiable
  under bounded captures only.
* Computes `frame(T_in, E)` recursively, lays out each frame, and emits the total stack budget and per-frame
  layout in the bytecode preamble.
* Lowers the expression tree into per-position dispatch bytecode driven by the decoder's traversal of the input
  value; push and pop instructions correspond exactly to entry into and exit from input aggregates (see
  *Transformation Language Binary Representation*).
* Rejects programs that fail any of the above: not type-correct, an excluded operation, structurally unalignable,
  total stack budget exceeds what the target device advertises, or attempting effects outside the permitted set.

The device-side VM has no notion of the source language. It executes a flat bytecode program against the
accumulator slot, the run stack, and an input-event stream, producing per-member output events as it goes —
nothing more.

## Transformation Language Binary Representation

The transformation program is sent to the devices dynamically and can be updated by the user at any time. All devices must support
a VM to run these transformation programs in memory. The point of the binary representation is therefore to enable a very
small VM implementation. Since these program are "just" statelessly transforming values, efficiency is less important than
fitting small microcontrollers.

The binary representation reuses this type system: a compiled transformation is a value of type `CompiledTransformation`,
encoded under *Values Binary Representation*. The same decoder a device already needs to consume SCAN values consumes its
bytecode. Opcodes and operands bit-pack across instructions; frame descriptors, jump tables, and scalar widths share the
discriminator-and-bit-packing rules of every other union. There is no separate bytecode format to specify — the compiled
program is just a value.

### Virtual Machine Model

The VM observes the input value's structural traversal as a sequence of **events**: entry into and exit from each
structural sub-element, each scalar, each chunk of bulk bytes. The full vocabulary is enumerated as `EventKind`
in *Handlers and Triggers* below; it is internal to the bytecode VM and not part of the on-the-wire protocol.

The VM exposes three regions of state, all stack-discipline (no heap, no allocation at runtime):

* A **frame stack** of typed slots that mirrors the input decoder's depth: a frame is pushed on entry to an input aggregate and
  popped on exit. Frames hold values that must outlive a single event — captures, fold accumulators, union discriminators.
* An **operand stack** of 64-bit slots used within a single handler for expression evaluation. It empties between events.
* An **output emitter** the device's encoder drains. The bytecode produces output events drawn from the same
  vocabulary (see *Instructions* below — the `Emit*` family); the encoder serializes them per *Values Binary
  Representation*. The bytecode never sees wire bytes.

Execution is event-driven. The runtime fetches the next event, looks up a handler for the `(input position, event)`
pair, and runs it to completion. Handlers do not suspend. Total RAM is `frameStackBytes + operandStackSlots × 8` plus a small
output buffer; all three are statically known per program.

### Top-Level Structure

```
CompiledTransformation {
    memory:           MemoryRequirements,
    frameDescriptors: Array(FrameDescriptor),
    jumpTables:       Array(JumpTable),
    handlers:         Array(Handler)
}
```

A device parses `memory` first and decides whether it can accept the program before allocating buffers; only then does it load
the rest. The four sections are independent — none cross-references the others except by zero-based index.

### Memory Requirements

```
MemoryRequirements {
    frameStackBytes:   VariableLengthInteger(2),
    operandStackSlots: VariableLengthInteger(1),
    maxFrameDepth:     VariableLengthInteger(1)
}
```

* `frameStackBytes` — peak total bytes of the frame stack across all reachable input traversals. Computed by the compiler as
  the maximum over all reachable input traversals of the sum of `FrameDescriptor` sizes simultaneously on the stack.
* `operandStackSlots` — peak operand stack depth in 64-bit slots, taken as the maximum across all handlers.
* `maxFrameDepth` — peak number of frames simultaneously on the stack, equal to the maximum nesting of input aggregates the
  program touches.

A device that cannot satisfy these requirements rejects the program at load time and reports the shortfall through
`scan.health`. No instruction has been executed at that point.

### Frame Descriptors

```
FrameDescriptor {
    slots: Array(SlotType)
}

SlotType = SlotI8  | SlotI16 | SlotI32 | SlotI64
        |  SlotU8  | SlotU16 | SlotU32 | SlotU64
        |  SlotF32 | SlotF64
        |  SlotBytes { size: VariableLengthInteger(2) }
```

A frame descriptor declares the layout of one kind of frame. Slots are laid out in declaration order, byte-packed (no inserted
padding); their sizes follow the slot type — 1, 2, 4, or 8 bytes for the integer / float widths and `size` bytes for
`SlotBytes`. The total frame size is the sum of slot sizes.

Slots are addressed by their zero-based index. The slot's declared type fixes how `LoadSlot` widens (sign-extending for signed
integer slots, zero-extending for unsigned, no-op for `SlotI64` / `SlotU64` / `SlotF64`) and how `StoreSlot` narrows
(truncating). Float slots permit only float instructions; integer slots permit only integer instructions. Type mismatch is a
compile-time error; devices need not check at runtime.

`frameDescriptors[0]` is the top-level frame. It is pushed automatically by the runtime before any handler runs, with its
leading slots populated from the prior cluster accumulator value (see *Accumulator Loading* below). All other frames are
pushed and popped explicitly by `PushFrame` and `PopFrame` instructions.

### Jump Tables

```
JumpTable {
    targets: Array(SignedInteger(2))
}
```

A jump table is a small array of instruction-relative offsets within the handler that consults it. The `JmpTable` instruction
pops an integer index off the operand stack and branches to `targets[index]`. The index must be in range; out-of-range indices
are a compile-time error. Tables are referenced by their zero-based index in the program's `jumpTables` array. Multiple
handlers may share the same table when the compiler detects identical dispatch patterns.

### Handlers and Triggers

```
Handler {
    trigger: HandlerTrigger,
    code:    Array(Instruction)
}

HandlerTrigger = ProgramStart
              |  TransportEnd
              |  AtNode { node: VariableLengthInteger(2), event: EventKind }

EventKind = OnStartField | OnEndField
         |  OnConstructor
         |  OnStartContainer | OnEndContainer
         |  OnStartStream
         |  OnStartItem | OnEndItem
         |  OnScalar | OnChunk
```

`OnStartContainer` / `OnEndContainer` fire on entry to and exit from finite containers (`Array`, `Set`).
`OnStartStream` fires on entry to a `Stream`; there is no end event because a stream has no terminator on the
wire (see *Stream(T)* in *Per-Type Encoding*). `OnStartItem` / `OnEndItem` wrap each item of an itemized
container or stream; `OnChunk` carries one batch of bulk bytes and never co-occurs with item events for the same
sub-element.

A handler runs in response to one specific event at one specific input position. The trigger identifies that position:

* `ProgramStart` — fires once, before any input event, after the runtime has pushed the top-level frame and loaded the prior
  accumulator into it.
* `TransportEnd` — fires once, after the last input event, while every frame is still on the stack.
* `AtNode { node, event }` — fires when the input traversal reaches `event` at input-tree node `node`. Node IDs are assigned by
  the compiler in a canonical pre-order walk of the input type; the runtime maintains the current node as it advances and uses
  it to look up handlers.

The handler list is unordered; the runtime builds whatever dispatch index it needs at load time. Multiple handlers for the
same trigger are not allowed — the compiler fuses overlapping work. A program with no handler for some `(node, event)` does
nothing at that event, which is the normal case for events the program does not observe.

A handler runs to completion before the runtime fetches the next event. There is no in-handler suspension. The handler
terminates with `EndHandler`, which returns to the dispatch loop. Falling off the end of `code` without `EndHandler` is a
compile-time error.

### Instructions

```
Instruction = ConstI    { value: SignedInteger(8) }
           |  ConstF    { value: FloatingPoint(8) }
           |  Dup | Drop | Swap

           |  LoadSlot   { depth: VariableLengthInteger(1), slot: VariableLengthInteger(1) }
           |  StoreSlot  { depth: VariableLengthInteger(1), slot: VariableLengthInteger(1) }
           |  AddToSlotI { depth: VariableLengthInteger(1), slot: VariableLengthInteger(1) }
           |  AddToSlotF { depth: VariableLengthInteger(1), slot: VariableLengthInteger(1) }

           |  PushFrame  { descriptor: VariableLengthInteger(1) }
           |  PopFrame

           |  AddI | SubI | MulI | DivI | ModI | NegI
           |  AddF | SubF | MulF | DivF | NegF
           |  And  | Or   | Xor  | Not  | Shl  | Shr  | Sar
           |  EqI  | LtI  | GtI  | LeI  | GeI
           |  EqF  | LtF  | GtF  | LeF  | GeF

           |  Jmp        { offset: SignedInteger(2) }
           |  JmpIfZero  { offset: SignedInteger(2) }
           |  JmpTable   { table:  VariableLengthInteger(1) }
           |  EndHandler

           |  ReadScalar { kind: ScalarType }
           |  ReadChunk  { depth: VariableLengthInteger(1), slot: VariableLengthInteger(1),
                           count: VariableLengthInteger(2) }

           |  EmitStartField     { index: VariableLengthInteger(2) }
           |  EmitEndField       { index: VariableLengthInteger(2) }
           |  EmitConstructor    { index: VariableLengthInteger(2) }
           |  EmitStartContainer { kind:  ContainerKind }
           |  EmitEndContainer   { kind:  ContainerKind }
           |  EmitStartStream
           |  EmitStartItem | EmitEndItem
           |  EmitScalar         { kind:  ScalarType }
           |  EmitChunk          { depth: VariableLengthInteger(1), slot: VariableLengthInteger(1),
                                   count: VariableLengthInteger(2) }

ScalarType = U8 | U16 | U32 | U64
          |  I8 | I16 | I32 | I64
          |  F32 | F64
          |  Vli { maxBytes: VariableLengthInteger(1) }

ContainerKind = ArrayKind | SetKind
```

The instruction set has 55 variants, a 6-bit discriminator that bit-packs with the next sub-byte operand and with the
discriminator of the following instruction. A `Dup` occupies 6 bits on the wire; a `LoadSlot 0, 3` is roughly three bytes
after VLI encoding.

Behaviour by group:

* **Constants and operand stack.** `ConstI` / `ConstF` push a literal. `Dup`, `Drop`, `Swap` are the standard operand-stack
  primitives. The operand stack is untyped 64-bit slots; the instruction's variant determines whether the value is treated as
  integer or float.

* **Slot access.** `LoadSlot { depth, slot }` pushes the value of slot `slot` in the frame `depth` levels below the top of the
  frame stack (depth 0 = current top), widened to 64 bits according to the slot's declared type. `StoreSlot` pops one operand
  and narrows to the slot's declared type. `AddToSlotI` and `AddToSlotF` are read-modify-write shortcuts: pop one operand, add
  to the slot in place. They exist because fold updates are the dominant pattern in compiled programs and would otherwise
  cost three instructions each.

* **Frame management.** `PushFrame { descriptor }` pushes a fresh frame whose layout is `frameDescriptors[descriptor]`. All
  slots are zero-initialised. `PopFrame` removes the top frame. Pushing or popping mismatched against the input decoder's
  structural state is a compile-time error.

* **Arithmetic, logic, comparison.** Each pops its operands and pushes the result. Integer / float variants must match the
  type of operands the bytecode placed on the stack. Comparisons push 0 or 1 as integer. Bitwise shifts (`Shl`, `Shr`, `Sar`)
  take a 64-bit shift amount masked to its low 6 bits before shifting.

* **Control flow.** `Jmp { offset }` advances the current handler's instruction cursor by `offset` (positive forward, negative
  backward; 0 is invalid). `JmpIfZero { offset }` pops one integer and jumps if it is zero. `JmpTable { table }` pops one
  integer index and branches by `jumpTables[table].targets[index]`. `EndHandler` returns to the dispatch loop.

* **Event input.** `ReadScalar { kind }` consumes the active `Scalar` event from the decoder and pushes its value at the width
  given by `kind`. `ReadChunk { depth, slot, count }` consumes `count` bytes from a `Chunk` event and writes them into the
  frame slot at `(depth, slot)`; the slot must be a `SlotBytes` of size at least `count`.

* **Event output.** Each `Emit*` instruction produces one output event. Structural events (`EmitStartField`, `EmitEndField`,
  `EmitConstructor`, `EmitStartContainer`, `EmitEndContainer`, `EmitStartStream`, `EmitStartItem`, `EmitEndItem`) carry their
  own descriptor. `EmitScalar { kind }` pops one operand and emits a `Scalar` event of width `kind`. `EmitChunk` emits raw
  bytes from a frame slot region.

### Accumulator Loading

The prior cluster accumulator is loaded into the leading slots of the top-level frame before `ProgramStart` runs. The compiler
reserves slots in `frameDescriptors[0]` whose layout matches the wire form of `accType`; the runtime decodes the persisted
accumulator into those slots in lock-step with their declared widths. From the bytecode's perspective, `acc` is data already
sitting in known slots when the first instruction runs.

The new accumulator is emitted as part of the program's output: the compiled output type is structurally
`{ acc, outputs }` (per *Accumulator and Outputs* in the surface language), so the bytecode emits a
`StartField(0)` / `EmitScalar` / ... sequence for the `acc` field as it would for any other field. The runtime intercepts that
sub-sequence and persists the bytes for the next invocation.

### Worked Example

The transformation `acc + sum(input.values)` over `input: { tag: Byte, values: Stream(Double) }`, `accType: Double`, output
`{ sum: Option(Double) }` (so the cluster's compiled output type is `{ acc: Double, outputs: { sum: Option(Double) } }`)
compiles to:

```
memory:
    frameStackBytes:   16
    operandStackSlots: 2
    maxFrameDepth:     2

frameDescriptors:
    [0]: { SlotF64 }              -- top-level: prior acc
    [1]: { SlotF64 }              -- stream:    running total

jumpTables: []

handlers:
    trigger = ProgramStart
    code    = [ EndHandler ]

    trigger = AtNode { node = <values stream>, event = OnStartContainer }
    code    = [ PushFrame 1
              , ConstF 0.0
              , StoreSlot 0, 0      -- frame[0].total = 0
              , EndHandler ]

    trigger = AtNode { node = <stream item>, event = OnScalar }
    code    = [ ReadScalar F64
              , AddToSlotF 0, 0     -- total += incoming
              , EndHandler ]

    trigger = TransportEnd
    code    = [ LoadSlot 1, 0       -- acc (one frame down)
              , LoadSlot 0, 0       -- total (current frame)
              , AddF                 -- acc + total
              , Dup
              , EmitStartField 0
              , EmitScalar F64       -- new acc
              , EmitEndField 0
              , EmitStartField 1
              , EmitStartField 0
              , EmitConstructor 1    -- Some
              , EmitScalar F64
              , EmitEndField 0
              , EmitEndField 1
              , EndHandler ]
```

