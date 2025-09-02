# SCAN, Simple Control and Acquisition Network

*Draft Version*

SCAN is an open, free, extensible, DIY-friendly way to collect data and control devices on a network.

## Components

What is SCAN and what does it consist of?

- *This specification*: Defines the protocol and device behavior.
- *Reference implementations*: Java (PC) and C/C++ (Arduino).
- *Reference administration interface*: Android app for network and device management.

## Functional Goals

SCAN supports scenarios such as:

- *Home Automation*: Sensors, actuators, and controls (doors, windows, lights, AV).
- *Building Control*: Access, security cameras, doors, windows, HVAC.
- *Automotive*: Actuators, servos, doors, windows with feedback.
- *Marine*: Boat functions: GPS, plotters, lights, engines, winches, surveillance.

## Non-Functional Goals

The protocol design prioritizes:

- *Security*: End-to-end encryption, forward secrecy, per-device authorization, no central servers or PKI.
- *DIY-Friendly*: No proprietary components, registries, membership fees or certifications.
- *Discoverability*: All interactions discoverable via protocol.
- *Interoperability*: Dynamic wiring without centralized message sets.
- *Failure Tolerance*: Eventual consistency even given errors.
- *Efficient*: Optimal use of network resources.
- *Minimal Effort & Setup*: Plug-and-play, minimal implementation for simple devices, linear scaling for complex ones.
- *Internet Compatibility*: Works over trusted and untrusted networks.

## Out-of-scope

SCAN does not replace radio-based protocols (e.g., Bluetooth, LoRaWAN). Such devices may connect via gateways but cannot participate directly because
they can not guarantee the appropriate level of security.

## Solution Overview

### The Protocol Summary

The SCAN protocol is divided into four layers:

- Internet Layer (Packet Communication and Announcement over IP)
- Logical Layer (Security, Multiplexing, Fragmenting, Logical Connections, Messaging)
- Data/Control Layer (Subscribing to Data and setting Controls)
- Application Layer (Required and common Data and Control definitions, including Wiring)

The Internet Layer is the actual transport infrastructure on top of IP that facilitates the transport of single
packets between devices and enables announcements. It supports different IP topologies, including
local networks, connections over gateways, etc.

The Logical Layer is responsible for providing a secure, flat, multiplexing and mixing capable layer
for communications between devices. Devices are identified, addressed based on static cryptographic keys instead
of hardware addresses and communicate point to point using a packet-based protocol that supports easy
multiplexing as well as unlimited length streaming messages.

The Data/Control Layer adds minimal quasi-request-response based interface that enables:

* Subscribing to *data* emitted by devices, which are a stream of changes in the state of the device.
* Setting *controls*, which may change the state of the device.

The application layer defines common data elements and controls that are either optional
or required for every device, such as setting up roles, resetting, unified software update control,
debugging and logging controls and data, and most notably wiring.

Wiring is an application layer tool that describes how devices interoperate. It describes which
data from which devices sets which controls at what other devices and it can also describe
how to transform data to be compatible with the required control.

Controls are a slightly different concept than *commands*. Even though they can be invoked in a
set-and-forget way on occasion like commands, the intended usage of controls through wiring is that they are *bound* to data, i.e. state,
coming from other devices. This means they will be eventually consistent with the data they are bound to.

### Operational Summary

SCAN is a peer-to-peer, distributed protocol, where all devices are potentially
data acquisition or control devices or both. Therefore two devices are already capable
of working together without any dedicated control device or server. Introducing
more components does not require any central component to exist either, although the
option is available if needed.

SCAN devices from the "factory" need to be provisioned first, which means that they need
to generate a new key for the administrator (the end-user) to use. After they are provisioned,
they can be *wired* to get data from, or set controls on another device or multiple other
devices.

Provisioning and wiring is done by the end user. Although most of this requires simple drag-and-drop
actions on an appropriate administrative interface, more advanced setups are possible using
transformations of data and actions and wiring up devices in non-conventional ways.

