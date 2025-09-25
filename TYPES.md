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

The language is very minimal, consisting of named definitions of types in the form of:

```
<name> = <type definition>
```

Where type definitions don't have any specific syntax other than "instantiating" already existing types, like this:

```
Button = Boolean
```

With the only caveat that types may have parameters, either named or unnamed, either as other types or even values, such as:

```
HourlyReadings = Array(24, Reading)
```

Even sequences and other constructs are using special built-in types, instead of dedicated syntax. (See below)

### Built-in Types

* Unit
* FloatingPoint(sizeInBytes)
* UnsignedInteger(sizeInBytes)
* SignedInteger(sizeInBytes)
* VariableLengthInteger(sizeInBytes)

Type definitions may have parameters themselves as above. Type parameters may be other types or even values as above.

The `FloatingPoint` type has a size parameter, which is either 4 or 8, corresponding to the standard IEEE float and double.

The `UnsignedInteger` and `SignedInteger` numbers may have sizes of 1, 2, 4 or 8, corresponding to the usual number types:
byte, word, int, long.

The `VariableLengthInteger` is a number stored as a variable number of bytes.
On each byte except the last the highest bit indicates that a byte still follows, which means
the last byte may use the high bit for representing the value itself, it does not have to be 0. This type is
for cases where lower numbers are much more likely, thus this results in more efficient packing. The size parameter
can be any integer from 1 to 8 inclusive, although a VLI of 1 is just a normal unsigned byte.

All values are stored in big-endian ordering.

### Basic Type Definitions

Types are defined thusly:

```
Byte = UnsignedInteger(1)
```

This defines a `Byte` type that is a 1 byte sized `UnsignedInteger`. This is how this type is defined in the standard library available to all 
definitions to use.

More examples:

```
Long = UnsignedInteger(8)
SignedLong = SignedInteger(8)
Float = FloatingPoint(4)
Double = FloatingPoint(8)
```

### Aggregate Definitions

Contrary to other definition languages, there is no specific syntax for defining aggregated types, instead they
are defined by using built-in types. For example:

```
HourlyMeasurements = Array(24, Measurement)
```

Array is a built-in type, which needs the exact number of items as parameter. There is another type, which allows for arbitrary
item counts at runtime by also serializing the array length:

```
Events = DynamicArray(Event)
```

There's a built-in type for structures:

```
LogLine = Struct)
   severity: Severity,
   time: Timestamp,
   line: String
)
```

There's a built-in type for unions:

```
False = Unit
True = Unit

Boolean = Union(False, True)
```

And there are streams, which are potentially infinite sized values. For example a live video stream.

```
VideoContent = Stream(Byte)
```

There can only be one stream per message, since one stream can be potentially infinite. A stream in any message will
be sent last in the message, so all other data will be already available.

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

