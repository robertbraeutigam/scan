# SCAN, Simple Control and Acquisition Network

*Version 0.1, 2021-05-25*

SCAN is an open, free, easy-to-use, extensible, DIY-friendly
way to collect data and control devices on a network.

## Goals

These are the main considerations driving the design of this protocol:

- **Security first**! State-of-the-art end-to-end encryption, perfect forward secrecy, etc.,
  as simply as possible with no complex certificate management nor third-party involvment.
- **Minimal effort** implementations. The effort to implement compatible devices
  should be linear to the extent of features the device will support. I.e.
  simple devices (like a Light source or a Button) should be almost no effort to implement, while
  more complicated devices may require more thorough analysis and design.
- **Driven by individuals**, not industry. I.e. does not have to support complicated
  use-cases which are almost never used, or used only because of historical reasons.
- **DIY** friendly. No proprietary components, processes or certification requirements.
  A private individual should be able to create a compatible device easily and completely
  independently.
- **Transparent**. It should be very easy to discover which devices need what inputs and react to-, or control
  what other devices. Not out-of-band, for example through documentation, but through the actual 
  protocol itself on the fly runtime.
- Does **not require** a complete and **perfect list of codes**, nor a perfect usage on the part
  of the devices to be *fully* usable.

These other design decisions were also used:

- Minimal setup required. Having only two devices should already work, without any
  additional components.
- Should work over the internet and in any network topology, trusted or not.

## Solution Overview

SCAN is defined on top of the Internet Protocol (IP), so off-the-shelf internet networking tools 
and devices, such as routers, repeaters, cables, WiFi or even VPNs,
can be used to build the "physical" network.
There is no requirement on the physical network topology, nor on any particular
network service to be available, as long as individual devices can communicate with each other.

All communication is peer-to-peer and end-to-end encrypted with automatically
rotating keys. There is no central component nor server, and all
communication is secured against third parties even through untrusted infrastructure.

All devices have a uniform interface, which mainly consist of these categories:

* Data. Specification, generation or consumption of individual data points.
* Controls. Specification and invocation of controls that may change the device's state.
* Wiring. Dynamic description of the relationships between data and controls.

SCAN is designed to work in a distributed fashion. All devices are potentially
data acquisition or control devices or both, with only two devices already capable
of working together without any dedicated control device or server. Introducing
more components does not require any central component to exist either, although the
option is available if needed.

All data is defined as a time-series. That is, each data point has a time
when it was produced, and all the related data elements produced at that instant.
Data elements are not defined in terms of format (like 32bit or 64bit numbers),
but in terms of what *kind* of data elements they are. Whether they represent an 
Identification (ID) of some sort, or an Event, or a Measurement (see relevant chapter).

Measurements carry unit information (like Liter, PSI, %, etc.) in addition to a name. This
way data elements don't exclusively rely on their "standardized" semantics, and can be uniformly
stored, visualized or processed even without the exact meaning they might carry.

Controls are a way to influence the device in some way. That can range from toggling a switch
to sending a remote firmware update. All the controls are listed through the uniform interface,
so they are discoverable on the fly.

Wiring in SCAN is the process of connecting data to controls. 
For example connecting the data from a switch to the on/off
control of a light source. Because the *kind* of both the data and control are known, the connection
can be made even if the exact semantics of either side is unknown or not defined.

Auto-wiring, the process of automatically connecting data to controls,
can be achieved using standardized semantics of data, if applicable. Such as 
auto-connecting a Plotter to a GPS Receiver.

## Network Layer

The network protocol is designed to be a "layer" on top of TCP/IP. It is independent and
ignorant of the "application layer" protocol defined in the next chapters.

The main purpose and design goals of this layer are the following:
* Provide **security** features, such as authentication, authorization and anti-tempering features.
* Logical **routing** capabilities, provide a virtual flat topology.
* Enable **multiplexing**, so that multiple logical connections can be established through one TCP/IP connection.
* Enable **message mixing**. Enable a device to interject messages even if another message is currently
  being sent or even streamed indefinitely.
* Intentionally **fragmenting** messages so each fragment can be validated on its own and
  potentially partially processed, without assembling the whole message in memory.

Devices get a peer-to-peer, secure, flat logical topology. That is,
each device is free to directly communicate with any number of other devices. There is no
"server", or any central software or hardware components.