The SCAN protocol goes well beyond being just a transport protocol to deliver messages among
devices. It has semantic rules, which constrains what messages may mean, in order to give
stability guarantees, such as guaranteed recovery from error states, recovery from network
congestion, restarts, and other failure modes.

## Internet Layer

The internet layer supports basic network primitives based on IP-native means for the next layer.
These functionalities are:

* Open and receive a "physical" TCP/IP connection to/from a peer to send and receive data.
* Send and receive data to/from all connected devices.

This layer mimics IP closely. Meaning connections support streaming-based data exchange
with no packet demarcations, while communication with the whole network supports stateless
packet based communication.

Addressing uses native IP addresses.

There can be multiple ways of configuring a device. However
this configuration should be completely transparent for the layers above.

Devices must reuse TCP connections, therefore
at most one TCP connection must be present between two given peers at all times.

### Local Network Configuration

Every device must be capable of operating in a local network, where other devices are directly
addressable and all devices can be contacted by multicast packets. In this scenario:

* Connections are made / received using TCP/IP on port 11372.
* All devices are addressed over UDP, at the address 239.255.255.244:11372.

Note, that this "local network" does not necessarily need to be a "physical" local network,
it can be a virtual local network that connects multiple devices, possibly through VPNs or other means.

### Gateway-based Configuration

Devices may support connecting through "Gateways". A "Gateway" is a "Logical Layer" level software or hardware
device that does not necessarily have an "Application Layer" presence, i.e. it may be invisible
to the network, but can present all the devices that connect to it.

In this scenario the software stack on the device that connects through a Gateway maps  operations thusly:

* Connections are always made with the Gateway on port 11372 (the same port as above).
* All devices are addressed over TCP through the same connection as above.

In this case all communication is directed at the Gateway, which in turn reflects all information 
present on the "other side" of the Gateway.

### Network Configuration

Devices are expected to be available through a variety of network topologies and configurations,
including through static or non-static IP addresses, through WiFi, with or without DHCP, through VPN,
or through multiple network segments each with its own network zones or firewalls.

Devices therefore must support low level network configuration options to enable them to participate
in the SCAN network. These must at least include the following options:
* Direct connection to SCAN network. Discovery and address resolution through broadcast UDP.
* Connection through a gateway or gateways. Discovery and address resolution through gateway directly.

Gateways present a way to configure a static set of IP addresses to speak to, where the gateway is
essentially a stand-in for all devices that are behind it.
This may be necessary for devices that are not on any "local" network. Connected through
untrusted networks, such as cellular networks or other host networks.

Devices should do anything and everything that can be securely done to not have to configure
the network to use the device. This should include the following:
* Support WPS to join a WiFi network without configuration.
* Support, detect and use DHCP if available.
* Support local-link IP address auto-selection when DHCP not available, to support ad-hoc
wired networks.
* Potentially cycle through multiple strategies if one is not available.

Devices may support other methods to connect to a SCAN network, like VPN, Proxies, or other custom tunnelling
methods.

At the end of the network configuration devices must be able to send and receive frames to and from
the rest of the SCAN network.

Note that joining a network is not a security sensitive operation. The layers above are designed to handle
communication through unsecure networks just fine. The point of this layer is to make the device
available to talk to, in the most convenient way possible for the user.

## Logical Layer

The main purpose and design goals of this layer are the following:
* Provide **security** features, such as authentication, authorization and anti-tampering features.
* Logical **routing** capabilities, provide a virtual flat topology.
* Enable **multiplexing**, so that multiple logical connections can be established through one physical connection (if exists).
* Enable **message mixing**. Enable a device to interject messages even if another message is currently
  being sent or even streamed indefinitely.
* Enable **message fragmenting** so each fragment can be validated on its own and
  potentially partially processed, without assembling the whole message in memory.
* Enable the **detection of offline** status of devices.
* Add as **minimal overhead** as possible.

Devices get a peer-to-peer, secure, flat logical topology. That is,
each device is free to directly communicate with any number of other devices. There is no
"server", nor any central software or hardware components.

To support every possible physical topology, frames may contain additional logical routing information and are designed
to be able to be multiplexed, forwarded and proxied. 

