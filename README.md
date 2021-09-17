# SCAN, Simple Control and Acquisition Network

*Version 0.1, 2021-05-25*

SCAN is an open, free, easy-to-use, extensible, DIY-friendly
way to collect data and control devices on a network.

## Goals

The main considerations driving the design of this protocol:

- **Security first**! State-of-the-art end-to-end encryption, perfect forward secrecy, etc.,
  as simply as possible with no complex certificate management nor third-party involvement.
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
- Does **not require** a complete and **perfect list of codes** nor a **complete dictionary** of
  some semantic identifiers, nor a perfect usage on the part of the devices to be *fully* usable.

These other design decisions were also used:

- Minimal setup required, ideally plug-and-play in most cases.
- Should work over the internet and in any network topology, trusted or not.

## Solution Overview

SCAN is defined on top of the Internet Protocol (IP), so off-the-shelf internet networking tools 
and devices, such as routers, repeaters, cables, WiFi or even VPNs,
can be used to build the "physical" network.
There is no requirement on the physical network topology, nor on any particular
network service to be available, as long as individual devices can communicate with each other at least
in one direction.

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

Additional transparent network elements, like proxies or gateways, to expand
the network or connect networks on separate physical infrastructure is supported.

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
excluding the frame header.

A logical connection is a connection between two devices identified by their public static keys. All
devices have a static key pair, the public part of which identifies the device uniquely and securely
on the network. There 
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
* Frame type (1 byte) (clear-aad)
* Number of following bytes (2 bytes) (clear)

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
* (Payload) (optional encrypted byte array)

The Noise Protocol Name is the exact protocol used for the following handshake
and data exchange. If the recipient disagrees with the protocol it must close the
logical connection.

The protocol name has to be included in the *prologue* of the Noise Handshake to
make sure it has not been tampered with.

All devices must support the following protocols:

* **Noise_KK[psk1]_25519_AESGCM_SHA256**: This is a bi-directional protocol. 
The '*KK*' variant comes from the fact, that the frame already contains the 
public static key of both the sender and responder. So both static keys are
already *K*nown.
* **Noise_K[psk1]_25519_AESGCM_SHA256**: This is an uni-directional protocol,
suitable only for sending. After this message the device is free to send
payload messages immediately.

The handshake makes sure that both parties actually possess the secret
private part of their static identity. In essence this makes sure that
both devices are who they pretend to be. This takes care of authentication.

Both devices should however also do *authorization*, that is, check
what the other device is allowed to do. Devices are free to implement
any allow-, or deny-listing based on the public static key, or implement
other restrictions based on the public static key.

All devices must however support handshaking with a given PSK (Private Shared Key).

A PSK is a way to invert the authorization strategy above. Instead of
defining restrictions on the target device where the action to be restricted takes place,
that target device can instead generate a PSK, essentially representing a group of devices,
and defining group-level privileges, instead of one by one.

Every device must come with a unique PSK already set up for administrative (full) access,
or must be only capable of setting an administrative PSK before any other messages
are processed.

Note, that the handshake does not identify the PSK used explicitly. The responder
might therefore need to try multiple PSKs to know which one the initiator is using.
The protocol is designed so a single try takes a single hashing operation only.

This frame may contain an optional payload. If the handshake is complete after
the initial handshake message, the initiator is free to send the first payload
in the same message. This allows devices that do not maintain a connection,
maybe because they are off-line most of the time, to send messages to other
parties as quickly and as compactly as possible.

#### Frame type: 02 (Continue Handshake)

Sent potentially by both parties. It continues the handshake after it has been initiated.
The first continue handshake must come from the responder, then from the initiator
and continue in turn until the connection is established.

For zero-roundtrip protocols, this message will never be sent.

Payload structure:
* Handshake (byte array)
* (Payload) (optional encrypted byte array)

If the handshake is complete after this continued handshake message, the message 
may then contain the first message payload. This payload is however limited to this
frame only.

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
* Message Id (4 bytes)
* Payload (encrypted)