To support every possible TCP/IP topology, each frame contains additional logical routing information and is designed
to be able to be multiplexed, forwarded and proxied. 

The network protocol is packet based, with each packet limited in size to a maximum of 65535 bytes
excluding the frame header. If there is a payload of more than that, or the size is not known
(such as the case with streaming data), the message needs to be fragmented to chunks not more
than 65535 bytes.

A logical connection is a connection between two devices identified by their public static keys. This also
means there
can be at most one logical connection between any two devices, because the unordered pair of public static keys uniquely identifies
a logical connection. Note however, that one TCP/IP connection can tunnel more than one logical connection.

If any parties to a communication encounter any errors in the protocol or interpretation of messages
they must immediately close the logical connection. If the logical connection is the only one in
the "physical" TCP/IP connection, that needs to be closed instead. If not, a close message
needs to be sent.
The initiating party must not retry opening connections more often than 60 times / minute, but may implement any heuristics
to distribute those reconnects inside the minute.

### Types

All number types, if not stated otherwise, are network byte order (big-endian, most significant byte
first) and are unsigned.

A byte array is a concatenation of bytes. The length is not explicitly
supplied but should be available from either the message overall length or by some other means.

A string is a length (2 bytes) followed by a byte array that contains UTF-8 encoded bytes of characters.

### Frame Header

All packets have the following header:

* Source peer (32 bytes) (clear)
* Destination peer (32 bytes) (clear)
* Frame type (1 byte) (clear)
* Length of payload (2 bytes) (clear)

The source is the sending peer's public identity key.
The destination is the public identity key of the target device. Note: none of the devices must necessarily
be a party to this TCP/IP connection. Devices may communicate with proxies, gateways or other
infrastructure components without their explicit knowledge. The sending device can assume
that the receiving device is either the destination itself, or can route the message
to the destination device, and vice-versa.

The frame type describes the payload that follows the header. Devices must ignore messages
with unknown frame type.

### Frame types

#### Frame type: 01 (Initiate Handshake)

Sent from the initiator of the connection immediately upon establishing the
TCP/IP connection. It transmits the first handshake message together with the
Noise Protocol Name.

Payload structure:
* Noise Protocol Name (string) (clear-prologue)
* Handshake (byte array)

The Noise Protocol Name is the exact protocol used for the following handshake
and data exchange. If the recipient disagrees with the protocol it must close the
logical connection.

The only protocol supported at this time is: **Noise_KKpsk2_25519_AESGCM_SHA256**.

The '*KK*' variant comes from the fact, that the frame already contains the 
public static key of both the sender and responder. So both static keys are
already *K*nown.

For the recipient to "authenticate" the sender, that is, to evaluate whether
the two are allowed to talk, this specification requires a shared key (PSK) in all devices that belong to the
same logical "network". All devices that possess this shared key are said to be
in the "same network". Note: this is in terms of "logical" network
and is not the same as being in the same TCP/IP network, subnet or similar lower level
structure.

During the on-boarding of a device, the PSK must be supplied 
for it to be able to communicate. See relevant chapter for
more details.

The protocol name also has to be included in the *prologue* of the Noise Handshake to
make sure it has not been tampered with.

#### Frame type: 02 (Continue Handshake)

Sent potentially by both parties. It continues the handshake after it has been initiated.
The first continue handshake must come from the responder, then from the initiator
and continue in turn until the connection is established.

Payload structure:
* Handshake (byte array)

#### Frame type: 03 (Close Connection)

There can be more than one logical connection in a single TCP/IP connection. In this case
the party trying to terminate a logical connection must use this message to indicate
that the communication is considered terminated. This means the initiator will need
to begin again with an Initiate Handshake if it wants to re-establish the connection.

Note that if the TCP/IP connection has only this one logical connection, then
that needs to be closed instead of sending this message.

This message has no payload.

#### Frame type: 04 (Application Message)

The actual payload of the application layer described in the next chapters. This message
may be sent by both the initiator and responder.

Payload structure:
* Message counter (4 bytes)
* Fragment counter (4 bytes)
* Payload (encrypted)

The message counter is a counter maintained by the sender, starts at 0 and
increases with each message. The counter must increase by at least 1 with each message.