The communication on this layer is packet based, with each packet limited in size to a maximum of 32767 bytes
excluding the frame header.

A logical connection is a connection between two devices identified by their public static keys. All
devices have a static key pair, the public part of which identifies the device uniquely and securely
on the network. There 
can be at most two logical connection between any two devices, because the ordered pair of public static keys uniquely identifies
a logical connection. Note however, that one physical connection can tunnel more than one logical connection.

If any parties to a communication encounter any errors in the protocol or interpretation of messages
they must immediately close the logical connection with a dedicated "close" message.

Devices may terminate the TCP connection without closing the logical connection. In this case the device is considered
"connected" but "offline". Devices may become offline in case of network or device errors temporarily, or may become offline
intentionally for energy saving purposes. Offline devices may reconnect and continue sending messages without additional handshakes.

The initiating party must not retry opening connections more often than 10 times / minute, but may implement any heuristics
to distribute those reconnects inside the minute.

### Frames

Frames are defined thusly:

```
Frame = {
   sourcePeer:      Option(PeerAddress)
   destinationPeer: Option(PeerAddress)
   content:         Union(Control, Payload, Advertisement)
}

PeerAddress = Array(32, Byte)
```

The source is the sending peer's public identity key. The destination is the public identity key of the target device. 

If both the source and destination are unique in a given physical connection, meaning that the physical
connection only carries this single logical connection, there is no need to
continuously send peer identifications. In this case both identifiers can be omitted.

This is also true for cases when either one of the identifications is superfluous. Devices need
to track logical connections in physical connections and know when this is the case. This information
is therefore essentially redundant, but may help some implementations.

The receiving device of a frame may ignore superfluous identifiers without further validation.

Since a single logical connection may traverse multiple physical connections, when routed through
proxies or gateways, the presence of peer identifications may be added or removed as needed
by intermediaries. These are explicitly not included in the end-to-end encryption scheme for this reason.

There is no explicit content delimiting. All peers, as well as intermediaries must be able to parse
all message types on this layer. If a message type is unknown (parsing fails), a device must close the connection, although
this shouldn't happen given the version number included in the handshake.

### Control Messages

```
Control = Union(InitiateHandshake, ContinueHandshake, CloseConnection)
```

#### Initiate Handshake

Sent first from the initiator of the connection to establish a logical connection.
If a physical connection does not exist yet, the initiator must try to open one first.
The frame transmits the first handshake message together with the
Noise Protocol Name and version of the logical layer.

```
InitiateHandshake = {
   noiseProtocolName: String
   protocolVersion:   ProtocolVersion
   handshake:         DynamicArray(Byte)
}

ProtocolVersion = Struct(
   major: Byte,  // 1 for this document, increased on incompatible changes
   minor: Byte   // 1 for this document, increased only on backwards compatible change
)
```

The Noise Protocol Name is the exact protocol used for the following handshake
and data exchange. If the recipient disagrees with the protocol it must close the
logical connection.

The protocol name, as well as the versions have to be included in the *prologue* of the Noise Handshake to
make sure it has not been tampered with.

If the responder disagrees with the version or the Noise protocol, it may respond with and Initial Handshake
of its own with the counter proposal. If the initiator does not agree, the handshake failed.

All devices must support the following protocols:

* **Noise_KKpsk1_25519_AESGCM_SHA256**: This is a bi-directional protocol. 
The '*KK*' variant comes from the fact, that the frame already contains the 
public static key of both the sender and responder. So both static keys are
already *K*nown.

The handshake makes sure that both parties actually possess the secret
private part of their static identity. In essence this makes sure that
both devices are who they pretend to be. This takes care of authentication.

Both devices must however also do *authorization*, that is, check
what the other device is allowed to do. Devices must implement the
"role"-based authorization method below. Additionally devices are free to implement
any allow-, or deny-listing based on the public static key, or implement
other restrictions based on either the public static key, time of day
or any other information gained during communications.

All handshakes contain a PSK (Private Shared Key).