After a message is sent, the sender is required to "rekey" its sending key. After a message
is received, the receiver is required to "rekey" its receiving key. This means
messages are perfectly forward secure, as each message is encrypted with a different key.

If any decryption errors occur, meaning that for some reason the sender and receiver becomes
out of sync, the connection must be closed.

All encryption happens with "nonce" of all zero.

The Message Id is a unique number in the current communication. Senders must never repeat
the same Message Id in a session. If this can not be fulfilled, the connection must be closed.

If a message is too large to fit
into one Application Message frame, it must be fragmented, with each fragment having the
same Message Id.

The last fragment of a message must have a payload that is less than the maximum size of the message.
Conversely, if the payload length (in the frame header) is 65535 (the maximum), there must be a
following fragment. If the last fragment's length is exactly 65535 by chance,
then a new fragment needs to be sent with 0 net payload, which is the length
of the decrypted application level payload. Note: 0 net payload will not result
in a 0 message length.

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
2. Responder sends "Continue Handshake", if not a zero-roundtrip protocol is used.
3. If handshake not concluded, Initiator also sends "Continue Handshake".
   If handshake not concluded after that, go to 2.
4. Both parties are now free to send, stream any number of Application Messages in any order including in parallel. Note: If
a zero-roundtrip protocol is used only the initiator can send.

Sending "Initiate Handshake" or "Continue Handshake" messages after the connection has been
established is an error. The logical connection must be closed as a result.

Any party may close the connection at any time for any reason. The initiator is
free to re-open the connection at any time.

The handshake message that completes the handshake may optionally already contain the first payload.

### Address Resolution

To be able to establish a "physical" TCP/IP connection between parties having static keys,
there must be a way to query the network what actual TCP/IP address is associated with
a given static key. This mechanism is not unlike what IP does to query the network
for a MAC address for a given IP.

It is possible that devices will be available through non-static IP addresses, for example
through WiFi with DHCP. For this reason a device should attempt an address resolution every time before
establishing a TCP/IP connection. The device may use cached values of already resolved TCP/IP
addresses, it must however fall back on a resolution process if connection can not be established
with cached value.

#### Configuration

Devices may support a statically configured resolution table.

This may be the case for devices that are not on any "local" network. Connected through
untrusted networks, such as cellular networks or other host networks.

The configured table may be composed of multiple key and address (or hostname) pairs, or
contain a wildcard route for all keys to a single host. The latter enables the device
to connect through a SCAN gateway.

#### Local Network

If a device does not have a statically available address for a static key,
a local TCP/IP network resolution has to be attempted through a multicast UDP message.

The UDP packet needs to be sent to 224.0.0.1, port 11372. The contents as follows:
* 01 (fixed byte)
* Target query static keys... (32 bytes each)

The query can contain any number of target addresses between 0 and 100. All the
host devices with the listed keys must respond to this query. A query with 0
target keys is a wildcard query, which means *all* devices in the local network
must respond.

Note that wildcard queries may or may not return all devices depending on 
online/offline status, or network topology.

All devices must be subscribed to the above multicast address and must reply when they are a target
of a query with a UDP reply. Contents as follows:
* 02 (fixed byte)
* Static keys... (32 bytes each)

This reply tells the requester that these static keys are reachable at
the address this UDP Packet is from. A device, such as a gateway,
may represent multiple devices on the local network, that is why
multiple static keys may reside at the same IP address.

#### Non-local Networks, NAT or Firewalled Networks

If one or more, or even all of the devices are scattered across different
IP networks, local address resolution will obviously not work. In case
any of these devices need to initiate a connection to another device,
they need to be configured with a static resolution table as 
described above.

It may be the case that devices behind firewalls or NATs will only be able
to initiate connections, but not receive them. This must be taken into account
when configuring the application layer later.

## Application Layer

The application layer is defined by specific payloads in Application Messages and their
choreography after a logical connection on the network layer has been established.

