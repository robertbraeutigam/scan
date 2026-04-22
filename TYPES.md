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

The language is very minimal, consisting of just a few syntactic elements.

The top level element is a union type definition in the following form:

```
<name>(parmeters) = <constructor definition 1> | ... | <constructor definition N>

```

Where constructor definitions may define a structure, which is sequence of name - type reference pairs, such as:

```
LogLine {
   severity: Severity,
   time: Timestamp,
   line: String
}
```

If the type defines only one constructor, the syntax can collapse to just the constructor itself,
in which case the type will be named the same as the given constructor.

Worth noting, that parameters to a type can be other types such as item type of an `Array`, but also
normal values, such as `length`, `max` or other information appropriate for the type.

### Built-in "Primitive" Types

This type system defines these built-in types:

* Unit
* FloatingPoint(sizeInBytes)
* UnsignedInteger(sizeInBytes)
* SignedInteger(sizeInBytes)
* VariableLengthInteger(sizeInBytes)

The `FloatingPoint` type has a size parameter, which is either 4 or 8, corresponding to the standard IEEE float and double.

The `UnsignedInteger` and `SignedInteger` numbers may have sizes of 1, 2, 4 or 8, corresponding to the usual number types:
byte, word, int, long.

The `VariableLengthInteger` is a number stored as a variable number of bytes.
On each byte except the last the highest bit indicates that a byte still follows, which means
the last byte may use the high bit for representing the value itself, it does not have to be 0. This type is
for cases where lower numbers are much more likely, thus this results in more efficient packing. The size parameter
can be any integer from 1 to 8 inclusive, although a VLI of 1 is just a normal unsigned byte.

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

The `Array` type is useful, because the number of items do not need to be written to the wire. However, if the number of items is not known
a-priori there's the `DynamicArray`, which can have a different size in each value.


```
Events = DynamicArray(Event)
```

The downside is, that the length needs to be written onto the wire.

If each item may only be present once, the `Set` can be used. This is useful for representing flags for example:

```
Flags = IPv4 | IPv6

EnabledProtocols = Set(Flags)
```

And there are streams, which are a potentially infinite sequence of values of the given type. For example a live video stream.

```
VideoContent = Stream(Byte)
```

There can only be one stream per message, since one stream can be potentially infinite. 

### Type Parameters

Types can have parameters. For example this type has a value as parameter:

```
Measurement(unit: String) = Double
```

This `Measurement` type that is a measurement of something that can be expressed with a "unit". Volts, Amperes, Kg, %, etc. Since the type
parameter is a value, it is essentially a constant and not a runtime value. It will not be encoded with the actual `Double` value.

Types can have type parameters as well:

```
Option(contentType: Type) = Unit | contentType
```

Which defines the standard `Option` type to denote a potentially missing value. The union type can only unite other types not values.

### Constraints

For all number types (all primitive types except `Unit`), following constraints are available:

```
TableLegNumber = Byte {1,2,3,4}
```

Or

```
TableLegNumber = Byte {1 to 4}
```

Or

```
TableLegNumber = Byte {min 1, max 4}
```

## Types Binary Representation

This binary represenation is used by devices to tell other devices about data and command types, so it is parsed
dynamically runtime by devices, but only once when they connect. It is therefore more important to have an easy
parsing instead versus an efficient encoding.

TODO

## Values Binary Representation

Values are sent between devices during runtime. It is what the network is designed for, therefore it is important
to have the most space efficient encoding possible.

TODO

## Subset Determination

When invvoking commands with some data value, possibly a transformed one, it is important to be able to tell whether
that value fits the type the command expects. These rules define when that is the case.

TODO

## Transformation Language

Since this protocol does not define devices at all, the transformation language's goal is to make devices compatible, by
being able to join current data and transform them into a proper format for a command invocation.

TODO

## Transformation Language Binary Representation

The transformation program is sent to the devices dynamically and can be updated by the user at any time. All devices must support
a VM to run these transformation programs in memory. The point of the binary representation is therefore to enable a very
small VM implementation. Since these program are "just" statelessly transforming values, efficiency is less important than
fitting small microcontrollers.

TODO