A PSK works as a "role" during authorization. Since the responder may assign
privileges to certain PSKs, the PSK presented by the initiator categorizes
it to have those privileges. PSKs can be potentially published to multiple devices,
effectively creating a role or group of devices.

Security note: If PSKs are shared, those devices must be considered to be in the same security
domain, i.e. they all are only as strong as the weakest device that has that key. I.e. they all
"fall" together. 

Every device must come with a unique PSK already set up for its initial or following enrollments.
This permanent "factory PSK" must not be allowed to be used for anything else other than setting up a
new administrative (full access) PSK, when the device is being enrolled by the end-user.
This is a control each device must support (see relevant chapter). During this process however, the device
should reset all state to factory defaults and purge all information potentially
stored on the device. This way the protocol guarantees, that the device will always
generate a previously unknown PSK for usage, but still allow the user to recover / re-enroll
the device in case that gets lost, or the device itself changes hands, without potentially
leaking previous data.

Devices must implement some form of control to guarantee that factory resets can not occur randomly. Devices should
implement some measure to check whether the user doing the factory reset has physical access to the device. For example
a device may offer factory reset only for 1 minute after power-on, or require a button to be pushed, etc.

Note, that the handshake does not identify the PSK used explicitly. The responder
might therefore need to try multiple PSKs to know which one the initiator is using.
The protocol is designed so a single try takes a single hashing operation only. Still,
this mechanism is designed with a limited set of possible PSKs in mind.

Devices must implement some throttling mechanism for authenticating connecting devices, to
prevent brute-forcing PSKs. Introducing delays when an unsuccessful connection was attempted,
or use a temporary ban list, etc. Devices are not required to permanently store any of this information.

Both the sender and destination identifier must be present in this frame.

#### Continue Handshake

Sent potentially by both parties. It continues the handshake after it has been initiated.
The first continue handshake must come from the responder, then from the initiator
and continue in turn until the connection is established based on the initially selected
protocol variant.

```
ContinueHandshake = {
   handshake: DynamicArray(Byte)
}
```

#### Close Connection

Both parties may send this message to terminate the logical connection. After this message
all keys and state information about the connection can be discarded.

```
CloseConnection = Struct(
   protocolVersion: ProtocolVersion,
   reason: String
)
```

The close message contains the protocol version to detect a version mismatch, as well as
a diagnostic, human readable reason for closing the connection.

### Payload Messages

```
Payload = Union(IntermediatePayloadChunk, LastPayloadChunk, SingleChunkPayload)

// Used later
EncryptedPayload = Struct(
   payload:   DynamicArray(Byte),
   mac:       Array(16, Byte)
)
```

#### Intermediate Payload Chunk

A part of an application message, including the initial chunk, but not the last chunk. This frame indicates
that the message is not complete, additional chunks will follow for this message.

The actual payload of the application layer is described in the next chapters. This message
may be sent by both the initiator and responder.

Structure:

```
IntermediatePayloadChunk = Struct(
   messageId:          VariableLengthInteger(8),
   encryptedPayload:   EncryptedPayload
```

If any decryption errors occur, meaning that for some reason the sender and receiver becomes
out of sync, messages were omitted or repeated, or parsing failed, the connection must be closed.

Chunks are a mechanism to split messages that are too large to fit into one frame into multiple
chunks. Each chunk of the same message must have the same message Id.
A sender may also choose to chunk messages for other reasons, for example
to get video frames that are already available quicker to the receiver to reduce lag.

The Message Id identifies this message and all chunks it consists of. Message Ids should
be re-used to be able to keep the Id low and in one byte. All values for which
a last chunk has been sent must be considered re-usable.

#### Last Payload Chunk

The last chunk of an application message. This chunk may also be potentially the first and only
chunk the message has, although in this case the below frame should be preferred.
It indicates that the application message identified by Message Id is complete with this payload.

Payload structure:
```
LastPayloadChunk = Struct(
   messageId:          VariableLengthInteger(8),
   encryptedPayload:   EncryptedPayload
```

Encryption and key management is the same as for intermediate frames.