The Initiator of the network connection is called a Controller Device on this layer, while the Responder
is called the Controlled Device. A single connection only allows for one side to be the Controller.
This means, that since existing connections must be re-used, no two devices can control each other at the same time.

Note that in any setup the Controller and Controlled roles may be defined independently of the Devices
themselves. For example in a single Light and a single Button setup, we may traditionally think,
that the Button "controls" the Light, but in fact the Light can be set up to "control" the Button.
That is, to initiate a connection to the Button and based on Data the Button generates control its own
operation. In this case the Light will obviously not "control" the Button in the traditional meaning,
but will nonetheless
assume the role of the "Controller" for the purposes of this specification. It will send Commands
to read the Data from the Button.

The application layer is a request-response type protocol. The Controller may make requests and
the Controlled may respond. Unlike protocols such as HTTP, this protocol allows many requests and
responses to be sent in parallel, including many streaming responses or multiple responses for
a single request.

### Data

Devices may generate Data during their operation. Data may be a mix between states the device
has or measured values from the outside world. This Data is represented in a unified format,
which is designed to be used even without full understanding of the semantics involved.

Data has two main building blocks, its *identification* and its *content*.

#### Data Identification

All Data has a base **name**. This name carries the semantics of the data, like 'throttle_position',
or 'geo_position'. Some standardized semantics are defined in this specification to enable a more
seamless integration between devices. However, this specification is explicitly designed to not
rely solely on standardized semantics.

Data also always has a **time**. This means all Data is actually a *time series*. The *time*
of a single Datapoint is the time this data was first generated or measured. For measured values this is the
time the value was measured at, for states it is the time the state was established, for
aggregations it is the time the aggregate was first applicable to, for example an end of an
aggregation interval.

Specifically, *time* is not always the current time. Devices may under some circumstances
be required to submit values from the past. These circumstances may range from the device
actually storing historical values and submitting them as Data, to the Device losing
the connection to some other party and may be required to "catch up" the other party
on changes when re-establishing a connection.

Data may be an aggregation of an underlying value. Currently supported aggregations are:
* sum
* count
* min
* max

All aggregations also have an **aggregation name**. This enables the aggregation
of the same value into multiple time-frames or according to different parameters. For
example a Device may emit min/max values for weekly and monthly aggregations for the same
underlying value.

Data may have **tags** attached to them. Tags are key-value pairs, where the value comes from a limited set.
Each tag defines a separate, small dimension for the Data, for example: sensor=1/2/3/4, ip_type=TCP/UDP,
region=eu/us, etc. Note that tags must be of low cardinality, that is, from a known limited set of
values. This specifically excludes Identifiers, Text and other unlimited or large value sets.

To summarize, the *identification* of a Data point includes the following information:
* Name
* Time
* Aggregate name and function (optional, single)
* Tags (optional, many)

#### Data Content

All Data in SCAN is typed. Each semantic name must have a specific type of content attached, 
identified by a media type. That is, all names must produce the same type.

### Message Format

There are two types of messages: requests and responses.

Requests are sent by the Controller and the Responses sent by the Controlled. The Controlled
can not send requests back nor can the Controller send responses.

#### Request

Requests contain one of the following *actions*:
* OPTIONS: Get meta-information from the Controlled about Data, Controls and Wiring, as well
  as auxiliary information about the device itself
* STREAM: Instruct the Controlled to send Data updates continuously. The Controlled
  must get the Controller up-to-date on all Data as soon as possible, then can
  send Data as specified by its own logic. For example only when something changes, or
  at periodic intervals, or a mix of both.
* USE: Use the specified control on the Controlled Device.

Content:
* Action (1 byte, OPTIONS: 1, STREAM: 2, USE: 3)
* Number of headers (1 byte)
* Headers

Headers consist of following records, where the number of these records
is specified by "Number of headers":
* Header type (1 byte)
* Value (string)

Following headers are specified:
* 01: Locale
* 02: Accept-Type
* 04: Content-Length