The fragment counter is also maintained by the sender, starts at 0 and increases
by at least 1 for each new fragment of the message. If a message is too large to fit
into one Application Message frame, it must be fragmented, with each fragment receiving
an incremental fragment counter, but the same message counter.

The last fragment of a message is the one where the whole payload is less than 65535 bytes.
Conversely, if the payload length (in the frame header) is 65535, there must be a
following fragment. If the last fragment's payload length is exactly 65535 by chance,
then a new fragment needs to be sent with 0 net payload, which is the length
of the decrypted application level payload. Note: 0 net payload will not result
in a 0 payload length. The payload length will include both counters and the 
authentication code used by the encryption scheme.

Note that the message and fragment counter together uniquely identifies a frame from a
given sender to a given recipient. The recipient must keep track of what message and
fragment counters it received and must close the logical connection if on any messages that don't present a completely
new combination. Note: the sender needs to only keep one "last seen" message counter, and
one "last seen" fragment counter for each in-progress message to fulfill this requirement.

The message and fragment counter together (8 bytes) represent the nonce to be used
to encrypt and decrypt the payload.

If any of the counters overflow the connection must be closed.

Sender must rotate (rekey) its sending key after each message sent. Note: TCP/IP guarantees
the order of delivery, so recipient can do the same. Also this means that different fragments
of the same message will always be encrypted with a different key.

### Message Choreography

All communication happens through TCP/IP connections. There can be only one logical connection
between any two parties. If a logical connection already exists, that must be used.
If not, a new logical connection needs to be established. If there is already a TCP/IP
connection from the source to the target, that TCP/IP connection must be used. If not, a new TCP/IP
connection must be established first.

The *initiator* of the connection is the party that opens the logical connection.
The *responder* is the one that accepts the connection.

It is an error to try to initiate or accept a connection if the peer is already a responder to the
other party. Each party is either an initiator or responder with regards to another party
with which a connection is already open.

The overall choreography of the network protocol is as follows:

1. Initiator opens connection and sends "Initiate Handshake" message.
2. Responder sends "Continue Handshake".
3. If handshake not concluded, Initiator also sends "Continue Handshake".
   If handshake not concluded after that, go to 2.
4. Both parties are now free to send any number of Application Messages.

Sending "Initiate Handshake" or "Continue Handshake" messages after the connection has been
established is an error. The logical connection must be closed as a result.

Any party may close the connection at any time for any reason. It is assumed that
the connection will be re-opened by the Initiator if needed.

## Application Layer

The application layer is defined by specific payloads in Application Messages and their
choreography after a logical connection on the network layer has been established.

The Initiator of the network connection is called a Controller Device on this layer, while the Responder
is called the Controlled Device. A single connection only allows for one side to be the Controller.
This means, that since existing connections must be re-used, no two devices can control each other at the same time.

When the network layer logical connection is established the Controller first
has to send an Request for Data packet. Controlled Device must then
send a Device Description, and then automatically begin sending Data. The Controller
may send Commands occasionally to which the Controlled Device is expected to answer.

When the logical connection is established and the Controlled Device starts sending Data,
it must send any Data about its own state that the Controller might not know about. If there are
hardware states (like switch state, lever state) it must immediately send those to the Controller.

The purpose of this is to allow a broken connection to be re-established without loss of state on
either side of the connection. The Controlled Device is not required to maintain the exact state
it was in. It may decide to fail to a safe state if the Controller is disconnected, but it must
re-synchronize the Controller to its current state upon reconnect.

Note that in any setup the Controller and Controlled roles may be defined independently of the Devices
themselves. For example in a single Light and a single Button setup, we may traditionally think,
that the Button "controls" the Light, but in fact the Light can be set up to "control" the Button.
That is, to initiate a connection to the Button, and based on Data the Button generates control its own
operation. In this case the Light will obviously not send any Commands to the Button, but will nonetheless
assume the role of the "Controller".

### Message Header

Application Messages have a header:

* Message Type (1 byte)

Note that there is no message length defined. Messages are split on network layer level to
smaller packets. Recipients are expected to be able to process partial messages as data
becomes available. This also means that messages can be of unlimited size, for example for
a streaming video feed.

If the messages are longer than suggested through its parameters then any additional bytes
may be ignored. This is for backwards compatibility reasons in case new fields are added
to messages where that is possible.

Note that because of message mixing, a single device may even stream multiple unlimited feeds
in parallel.