The Message Id used in this frame must be considered reusable after this frame is sent.

#### Single Chunk Payload

An application message that fits a single chunk.

```
SingleChunkPayload = EncryptedPayload
```

Encryption and key management is the same as for intermediate frames.

### Advertisement

Announces the identity or identities represented by a device. Every device must send
identity announcements approximately once per second.

```
Advertisement = DynamicArray(PeerAddress, max=16)
```

This message announces to all peers that these static keys are reachable at
the address this frame is from. A device, such as a gateway,
may represent multiple devices on the local network, that is why
multiple static keys may reside at the same IP address.

The packet may contain up to 16 static keys. If a device represents more logical identities than that,
it may send multiple packets of this frame.

Devices must announce themselves when they become available, unless
some restrictions (like low energy device) would make it impractical, or they are
not online for more than a second.

The identity announcement also doubles as keep-alive messages in addition to tracking the mapping
between IP address and static public address of logical devices. If a device misses 3 identity announcements
it must be considered *offline* from the network. Devices should not attempt to send anything to devices
considered *offline*. Such a device may re-establish the internet connection at a later point in time 
without initiating a new logical handshake. It may just continue to send messages normally, as if nothing had happened.

Note however, devices are not required to be able to persist connection information, and may even handle
offline devices with closing the connection and forgetting the keys altogether.

If, after a device has been *offline*, cryptographic keys become out of sync, or those keys simply no longer exist, the connection must be closed,
forcing the initiator to establish a new logical connection with new keys. This also means that a receiver must send a close
connection frame on unsolicited application message frames.

When sending to a gateway, this packet may be sent over TCP/IP directly to the gateway. The gateway
must announce itself to a connected device as all logical devices that are behind it.

### Message Choreography

There can be only at most two logical connections
between any two parties. If a logical connection already exists, that must be used.
If not, a new logical connection needs to be established. If there is already a physical
connection between the source and the target, that physical connection must be used. If not, a new physical
connection must be established first.

The *initiator* of the connection is the party that opens the logical connection.
The *responder* is the one that accepts the connection.

Note, there is an asymmetry between the initiator and the responder, because only the initiator
"sets" the PSK. This means the responder *authorizes* the initiator for the communication, but the
reverse is not true.

#### Initiator establishes new connection

A handshake is started by the initiator.

1. If there is no TCP connection between the two peers, initiator opens one.
2. Initiator sends "Initiate Handshake" message.
3. If handshake not concluded, Responder sends "Continue Handshake".
4. If handshake not concluded, Initiator also sends "Continue Handshake" and process continues at step 3 again.
5. If no errors happened, logical connection established.

After the handshake is finished both parties are now allowed to send any number of application messages in any order, provided
the chosen Noise protocol allows it.

Any party may close the connection at any time for any reason. The initiator is
free to re-open the connection at any time. The Responder may close the connection
with the Renegotiate frame.

#### Initiator re-establishes a connection

1. Initiator sends Application Messages with previously established keys.
2. If Responder does not remember previous keys, or is unable to decrypt, it sends a Renegotiate frame.
3. If Responder sent Renegotiate, Initiator removes it keys and closes the connection.
4. Otherwise, logical connection established.

### Address Resolution

Each device must monitor identity announcements for two reasons:
* To maintain a mapping of IP address to public static address key
* To maintain "offline" status of devices

A device is not required to cache the monitored announcements, in which case it may need to wait a couple of seconds
to detect the identity announcement it is interested in. Maintaining a cache of announcements speeds
up this discovery of course.

Monitoring announcements is required to maintain offline status of at least the connected devices.
Devices must not send messages to offline devices to maintain key synchronization.

If an IP address can not be found for a given identity key, the connection can not be established.
Devices may choose to display this to the user if capable, or may send specific error events through
other logical connections.

## Data/Control Layer

The Initiator of the logical connection is called a Controller Device on this layer, while the Responder
is called the Controlled Device. A single logical connection only allows for one side to be the Controller.