The Message Id from the Network Layer will be used in the answer to reference this request. There are no
restrictions in what value this is, the Controller can manage these as it sees fit.

Headers are all optional. If the Controlled does not know or implement a specific header,
then it is free to ignore it.

The Locale will be used to translate names, keys and other text by the Controlled.

The Accept-Types field describes which Media-Types the Controller is able or willing
to accept as answer.

The Content-Length is the length of the following content. This must be supplied if known.

#### Response

Responses are always in reaction to a previous request, therefore always refer
back to its Request.

There may be multiple responses to a single request. Responses may be streams
of unlimited length.

Content:
* Reference Id (number, 4 bytes)
* Number of headers (1 byte)
* Headers
* Content

Following headers are specified:
* 01: Locale
* 03: Content-Type
* 04: Content-Length

The Reference Id is the Message Id of the Request.

The Locale defines what language the content is in, if any.

The Content-Type specifies what the content is.

The Content-Length is the length of the following content. This must be supplied if known.

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

## Technical Discussions

### Backpressure

Backpressure is the mechanism by which consumers of messages can tell producers to slow
down producing messages in the event that they can't consume them fast enough.

In SCAN backpressure is done via the already built-in mechanisms of TCP. If a consumer
is not ready to process another message it will not empty the TCP/IP receive buffer,
therefore eventually the buffer runs full, which will result in not acknowledging
packets. This will eventually result in the send buffer of the producer to fill up as well.

All devices must respond to backpressure by *dropping* messages, as not dropping messages,
such as queueing them will likely not alleviate the underlying problems. This only works
if both commands and data are designed to *lose only resolution* when losing or dropping
messages, not lose the command or data itself.

This in essence means that both commands and data must be designed to be *stateless*. All
supplied data needs to stand in its own, without referencing older data points. 
For example instead of supplying data like "3L flow since last data", use absolute
measures like "34566L flow total". Commands
must be designed to be absolute as well. For example instead of having commands like
"+10% Power", commands need absolute values like "70% Power".

### Quality of Service

Data does not have any acknowledgements outside of TCP layer acknowledgements. Those
however do not indicate a successful processing or even reception at the endpoint,
since proxies or other intermediaries may be in the network path.

It doesn't need one though, because any issues in reception will eventually result
in the connection to be closed. A new connection will then submit stateless data
again, which will catch up the other party. Some resolution might get lost however.

Commands do have an explicit acknowledgement and therefore may need to be repeated
if that acknowledgement is late or does not arrive. For this reason all commands
must be *idempotent*. This just means that if the same command is repeated several
times for some reason, the end-result must be the same as if the command
was executed only once. Not counting the potential overhead of network and compute usage.

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

### There are multiple Meters over the Cell-Network sending data to a central Server

## Appendix B: Media-Types

TODO:
All Data in SCAN is typed. That means each semantic name carries the type information with it,
which must be one of the following things:
* Enumeration
* Counter
* Geo
* Event
* Raw
* Measurement

An *Enumeration* is a set of string values. A single member from this set may be the content
of an enumeration type data point. There are predefined enumeration types already defined in
this specification.

A *Counter* is an monotonically increasing integer value that may reset to zero from time to time.
Typical examples include non-persistent counters for some event that resets when the device is
re-started.

A *Geo* data type is a geographical coordinate.

An *Event* has no value, just a name. Such as: Alarm, Error, Navigation Point Reached, etc.

*Raw* types have both a media-type and a byte array content. These are 

## Appendix C: Terminology

* **Initiator**: The party who establishes a network layer connection to another party.
* **Responder**: The party who receives a network layer connection from another party.
* **Device**: A party in a SCAN network. Although the term "Device" implies a physical
machine, a Device may be fully software implemented, or one physical device may even represent
multiple logical Devices.
* **Controller**: The *Initiator* on the application layer. The party who invokes commands
on the other party.
* **Controlled**: The *Responder* on the application layer. The part who responds to commands
from the other party.