### Message Types

#### Message type: 01 (Request for Data)

Sent by the Controller to request continuous sending of data from the Controlled.

Payload structure:
* Language (string)

The Controlled must use the desired language setting if translations for that language are
available. If not, it may default to a factory langauge.

#### Message type: 02 (Device Description)

Sent by the Controlled Device right after a Request for Data is received.

Payload structure:
* Gzipped Json (byte array)

See relevant chapter.

#### Message type: 02 (Data)

Sent by the Controlled Device every time new Data is acquired. This might be reading
from external signals, or internal state changes.

Data must also be sent to synchronize any Controllers to the current hardware / internal
state of the Device upon a connection is established.

Payload structure:
* Gzipped Json (byte array)

See relevant chapter.

#### Message type: 03 (Raw Data)

Sent by the Controlled Device when a large or streaming raw data element is available.

Payload structure:
* 

## Device Description

The "Data and Controls" message describes following information:

* Meta-data about the device
* Data structures
* Controls
* Current wiring

### Structure and Meta-data

```json
{
  "name": "Switch",                   # Official name of the device
  "manufacturer": "Acme Inc.",
  "hardwareVersion": "1.2.1.0",
  "firmwareVersion": "12.3.1",

  "displayName": "Schalter",          # Localized name for display
  "displayIcon": "...",               # Base64 32x32 PNG or JPEG

  "serialNumber": "SW000012",         # Non-defined property

  "i18n": {
    "serialNumber": "Serialnummer"
  },

  "data": [
    ...see 'data definition' chapter...
  ] 
  "controls": [
    ...see 'controls' chapter...
  ]
  "wiring": {
    ...see 'wiring' chapter...
  }
}
```

Notes about meta-data: Devices may use meta-data to auto-wire themselves. In
particular `manufacturer` and `name` should be stable enough to do text
matching on.

The `hardwareVersion` and `firmwareVersion` may be used to determine whether
a given firmware update is compatible with the device. Software devices
must omit `hardwareVersion`, but must use the `firmwareVersion`. Hardware
devices must include a `hardwareVersion`.

Both `displayName` and `displayIcon` are optional. Display tools should use these
prominently if they are available.

Devices may specify any additional (non-standard) meta-data properties in the meta-data
section. Generic display tools should display these along with the standard properties.

If any property has a `display*` prefixed version, any generic display should prefer
those instead of the non-display version.

The `i18n` object may contain translations for the property names of any additional meta-data
properties. Any display software should use these, if available, to present to the user.

### Data Definition

### Controls

### Wiring

## Data

All data is part of a time-series. That means all data packets relate to a single time instant.
Additionally all data items are categorized into the following *kinds*:

* **ID**s: Values with unlimited cardinality. Device IDs, IP, Serial numbers, etc.
* **Tag**s: Values strongly limited or enumerated. Types, Channel, States.
* **Timestamp**s: An absolute instant in time.
* **Counter**s: A monotonic counter that always goes up, but which may reset at times to 0.
* **Geo**: Coordinates. Current position, waypoints, target, etc.
* **Event**s: An event that happened. An error, alert, change of state.
* **Raw**: Binary data with type. Picture taken, part of a video, etc.
* **Value**s: Measured or otherwise observed value. These always have a unit (see relevant chapter).
  For example: tank level, current speed, motor power, battery level, etc.
* **Max/Min/Avg Value**s: Aggregate values. The interval of aggregation is not given explicitly,
  multiple aggregation windows may co-exist. For example: Max temperature of engine, Max speed, Average speed, etc.

These *kinds* exist to allow for a uniform handling to some extent regardless of
actual semantics. For example it is possible to display and compare values by converting them to the same
scale, plot them on a chart (when dimensions are compatible), analyze them, set alert thresholds,
log them, etc.

A single device may produce different data packets, it may describe them in an array of following objects:

```json
{

}
```

## Appendix A: Selected Use-Cases

### Switch controls Light

There are two devices on the network, a Switch and a Light, where
the Switch has two states and controls the Light.

### Throttle controls Engine

There is a Throttle and an Engine on the network. The Throttle controls
the Engine's output power.

### Monitor streaming video from Security Camera

There is a Monitor device which continuously shows the feed coming from
a Security Camera.

## Appendix B: Units