Note that in any setup the Controller and Controlled roles may be defined independently of the Devices
themselves. For example in a single Light and a single Button setup, we may traditionally think,
that the Button "controls" the Light, but in fact the Light can be set up to "control" the Button.
That is, to initiate a connection to the Button and based on Data the Button generates control its own
operation. In this case the Light will obviously not "control" the Button in the traditional meaning,
but will nonetheless assume the role of the "Controller" for the purposes of this specification.

This layer enables the Controller to perform following actions on the Controlled device:
* Subscribe to data
* Set controls

Subscribing to data means to get a stream of values conforming to a given Data Type, while settings a
control means to submit a value of a certain Data Type as an argument. In both cases
getting a single data element or executing a single control call will involve a single, albeit possibly complex, value.

Immediately after establishing the lower layer connection, the Controlled must send its capabilities to the Controller.
These capabilities must not change during the whole life of the connection.
If the capabilities of the Controlled device do change, it must terminate the connection and let the
Controller re-establish a connection where the new capabilities can be submitted.

The Controller may not send any messages before the Capabilities is received.

### Controller Device Messages

```
ControllerMessage = Union(StreamData, SetControl)
```

#### Stream Data

Request the Controlled to send values for the specified Data Packet indefinitely. Action will have any number of responses.

```
StreamData = Struct(
   dataIndex:       VariableLengthInteger(8)
   minimumSendWait: Duration
)
```

The Data Index specifies the data by its index (0 based) in the Controlled device's capabilities list.

Minimum send wait specifies how much time the Controlled device should wait between sending data. Zero
means as fast as possible, without any waiting. The Controlled Device must honor this wait
in every case, even if the data is generated by manual input. It must never send data more
frequently than specified. It may however send data less frequently. If the data is produced more
often than specified proactively, the Controlled must make sure the most current data is submitted
when the next communication window arrives, even by potentially dropping or overwriting earlier data points.

The Controller may repeat this message if the waiting period changes for some reason.

The Controlled Device should send the most current data as soon as possible when receiving this request,
and re-calculate the time for the next message. Sending this request with an "infinite" wait can therefore
be used to request one data message, essentially imitate a pull-based approach.

The Controlled Device must not send messages for the same data packet in parallel. It must always send messages
for the same data sequentially.

### Set Control

Request to set a control on the Controlled device.

```
SetControl = Struct(
   controlIndex: VariableLengthInteger(8),
   value:        DynamicValue
)
```

The value type is dynamically set at the time of sending, since it depends on the control being sent.

### Controlled Device Messages

```
ControlledMessages = Union(Capabilities, Data, ControlResponse)
```

#### Capablities

The Controlled devices send this message as soon as a connection is established unsolicited.

```
Capabilities = {
   protocolVersion:    ProtocolVersion,
   deviceDefinition:   DeviceDefinition
   typesDefinitions:   DynamicArray(Byte)  // Compiled type definitions
   dataDefinitions:    DynamicArray(InterfaceDefinition)
   controlDefinitions: DynamicArray(InterfaceDefinition)
}

// Describes device specific information to identify the device
DeviceDefinition = {
   deviceName: String                 // Display name of device
   deviceDescription: String          // Description for the user
   deviceId: String                   // Vendor specific/internal id
   deviceURI: Optional(String)        // A website describing the device
   firmwareVersion: Long              // Monotonically increasing firmware version number
   vendorName: String                 // Vendor's name
   vendorId: String                   // Vendor's id
   vendorURI: Optional(String)        // Vendor's website
}

InterfaceDefinition = Struct(
   name:        String,
   descirption: Markdown,
   typeName:    String     // Name of the type
)
```

#### Data

Send data values as requested by a subscription from the Controller.

```
Data = Struct(
   dataIndex: VariableLengthInteger(8),
   value:     DynamicValue
)
```

The Data Index identifies which request this data value belongs to.

Note, that because of the Resolution Principle the Device must immediately
send the newest data value upon receiving a Data Request. This may involve taking
an immediate measurement, or may involve sending a cached value from memory, but
the value must always be the most current one in the given semantics.

