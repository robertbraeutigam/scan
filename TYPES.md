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

The right-hand side is a single type reference, optionally with constraints. Examples:

```
Byte = UnsignedInteger(1)
Double = FloatingPoint(8)
String = DynamicArray(Byte)
TableLegNumber = Byte {1 to 4}
```

An alias is distinguished from a type definition by its right-hand side. If the RHS contains `|`, or ends in a `{ ... }` struct body, it defines a new type; otherwise it is an alias. The collision case — an RHS that is a bare identifier, which could in principle be read as a type definition with a single no-data constructor — is always read as an alias, since such a type would be isomorphic to `Unit` and carries no information. The collapse form (`Foo { ... }`) still covers the useful struct-definition cases.

### Constraints

Number types may be refined to a subset of values, e.g.:

```
TableLegNumber = Byte {1 to 4}
```

Several equivalent forms exist (such as `{1,2,3,4}` and `{min 1, max 4}`), and constraints compose. The full grammar is given in the *Type Reference* section below.

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