Note also, that this semantic may include a month-end meter value for example. "Historical"
values are allowed, as long as this does not mix with "current" values. I.e. it has to have its own
data definition, as it has its own modality.

#### Control Response

Used by the Controlled device to optionally indicate the rate the given control can be set.

```
ControlResponse = Struct(
   controlIndex:    VariableLengthInteger(8),
   minimumSendWait: Duration
)
```

Same as the similarly named attribute in the data streaming request, it controls the minimum waiting time the control can be set.
The Controller must honor this rate and never set the control more frequently than given.

Controlled devices are not required to send this response, or they may send it multiple
times. The Controller must honor the latest received maximum rate at all times.

The Controller may proceed to set the control at any rate until the Controlled
explicitly answers with a given rate. The Controlled is also free to skip certain
control settings, if it can be immediately replaced with a newer one.

## Technical Discussions

### The Resolution Principle

In SCAN any piece of data or control must be replaceable by newer versions
of the same data or control setting.

This means that any stream of data or control *for the same thing*
may be simply substituted by the last (newest) element. In other words, losing messages
will only decrease the *resolution*, not change the *meaning*.

"The same thing" is called a *modality* for the purposes of this specification. A *modality*
is a single semantic entity on the device. A single physical sensor or a single logical
entity that emits data or control. For a modality any emitted data makes any old data
from the same modality obsolete for the purposes of determining the state of the modality.

An example would be submitting the current value of a thermistor, the current
measured temperature. Any stream of this data may be replaced by the last measured
value and it would only lose some temporal resolution, but the overall meaning
would not be lost.

A counter example would be a throttle control that submits changes (deltas, not absolute values) to its position.
If any stream of such data would lose even one piece, it would change the meaning
of the stream of data. It would result in the wrong setting on the receiver side.

This principle is the backbone of handling backpressure and other connection problems
and must be adhered to at all times.

For the above to work the device must also make sure that the newest message does
actually get delivered. So in the case of connection loss all devices must
send all relevant newest data and/or control values immediately upon the connection is established
and the data is requested again.

This applies also to the case if the device itself crashes and gets restarted. The device
must send the newest messages for all modalities, or measure/acquire it explicitly
again if those messages are no longer available.

If a modality can not be directly measured, such as an alert that was generated
which no longer applies, devices must make sure such data is persisted and available
in case of a crash or restart.

As a side-effect messages are also repeatable. Since a stream of two messages with
the same content would also mean the same thing as one of those messages.

### Network Backpressure

Network Backpressure is the mechanism by which consumers of messages can tell producers to slow
down producing messages in the event that they can't consume them fast enough, or if
the network is saturated and can't handle more traffic.

If a consumer
is not ready to process another message it will not empty the TCP receive buffer,
therefore eventually the buffer runs full, which will result in not acknowledging
packets. This will eventually result in the send buffer of the producer to fill up as well.

Devices must react to this in one of the following ways:
* Let the backpressure propagate upstream. That is, block the next sensor reading for example until
the send buffer clears up. This may be implemented as easily as reading a sensor and
sending data on the same thread. This will result in losing *resolution*, but not meaning, conforming
to the resolution principle.
* If the backpressure does not propagate, *drop* obsolete messages instead of queueing them. Replace
a non-sent message from the same modality with a new one. Queued obsolete messages have no value
to a controller and would likely only contribute to the problems causing the backpressure in
the first place.
* In case of streaming messages implement custom *drop* policy based on the data. For example
in video streams drop obsolete frames, or in case of progressive video drop literal resolution
until the backpressure is eased.

### Quality of Service

The Quality of Service of SCAN is not as clear-cut as "At most once", "At least once"
or "Exactly Once". That is because the purpose of the network is not just delivering data for its own sake,
but *controlling* devices based on the most current data available.

SCAN guarantees that *the most current data* is delivered *as fast as possible* at all times, i.e.
that the correct controls will be *eventually applied* in the face of temporary errors.

The first part of that guarantee is that at least the most current data for each modality *will*
be delivered eventually. That means if there is a new piece of data or there is a new control value
it will not get lost, only possibly replaced by an even newer piece of data or control for that same modality.
This holds under all circumstances, even in the face of network errors and intermediaries having random errors.

This is easy to prove using the following observations:
* After a message is created it either gets delivered, dropped by the device intentionally, or results in an
error which eventually closes the connection. There can not be any other outcomes.
* If the message is dropped intentionally, that can only happen if a newer message for that modality
exist, so the guarantee stands.
* If the message is lost on the network the TCP connection will eventually time out. If it is
somehow silently dropped, the next message will result in a wrong decryption because of the rotating keys.
Either way the connection will close.
* If the source or target device crashes during some phase of the communication the connection will be lost too.
* If any connection is re-established all of the current state is retransmitted. Hence a lost connection
results in a sort-of "save-point" to be re-established.

The second part of the guarantee is that the newest data will be delivered as fast as possible. As fast
as the network and the receiver allows, because if any of those is slow the device will drop obsolete messages
in favor of new ones, which will both help solve the problem and reduce the time the most current data gets delivered to
a minimum.

### Rate Limiting

The goal of Rate Limiting is twofold. It is to prevent wasting network resources,
but perhaps more importantly it is to provide a way to
synchronize the producers with the consumers to guarantee the most recent measurements
are available at the right time, with almost no communication overhead.

SCAN networks can form a complex graph of devices communicating, issuing data and setting controls on each other.
Rate Limiting is the way SCAN ensures that throughout all processing chains data and controls
are generated at _exactly_ the same rate as they are consumed throughout the whole chain, _and_ at the
exact right time.

For example, if one device specifices that a control only need to be set once per second, it's
controller is notified of this fact (see message descriptions). From this point, the controller
is responsible for the timing of the controlled device. If that controller relies on another device
for some events, it pushes the rate limiting further upstream, making the first device in the chain
responsible for the timing of all downstream devices. Devices can also use "pull"-based event
reception for more complex dependency trees or graphs.

Note that network backpressure alone does not lead to an ideal producer-consumer synchronization. 
Network backpressure is not specific to a single control nor data, but may influence all the
communication on the network. It would be difficult to isolate which communication channel, if any,
caused network congestion. This uncertainty and imprecision may cause various communication artifacts,
including network over-use, messages coming in batches or waves, or even not finding a steady state.

Rate Limiting is an explicit measure based on application-level feedback to senders from receivers. All
receivers must define the maximum rate at which they can consume messages, or more accurately, they
should define the minimum practicable rate for the given use-case. This is for both data and
control settings. This measure is trivially independent of network problems, therefore can work
reliably even in the face of network backpressure events.

A steady state is easily reached as producers will automatically approximate the consumer's rate at all times
and not fluctuate trying to dynamically match the consumer with some algorithm.

It also does not require constant adjustment, therefore does not require regular feedback messages.
The consumer will explicitly communicate what the best-case steady state looks like once. This
maximum rate is independent of the network and transient congestion events, therefore does not need
to be adjusted.

Note, that while the rate does not require constant adjustment, devices may decide to alter rate
for use-case specific reasons. For example the user navigates away from a screen, then the device presumably 
no longer needs the data presented on that screen. The device should set the rate for data it doesn't need
to 0, and then back again when it actually needs it.

This also means that device vendors and implementers do not need to guess proper timing and message
rates for downstream devices.
Devices need only define *how* and *what* to measure and then let the SCAN network take care of proper timing and intervals adaptively.

## Appendix A: Terminology

* **Initiator**: The party who establishes a network layer connection to another party.
* **Responder**: The party who receives a network layer connection from another party.
* **Device**: A party in a SCAN network. Although the term "Device" implies a physical
machine, a Device may be fully software implemented, or one physical device may even represent
multiple logical Devices.
* **Controller**: The *Initiator* on the application layer. The party who setgs controls
on the other party.
* **Controlled**: The *Responder* on the application layer. The part who responds to controls
from the other party.
* **Modality**: A single physical or logical entity in a device, which may produce stream of data or may
be set as control, or both.
* **Operator**: A human user configuring the network and/or devices.

