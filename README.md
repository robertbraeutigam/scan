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
- Modalities Layer (Defining the actual interface of the Device)
- Application Layer (Required and common modality definitions, including Wiring)

The Internet Layer is the actual transport infrastructure on top of IP that facilitates the transport of single
packets between devices and enables announcements. It supports different IP topologies, including
local networks, connections over gateways, etc.

The Logical Layer is responsible for providing a secure, flat, multiplexing and mixing capable layer
for communications between devices. Devices are identified, addressed based on static cryptographic keys instead
of hardware addresses and communicate point to point using a packet-based protocol that supports easy
multiplexing as well as unlimited length streaming messages.

The Modalities Layer adds minimal quasi-request-response based interface that enables connecting modalities to act as one consistent unit,
enabling an error-resistant, eventually consistent network of devices.

The application layer defines common modalities that are either optional
or required for every device, such as setting up roles, resetting, unified software update,
debugging and logging, and most notably wiring.

Wiring is an application layer tool that describes how devices interoperate. It describes which
data from which devices sets which controls at what other devices and it can also describe
how to transform data to be compatible with the required control.

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

The Internet Layer supports basic network primitives based on IP-native means for the next layer.
These functionalities are:

* Open and receive a "physical" TCP/IP connection to/from a peer to send and receive data.
* Send and receive data to/from all connected devices.

This layer mimics IP closely. Connections support streaming-based data exchange with no
packet demarcations (frame boundaries are defined in the Logical Layer), while communication
with the whole network supports stateless packet based communication.

### Terminology

Two notions of "peer" appear in this specification. At the Internet Layer a *physical peer*
is an IP address at which a SCAN stack is reachable on port 11372. At the Logical Layer and
above a *logical peer* is a 32-byte static public key (`PeerAddress`). A single physical peer
may represent multiple logical peers (the gateway case), and a single logical peer may be
reachable through multiple physical peers (a multi-homed device). Unless otherwise qualified,
"peer" in this section means physical peer.

### Addressing

Addressing uses native IP addresses. Both IPv4 and IPv6 are supported; implementations must
support at least one family and should support both where the environment permits.

There can be multiple ways of configuring a device. However, this configuration should be
completely transparent for the layers above.

Devices must reuse TCP connections. At most one TCP connection must be present between any
two physical peers at any time; multiple logical connections multiplex over that single TCP
(see Logical Layer). When both sides dial each other and two TCP connections briefly exist
between the same pair of physical peers (simultaneous open), both sides must close the
connection whose initiator's 32-byte logical peer address, compared as a big-endian
unsigned integer, is numerically larger. Both sides observe the initiator identity in the
`Initiate Handshake` frame and therefore converge on the same decision without coordination.

### Local Network Configuration

Every device must be capable of operating in a local network, where other devices are directly
addressable and all devices can be contacted by multicast packets. In this scenario:

* Connections are made to, and received on, TCP port 11372. Source ports for outgoing
  connections are ephemeral.
* All devices are addressed over UDP on port 11372, at multicast group:
  * `239.255.255.244` for IPv4 (RFC 2365 "IPv4 Local Scope").
  * `ff05::2c6c` for IPv6 (site-local scope; `0x2c6c` = 11372 decimal).
* Multicast datagrams must be sent with IPv4 TTL 32 or IPv6 hop-limit 32.
* Devices must join the relevant multicast group via IGMPv2+ on IPv4 and MLDv2 on IPv6
  on every interface over which they participate. Switches should enable IGMP/MLD snooping
  to prevent multicast flooding, but the protocol does not require it.

The `Advertisement` frame (see Logical Layer) is capped at 16 `PeerAddress` entries per
frame. With frame overhead this fits comfortably within 1280 bytes (the IPv6 minimum MTU),
so implementations must not rely on IP fragmentation for advertisements. Devices representing
more than 16 logical identities send multiple frames.

Note that this "local network" does not necessarily need to be a "physical" local network;
it can be a virtual local network that connects multiple devices, possibly through VPNs or
other means.

**Privacy note.** Multicast advertisements carry 32-byte static public keys in the clear.
Any device on the same broadcast segment can enumerate SCAN identities and correlate them
across time. SCAN assumes the local segment is semi-trusted; for hostile networks use a
gateway reachable over a trusted tunnel.

Port 11372 is not currently registered with IANA; registration is intended.

### Gateway-based Configuration

Devices may support connecting through "Gateways". A Gateway is a Logical Layer-level
software or hardware device that does not necessarily have an Application Layer presence —
it may be invisible to the network, but presents all the devices that connect to it.

A device may be configured with any number of gateways concurrently. Each gateway represents
a distinct set of logical peers reachable through it, typically on a different network
segment. There is no ordering, failover, or primary/secondary relationship among gateways:
the device maintains an independent TCP connection to each, and treats the union of their
advertised identities, plus any locally multicast identities, as its view of the network.

Operations through a gateway map thusly:

* The device opens a TCP connection to each configured gateway at the gateway's IP on
  port 11372.
* All traffic to logical peers reachable through a gateway flows over that gateway's
  TCP connection.
* The gateway emits standard `Advertisement` frames over that same TCP connection at
  approximately the same ~1 Hz cadence as local multicast, each carrying up to 16
  `PeerAddress` entries. Devices process advertisements received over TCP identically
  to those received over UDP multicast. Gateways with more than 16 identities behind
  them send multiple frames; the identity set is eventually consistent.

Gateways have no cryptographic access to payloads — end-to-end encryption is preserved at
the Logical Layer. A gateway can, however, observe metadata (identity keys, packet timing,
traffic volumes) and can drop traffic. Users should treat a gateway as untrusted transit,
equivalent to any other network intermediary.

### Address Change Handling

The mapping from logical peer to physical peer is maintained by processing `Advertisement`
frames. When a new advertisement reports an IP address for a currently-connected logical
peer that differs from the one the existing TCP connection is bound to, the device must
close the existing TCP connection and establish a new one to the newly advertised address.
The logical connection (Noise keys, subscription state) is not affected and resumes per the
reconnect rules in the Logical Layer. This handles DHCP renewals, WiFi roaming between
access points, and interface changes on multi-homed peers.

### Network Configuration

Devices are expected to be available through a variety of network topologies and
configurations, including through static or non-static IP addresses, through WiFi, with or
without DHCP, through VPN, or through multiple network segments each with its own network
zones or firewalls.

Devices therefore must support low-level network configuration options to enable them to
participate in the SCAN network. These must at least include the following options:

* Direct connection to SCAN network. Discovery and address resolution through multicast UDP.
* Connection through one or more gateways. Discovery and address resolution through each
  gateway directly.

Gateways present a way to configure a static set of IP addresses to speak to, where each
gateway is essentially a stand-in for all devices that are behind it. This may be necessary
for devices that are not on any local network, connected through untrusted networks such
as cellular networks or other host networks.

Devices should do anything and everything that can be reasonably done to not have to
configure the network to use the device. This should include the following:

* Support Wi-Fi Easy Connect (Wi-Fi Alliance DPP) for WiFi onboarding where available.
  Devices without DPP support should offer BLE-based provisioning or a temporary captive
  portal. WPS is discouraged and should not be relied on, as WPS PIN is considered
  insecure and has been deprecated by the Wi-Fi Alliance.
* Support, detect and use DHCP (IPv4) and SLAAC or DHCPv6 (IPv6) where available.
* Support link-local IP address auto-selection (RFC 3927 for IPv4, RFC 4862 for IPv6)
  when DHCP is not available, to support ad-hoc wired networks.
* Potentially cycle through multiple strategies if one is not available.

Devices with multiple network interfaces treat each interface independently: they listen
for TCP on 11372 on each interface, join the multicast group on each interface, and track
the (interface, source IP) tuple of each received advertisement. A logical peer reachable
via several physical peers (e.g. wired + WiFi) will have multiple IP-to-key mappings; the
device may use any of them to establish a TCP connection, subject to the one-TCP-per-
physical-peer-pair rule above.

Devices may support other methods to connect to a SCAN network, like VPN, proxies, or other
custom tunnelling methods.

At the end of network configuration, devices must be able to send and receive frames to
and from the rest of the network or parts thereof, so that the user can connect to it
with an administrative device.

Note that joining a network is not a security-sensitive operation. The layers above are
designed to handle communication through insecure networks just fine. The point of this
layer is to make the device available to talk to, in the most convenient way possible for
the user.

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
can be at most two logical connections between any two devices, because the ordered pair of public static keys uniquely identifies
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
   sourcePeer:      Optional(PeerAddress)
   destinationPeer: Optional(PeerAddress)
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
   protocolVersion:   Version             // 1.0 for this specification
   handshake:         DynamicArray(Byte)
}
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

All handshakes contain a PSK (Pre-Shared Key).

A PSK works as a "role" during authorization. Since the responder may assign
privileges to certain PSKs, the PSK presented by the initiator categorizes
it to have those privileges. PSKs can be potentially published to multiple devices,
effectively creating a role or group of devices.

Security note: If PSKs are shared among more than two devices, those devices must be considered to be in the same security
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

Sent potentially by both devices. It continues the handshake after it has been initiated.
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
   reason: String
)
```

It contains a diagnostic message, a human readable reason for closing the connection.

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
between any two devices. If a logical connection already exists, that must be used.
If not, a new logical connection needs to be established. If there is already a physical
connection between the source and the target, that physical connection must be used. If not, a new physical
connection must be established first.

The *initiator* of the connection is the party that opens the logical connection.
The *responder* is the one that accepts the connection.

Note, there is an asymmetry between the initiator and the responder, because only the initiator
"sets" the PSK. This means the responder *authorizes* the initiator for the communication, but the
reverse is not true. It is assumed that layers above will also be asymmetric, in that the initiator will
use services of the responder, but not the other way around.

#### Initiator establishes new connection

A handshake is started by the initiator.

1. If there is no TCP connection between the two peers, initiator opens one.
2. Initiator sends "Initiate Handshake" message.
3. If Responder does not accept version or protocol, it also send an "Initiate Handshake" message back
with the proposed parameters.
4. If handshake not concluded, both devices continue to send "Continue Handshake" messages.
5. If any errors happened, Close is sent and the physical connection is possibly closed if not used.
6. If no errors happened, connection is established.

After the handshake is finished both parties are now allowed to send any number of application messages in any order, provided
the chosen Noise protocol allows it.

Any party may close the logical connection at any time for any reason. The initiator is
free to re-open the connection at any time. 

Any party is also free to close the physical connection at any time to mark itself "offline" but still
"connected".

#### Initiator re-establishes a physical connection

1. Initiator sends Application Messages with previously established keys.
2. If Responder does not remember previous keys, or is unable to decrypt, it sends a Close frame.
3. Otherwise, logical connection is still established.

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

## Modalities Layer

All devices expose one or more *modalities*. Modalities represent isolated aspects of the devices' state or behavior and
are the only way to interact with them.

Examples include:
* Light on/off
* Switch on/off (up/down)
* Volume knob position (0-100)
* Firmware update
* Reboot

Modalities may have an input and an output, where both could be any types from simple scalars to complex values. The input and output types
do not have to be the same, the modality may have more or even less state to report than can be set externally.
The input changes the state represented by the modality, while the output streams the visible parts of the states as they change. The modality
may have hidden state that is not visible nor changable.

A network of devices is created by "connecting" modalities together. A connection involves directing the output of one modality to the
input of the other and directing the output of the other back to the input of the first one, assuming they have matching types.
This means devices don't really "control" each other, as much as report their own state to them continuously.

This concept differs from the traditional "data and commands" or "read/write attributes" concept in several ways. Modalities don't
"control" each other and don't even "set" the state of each other directly. Since there are no "commands", there's also no applicable concept
of "acknowledgements" and no reason to "reject" states the other device reports.
Note however, that devices may implement use-case specific acknowledgement strategies when this is required for
their correct operations. For example waiting for the state to be reflected by the remote device, or the device to indicate in some use-case
specific way that the operation is in progress / completed / failed.

Modalities are designed to resist temporary errors and mirror all relevant remote states reliably to a device, based on which the device
makes decisions to set its own state.

The "Resolution Principle" applies to both directions of data flow. Data is always replaceable by newer versions of the same.
Devices can not expect to receive _all_ state transitions from a remote device. The only guarantee is, that they will receive the newest
one at the earliest time possible. Devices therefore don't need a sending queue for modalities, they only need to keep the currently
transmitting and the newest state for each modality in memory at most, if needed.

While connecting modalities is semantically symmetric, as data is moving back and forth the same way between devices, with exactly same rules, the
connection itself is not symmetric. One device, the *Initiator*, connects to the other device, the *Responder*. The Initiator will make requests
to the Responder, which will reply. Note also, that the Initiator will present the PSK, therefore the Responder will authorize the Initiator to
make requests, not the other way around.

### Initiator Messages

```
InitiatorMessage = Union(Subscribe, Unsubscribe, State)
```

#### Subscribe

Request the Responder to send state values indefinitely for the specified modality.

```
Subscribe = Struct(
   modality:            IndexedModalityReference,
   minimumSendWait:     Duration,
   priority:            Optional(Priority)
)
```

Minimum send wait specifies how much time the Responder should wait between sending data. Zero
means as fast as possible, without any waiting. The Responder must honor this wait
in every case, even if the data is generated by manual input. It must never send data more
frequently than specified. It may however send data less frequently. If the data is produced more
often than specified proactively, the Responder must make sure the most current data is submitted
when the next communication window arrives, even by potentially dropping or overwriting earlier data points.

The `priority` field, if present, overrides the modality's default priority for this subscription.
This allows the Initiator to request a priority appropriate to its own use of the data. For example,
a rudder angle sensor may declare `Normal` priority by default, but an autopilot subscribing to it
should set `Critical`, while a display showing the same data on a dashboard would leave it at the default.
The Responder must use the effective priority (override if present, otherwise the modality default)
when marking outgoing packets for this subscription.

For State messages sent by the Initiator to the Responder, the Initiator applies the effective
priority itself.

The Initiator may repeat this message if the waiting period or priority changes for some reason.

The Responder must not send messages for the same data packet in parallel. It must always send messages
for the same data sequentially.

#### Unsubscribe

Request the Responder to stop sending state values.

```
Unsubscribe = Struct(
   modality: IndexedModalityReference
)
```

The Responder must immediately stop sending state updates, and interrupt any outstanding streams.

The Initiator may send this message, if it does not use the state updates anymore (for example the data is not on screen).

#### State 

Signal a change of the visible state of a modality.

```
State = Struct(
   modality:      IndexedModalityReference,
   value:         DynamicValue,
)
```

The `modality` must reference the target modality on the Responder device.

The exact type of the `value` should be the `inputType` of the target modality.

### Responder Messages

```
ResponderMessage = Union(Modalities, State)
```

#### Modalities

Responders send this message as soon as a connection is established, unsolicited.

```
Modalities = DynamicArray(Modality)

Modality = Struct(
   id:                 String,                // Identifier of this modality on this device
   name:               Text,
   description:        MarkdownText,
   localTypes:         DynamicArray(Byte),    // Types for this modality
   minimumIntentWait:  Duration,              // Minimum time to wait between inputs
   priority:           Priority,              // Default traffic priority for this modality
   keyType:            String,                // The type identifying modality instances
   outputType:         String,                // The type of the visible state of this modality
   inputType:          String,                // The type of the changeable part of the state
)
```

The `priority` field declares the default traffic priority for this modality. It indicates the importance
of the data for network-level quality of service. See the Priority type definition and the Deterministic
Delivery discussion for details. Initiators may override this priority when subscribing.

The input and output types may be `Nothing` to indicate that there is no input or output respectively.
There are modalities which may be read-only by nature, such as a time source, gps position, or a toggle switch which
needs to be physically toggled to change state.
There can also be modalities which are write-only, such as a firmware update where the firmware needs to be submitted, but there
may be no corresponding reading of the firmware image.

Modalities may have multiple instances, described by the values allowed by the `keyType`. For example a security panel,
capable of displaying any number of video inputs may define a video modality keyed by a simple String identifying the video
stream, while a simple switch may set this type to `Unit`, to indicate that there is only one instance of this modality
available.

The returned structure must take the caller's rights into account. It must only list modalities to which the caller
has at least some rights, but then it must list the modality in its entirety.

#### State

Send state values to a subscribed Initiator. This is the same `State` type the Initiator uses, but references the local
modality not the target modality.

```
State = Struct(
   modality:      IndexedModalityReference,
   value:         DynamicValue
)
```

Note, that because of the Resolution Principle the Device must immediately
send the newest data value upon receiving a Subscribe. This may involve taking
an immediate measurement, or may involve sending a cached value from memory, but
the value must always be the most current one in the given semantics.

Note also, that this semantic may include a month-end meter value for example. "Historical"
values are allowed as long as its defined that way.

The value must be of type `outputType` defined in the modality.

## Application Layer

This layer defines what modalities devices must implement. It also defines some optional modalities
and some common data types that devices should use whenever appropriate.

### Administrative Modalities

Modalities to administer the device. All of these must be implemented by all devices.

#### Enroll

Enroll a device to an administrative application. This function must only be possible with the initial enrollment
key and no other keys. Even with this key, this enrollment must be protected by some other means as well to disallow
completely remote enrollment. Ideally enrollment should only be possible with physical access to the device. To make sure this
is a case, a device may use buttons, or a simple timer to disallow using this function after a given amount of time after
power-on.

As part of the enrollment the device must delete all settings and all information potentially stored on the device. The device
must be set back to factory state. This guarantees that in the case of re-sale or changing owners, the device will not leak
any confidential data.

```
Modality(
   id:                 "scan.enroll",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "Nothing",
   inputType:          "PSK"
)
```

The modality is "called" with the proposed administrative ("root") PSK. There is no output state, but if the call succeeded, 
the connection will be terminated, and the PSK can be used to connect to the device and receive all modalities.

The device must disconnect all connections as part of the enrollment and must not allow new connections until the new PSK is registered. The device may reboot as part of the enrollment.

#### Reset

Reset the device to factory settings, including removing any and all user settings and data.

```
Modality(
   id:                 "scan.reset",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "Nothing",
   inputType:          "Boolean"
)
```

Input is whether the device should reset. There is no output, it is expected the input will be kept `true` until the device resets, i.e.
the connection is lost.

Usable only with the administrative PSK. Note: After the call that administrative PSK is not valid anymore. Device must terminate
all connections.

#### Rights

All the PSKs and associated rights on this device, expect the master adminitration key used for enrolling.

```
Modality(
   id:                 "scan.grant",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "Rights",
   inputType:          "Rights"
)

Rights = DynamicArray(PskRight)

PskRight = Struct(
   psk:           PSK,
   rights:        DynamicArray(Right)
)

Right = Struct(
   modalityId:    String,
   readOutput:    Boolean,
   writeInput:    Boolean
)
```

Each PSK must have only one entry in the array and list all rights associated with that PSK.

Usable only with administrative PSK. Note, that the administrative PSK is not listed here. If this list is empty,
the master administrative key stays valid. Additional "administrative" keys may be created, if needed, through this interface,
not through enrollment, by assigning rights to all relevant modalities.

#### Keys

All the keys to other devices this device possesses.

```
Modality(
   id:                 "scan.keys",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "Keys",
   inputType:          "Keys"
)

Keys = DynamicArray(Key)

Key = Struct(
   device:        PeerAddress,
   psk:           PSK
)
```

When the device connects to another device, it must use the registered PSK to do so. There can be only one PSK for each
target device, to which all the rights necessary are granted on the target device.

#### Firmware

Represents the firmware on the device. All devices must support firmware updates.

```
Modality(
   id:                 "scan.firmware",
   priority:           Bulk,
   keyType:            "Unit",
   outputType:         "NextFirmware",
   inputType:          "Media"
)

NextFirmware = Struct(
   updateSource:       URI   // Vendor specific version string
)
```

The `updateSource` must be a valid URI. A GET to that URI should get an updated firmware image, if available. This means the URI
will need to likely include the current (or the next) version number, as the server will need to determine whether a new version is available.
If the server returns HTTP status code `200` for the GET, the admin interface (or whatever software is doing the downloading)
should assume there is a firmware update available for this device with the current firmware. If it returns `404`, it should assume
no update is available, i.e. the device is up to date.

It is the responsibility of the device to include any and all mechanisms to verify the authenticity of the firmware, and it should
be capable of doing this completely offline.

It is assumed that firmware updates will be applied through the administrative interface or dedicated servers. The devices themselves
should not assume that they have, or will eventually have access to the internet.

The call should be considered successful, if the reported URI changes. Note, that the device may reboot as part of the update
to finish installing. If no updates are available the caller may conclude, that the update process was successful.

#### Reboot

Reboot the device.

```
Modality(
   id:                 "scan.reboot",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "Nothing",
   inputType:          "Boolean"
)
```

Keep input `true`, until device reboots. Process should be considered successful if connection is terminated.

### Operational Modalities

Modalities supporting the day-to-day operations of the device.

All devices must implement all of these.

#### Device Information

Provide mostly static information about the device.

```
Modality(
   id:                 "scan.info",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "DeviceInformation",
   inputType:          "UserDefinedData"
)

DeviceInformation = Struct(
   deviceData:        DeviceData,
   versionData:       VersionData,
   userData:          UserDefinedData
)

DeviceData = Struct(
   name:              Text,                  // Name of the device
   description:       MarkdownText,          // Description of the device and its operation
   icon:              Icon,                  // Embedded icon for the device
   vendor:            String,                // The vendor's readable (non-localized) name
   web:               Optional(URI),         // The product's web page, if given
)

VersionData = Struct(
   hardwareVersion:   Optional(String),      // The vendor's own hardware version
   firmwareVersion:   Optional(String),      // The vendor's own firmware version
   serialNumber:      Optional(String)       // The vendor's identifier for this exact product instance
)

UserDefinedData = Struct(
   applicationName:   Optional(String),      // User editable name for the current application / environment
   location:          Optional(String),      // User editable location
   tags:              DynamicArray(String)   // User editable set of tags
)

```

The `DeviceData` fields are for display purposes to the user, mainly for the administrative
interface, but can be used for other use-cases as well.

The `VersionData` fields are for the vendor to be able
to identify products for defects, updates or other information to the users.

The `UserDefinedData` fields are completely user supplied and the user can decide how to use them. The
`applicationName` is supposed to be an application specific designator given by the user, like "Hall Light". The location might be used to help locate
the device in larger installations, like marking the room number the device is in, which compartment on a boat, etc. The tags can be
used to group devices, like all lighting fixtures, or all devices in a certain room, etc.

#### Health

Provide runtime health information about the device.

```
Modality(
   id:                 "scan.health",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "DeviceHealth",
   inputType:          "Nothing"
)

DeviceHealth = Struct(
   status: HealthStatus,                     // Overall health summing everything up

   uptime: TimeInterval,
   lastBootReason: BootReason,

   temperature: Optional(HardSoftLimited(Temperature)),
   voltage: Optional(HardSoftLimited(Voltage)),
   current: Optional(HardSoftLimited(Current)),
   battery: Optional(HardSoftLimited(Percent)),

   nonVolatileMemory: HardSoftLimited(Information),
   volatileMemory: HardSoftLimited(Information),
   cpuUsage: HardSoftLimited(Percent),

   networkErrors: HardSoftLimited(Long),
   networkLatency: HardSoftLimited(TimeInterval),

   modalityStatus: HealthStatus,             // Sum status of all modalities
   modalityHealths: DynamicArray(ModalityHealth)
)

HealthStatus = Union(Ok, Degraded, Error)

Ok = Unit

Degraded = Reasoned(Unit)

Error = Reasoned(Unit)

BootReason = Union(PowerOn, SoftwareReset, Watchdog, Brownout, Crash, Other)

Other = Reasoned(Unit)

ModalityHealth = Struct(
   modalityId: String,
   status: HealthStatus,
   outgoingCount: Long,
   incomingCount: Long,
   failedIncomingCount: Long,
   connectedCount: Long
)
```

The overall health status reflects all of the given data and also potentially internal attributes not visible through this interface.
If the health status is "Ok", it means all parameters are within acceptable ranges and all modalities are performing as desired.

Uptime and last boot reason reflect reboot behavior. Seeing low uptimes may indicate problems, as well as boot reasons, especially
brownouts or crashes.

Temperature, voltage, current and battery information are all optional and depends on whether the hardware is capable of measuring them,
or whether a battery is even present. If they are measured, the device will always supply acceptable operating ranges. Values exceeding soft limits
may be considered cause for warnings or user intervention. Exceeding "hard" limits must be considered an error state.

The non-volatile memory information can be used to check how much information the device can hold. It is used by all persistent information,
such as network settings, virtual modalities, roles, keys, etc. If those modalities start to fail, one reason might be that non-volatile memory
is not available.

Depending on the device and its use, volatile memory and CPU limits may be defined.

Network errors are either protocol-level or network-level errors that usually lead to connection termination. Ideally there should be no errors
of this kind at all. Network latency is the average time packets are acknowledges by TCP/IP.

The health of modalities can be individually inspected, where each modality is represented exactly once. A modality is considered healthy if it
did not log errors or warnings recently. If it did, but only warnings, it is considered degraded. The `failedIncomingCount` represents the number
of incoming messages that were handled with at least one error logged. While `connectedCount` is the number of remote modalities connected to this one.

#### Logs

Stream the logs from the device.

```
Modality(
   id:                 "scan.logs",
   priority:           Management,
   keyType:            "Severity",
   outputType:         "Logs",
   inputType:          "Nothing",
)

// Categorization of log entries
Severity = Union(
   Fatal,  // Entry indicates a device-wide error condition
   Error,  // Entry indicates function specific error condition
   Warn,   // Function may be degraded or did not execute fully as intended by user
   Info,   // No errors, but user may want to know this
   Debug   // Messages helpful for tracking problems
)

Logs = Stream(Log)

Log = Struct(
   severity:      Severity,
   message:       Text
)
```

This modality is keyed by severity. Each modality instance returns log entries at least the severity given in the key,
so "Error" would return "Fatal" and "Error", while "Debug" would return every log entry.

A `Fatal` severity should be used if the log entry indicates a problem not just related to a single operation or function,
but is some global error in the device that would likely prevent one or more functions to permanently fail.

An `Error` is issued if a specific function was not able to be executed at all, or will not be able to be executed in the future.

A `Warn`ing indicates that a function is executing, but not as fully intended. For example a degradation of performance of a motor because
of heat, or disabling of charging because temperature is too cold, etc.

The `Info` severity should be considered a default level on which a user is expected to watch the whole system. It should only be
used for events that would require the user's attention and not for events that would occur under normal daily operations. It is
expected that under normal operations this level would only produce at most a couple of entries per day for the whole system.

For example a light shouldn't log when its switched on and off at level `Info`, but at level `Debug`. However, a device that is expected
to be online uninterrupted may use level `Info` to indicate that it is coming online. Such an event may indicate power problems or unreliable network
connections, so its something the user might want to know, but is not an error or warning in itself.

A device switching some internal modes, that would be not evident to the user, but would require different handling from the user may also require `Info`
messages.

The `Debug` level is for everything else. Received intents, execution steps, communications, etc.

#### Backup/Restore

Backup or Restore the device's internal state or parts thereof.

```
Modality(
   id:                 "scan.backup",
   priority:           Bulk,
   keyType:            "StateType",
   outputType:         "Media",
   inputType:          "Media",
)

StateType = Union(FunctionState, ConfigurationState, FullState)
```

// TODO: how to pull a consistent state from all devices?

// TODO: how does restore actually work, since it should be a single time call, and then it may diverge

This modality can be used to backup and restore parts or all of the state of the device.

The `FunctionState` of the device is the state associated with its business logic and is usually volatile, although
it can have non-volatile parts. Example: A light may have a simple on/off business state. Restoring the `FunctionState`
on a device can be used to set a known snapshot of state. Example: Recall "Evening" settings from a scheduler for devices, or
set all lights to "Mooring" mode on a boat.

The `ConfigurationState` is all the other, usually non-volatile settings on the device. This can be used to restore a known, working configuration
after a configuration goes wrong. It does not include the administration PSK the device was enrolled with.

All outputs must be encrypted by a device internal key (generated at enrollment) that is not published. All input media needs to be
decrypted by the same key and validated to be intact. This encryption must allow for earlier backups to be resubmitted, so it can't use
key rotation, but it must use different initial vectors for all encryption operations.

// TODO

This modality has the consistent snapshot state of the device as state.
It is expected that this will modality will be used from an administrative interface that will want to receive only
one copy of the state (using infinite maximum wait) or will want to set the state only once.

The state should include all administrative settings as well as any application level data saved on the device. Ideally
it should be a dump of the non-volatile store on the device (or equivalent).

The vendor can use any format suitable for storage, but it must at least accept the same media type in the intent as it produces in
the state. Ideally this format should include checksums or authentication info making sure it is valid and can be applied.

#### Network Statistics

Report network statistics since startup.

```
Modality(
   id:                 "scan.netstat",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "NetworkStatistics",
   inputType:          "Nothing",
)

NetworkStatistics = DynamicArray(NetworkPeerStatistics)

NetworkPeerStatistics = Struct(
   remotePeer:         PeerAddress,
   sentCount:          Long,
   sentBytes:          Long,
   receivedCount:      Long,
   receivedBytes:      Long
)
```

#### Network Settings

TODO

#### State Messages

Listen in into all the state messages this device is sending and receiving. Intended to be
able to debug which remote states the device is receiving and what it reacts in turn.

```
Modality(
   id:                 "scan.messages",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "Messages",
   inputType:          "Nothing",
)

Messages = Stream(SentState, ReceivedState)

SentState = Struct(
   localModality:             LocalModalityReference,
   remoteModality:            RemoteModalityReference,
   state:                     State
)

ReceivedState = Struct(
   localModality:             LocalModalityReference,
   remotePeer:                PeerAddress,
   state:                     State
)
```

### Interoperability Modalities

Modalities responsible for creating interoperability between devices.

This specification does not define what devices may connect to this network or how exactly their modalities must look like. Every vendor may create any
device with any interface it may deem best for its use-case. Instead of agreeing on modality formats, this
specification offers mechanisms by which to transform modalities (single ones, or even aggregate many) to make them compatible
with any other device in a similar domain at the usage site.

In other words, instead of statically fixing formats, this specification enables dynamic transformations to achieve compatibility.

All devices must implement these modalities.

#### Virtual Modalities

Define virtual modalities that transform other ones into another modality.

```
Modality(
   id:                 "scan.vmods",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "VirtualModalities",
   inputType:          "VirtualModalities"
)

VirtualModalities = DynamicArray(VirtualModality)

VirtualModality = Struct(
   modality:           Modality,
   outputTransform:    DynamicArray(Byte),    // Compiled transformation program
   inputTransform:     DynamicArray(Byte),    // Compiled transformation program
)
```

Note, that if one or more remote modalities are used, those need to be wired first in order for them to be available.

Transformations are compiled platform-independent, interpreted programs, whose interpreter is described with the SCAN Type System.

The state transformation will transform all the incoming states of the modalities to a single resulting state that should match
the type specified in the modality definition. The incoming values will be placed as input to the program in the order they are defined here.

The intent transformation transforms the one intent received by this virtual modality (if changeable) and transforms it to an array
of optional intents for each of the modalities connected in sequence. Where the optional value is None, no intent will be sent. The
incoming intent will be considered applied, if _all_ outgoing intents were applied. Any errors will immediately cause the incoming intent
to be failed as well.

The intent transformation can throw an error too, in which case the incoming intent will be rejected with the given error message.

TODO: what rules apply for multiple authoritative sources, etc.?

TODO: describe how does rate limiting apply?

#### Wiring

Define what modalities on this device is wired to what modalities at other devices.

```
Modality(
   id:                 "scan.wiring",
   priority:           Management,
   keyType:            "Unit",
   outputType:         "Wiring",
   inputType:          "Wiring"
)

Wiring = DynamicArray(Wire)

Wire = Struct(
   remoteModality:     RemoteModalityReference,
   localModality:      RemoteModalityReference,  // Has to reference local peer
)
```

Note, that in order for these wirings to work, the necessary PSKs to contact the remote devices must be registered first.

Note, that the remote device will be the authoritative device for this wiring.

### Miscellaneous Modalities

There are optional modalities devices may choose to implement.

#### Locate Device

Help locate the device physically.

```
Modality(
   id:                 "scan.locate",
   priority:           Normal,
   keyType:            "Unit",
   outputType:         "Boolean",
   inputType:          "Boolean",
)
```

Devices that are potentially not visible to the user may need a way to be located. For example switching devices
built into furniture or walls, etc. These devices should provide a physical way to locate them. This can be anything
from a blinking light, a buzzer, radio location using Bluetooth, triangulation, etc.

This generic modality can be used to switch on the locating function of the device, whatever it may be. Note, that
this locator function may switch itself off after a given time period.

### Common Type Definitions

```
// A reference to a modality's index in the Modalities message.
IndexedModalityReference = Struct(
   modalityIndex:       VariableLengthInteger(8), // The index in Modalities modality array
   modalityInstanceKey: DynamicValue              // The key of the modality instance
)

// A reference to a modality in the whole system, potentially local
RemoteModalityReference = Struct(
   peer:                 PeerAddress,
   modalityReference:    LocalModalityReference,
)

// A reference to a local modality, relative a peer
LocalModalityReference = Struct(
   modality:             String,
   modalityInstanceKey:  DynamicValue,
)

// Traffic priority for network-level quality of service
Priority = Union(Critical, Normal, Management, Bulk)

// PSK (Pre-Shared Key), used for authorization
PSK = String(minLength = 32, maxLength = 32)

// Version
Version = Struct(
   major: Byte,
   minor: Byte
)

URI = String

// Represents content that is typed by mime-type
Media = Struct(
   mimeType:    String,
   content:     Stream(Byte)
)
```

## Technical Discussions

### The Resolution Principle

In SCAN any piece of state must be replaceable by newer versions of the same.

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

For example, if one device specifies that a control only need to be set once per second, it's
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

### Physical Layer Recommendations

SCAN runs over TCP/IP and does not mandate a physical layer. For electrically demanding
environments (marine, automotive, industrial) the following recommendations apply.

#### Wired Ethernet

Baseline for wired installations:

* **Cable**: Shielded Cat5e or better (S/FTP or F/UTP), with a jacket rated for the environment
(UV, oil, fuel). Cat5e carries 1 Gbps at 100m.
* **Connectors**: M12 D-coded (4-pin) for 100 Mbps, M12 X-coded (8-pin) for Gigabit. M12 is
IP67-rated and vibration-resistant, matching NMEA 2000 and industrial Ethernet practice. RJ-45
is acceptable in protected indoor environments.
* **Power**: IEEE 802.3at PoE delivers up to 25.5W per device over the same cable; 802.3bt
(PoE++) up to 90W.

IEEE 802.3 mandates 1,500 Vrms galvanic isolation (transformer coupling) between every port and
chassis ground. This is inherent in every standard Ethernet port and directly addresses
stray-current corrosion in marine and other ground-loop-prone installations.

For multi-drop clusters of low-bandwidth devices (temperature sensors, tank levels, simple
switches) where individual Ethernet drops are impractical, 10BASE-T1S (IEEE 802.3cg) provides
10 Mbps half-duplex over a single twisted pair, up to 8 nodes per bus segment, with PLCA giving
deterministic round-robin access. Bus segments join the wider network through a standard switch.

#### WiFi

WiFi suits mobile devices (displays, tablets, handheld controllers) and locations where cabling
is difficult. WiFi 6 (802.11ax) or later is recommended; OFDMA keeps local roundtrip latency in
the 2-5ms range in multi-device environments.

A wired backbone with a WiFi overlay is standard practice: wired for fixed devices, WiFi for
mobile and redundancy. WiFi must not be the sole path for safety-critical control loops - see
Deterministic Delivery below.

#### Mixed and Redundant Topologies

A single network may span wired Ethernet, WiFi, 10BASE-T1S, and internet-connected gateways.
The logical layer behaves identically across all of them and end-to-end encryption is preserved.

For high-availability installations, IEEE 802.1CB (Frame Replication and Elimination for
Reliability) duplicates frames across two disjoint physical paths (wired + wireless, or two
separate cable runs) with automatic deduplication at the receiver, providing zero-failover-time
redundancy at the Ethernet layer.

### Deterministic Delivery

TCP/IP does not inherently provide hard real-time delivery. For most use cases -- sensor data,
management, user interfaces, streaming -- this is a non-issue: TCP/IP on a local network delivers
messages in well under a millisecond through a single switch hop.

Safety-critical applications (helm-to-rudder autopilot, brake-by-wire) may require formally
bounded worst-case latency. Marine autopilot loops run at 10-50 Hz; a one-way communication
budget under 10ms with sub-1ms jitter is well within requirements. For comparison, NMEA 2000
over CAN bus has a worst-case frame time around 0.5ms. Three infrastructure tiers can meet such
requirements; each is driven by the DSCP markings defined in Tier 1.

#### Tier 1: Managed Switch with DSCP

Any managed Ethernet switch uses DSCP markings in the IP header (or IEEE 802.1p tags in the VLAN
header) for strict-priority queuing, so high-priority traffic is always served before bulk
traffic. On a lightly loaded local network this gives sub-millisecond latency for prioritized
traffic with no special hardware.

Devices must mark outgoing IP packets with DSCP values based on the effective priority of the
traffic (the subscription override if present, otherwise the modality default):

| Priority | DSCP | Value | Intended Use |
|----------|------|-------|-------------|
| Critical | EF (Expedited Forwarding) | 46 | Safety-critical control (autopilot, steering, engine) |
| Normal | AF41 (Assured Forwarding) | 34 | Regular modality state (sensors, switches, displays) |
| Management | AF21 (Assured Forwarding) | 18 | Device management (health, logs, configuration) |
| Bulk | BE (Best Effort) | 0 | Large transfers (firmware, backup, media streams) |

DSCP is set per-socket, not per-packet. When a TCP connection carries subscriptions with
different priorities, devices must set the connection's DSCP to the highest effective priority
among its active subscriptions. In practice device pairs have narrow relationships -- an
autopilot exchanges only steering data (Critical) with a rudder sensor; firmware updates come
from a separate administrative device on a separate connection (Bulk) -- so priority separation
follows naturally from the topology.

#### Tier 2: Bounded Latency (Credit-Based Shaper)

IEEE 802.1Qav (Credit-Based Shaper) provides formally bounded worst-case latency without global
time synchronization:

* Class A: ≤2ms over 7 hops.
* Class B: ≤50ms over 7 hops.

Available on industrial managed switches, which map traffic to stream-reservation classes using
the DSCP markings above. Sufficient for marine, building, and most automotive control.

#### Tier 3: Hard Real-Time (Time-Sensitive Networking)

For hard real-time guarantees with formal certification, IEEE 802.1 Time-Sensitive Networking
(TSN) provides deterministic delivery at Layer 2:

* **IEEE 802.1AS**: sub-microsecond clock synchronization across all nodes.
* **IEEE 802.1Qbv** (Time-Aware Shaper): scheduled time slots with exclusive access for
high-priority traffic; worst-case latency ~100µs over 5 hops with sub-µs jitter, matching or
exceeding CAN bus determinism.
* **IEEE 802.1CB**: seamless redundancy with zero failover time (see above).

TSN switches classify traffic into time-scheduled slots using the DSCP markings above. Both
the switches and device Ethernet controllers must support the relevant TSN profiles. 

Recommended where regulatory certification requires formal proof of bounded latency (safety-critical
steering, engine control).

#### Summary

| Tier | Mechanism | Worst-Case Latency | Hardware Required | Use Case |
|------|-----------|-------------------|-------------------|----------|
| 1 | DSCP/802.1p strict priority | Statistical (sub-ms typical) | Any managed switch | Most deployments |
| 2 | IEEE 802.1Qav (CBS) | 2ms over 7 hops (Class A) | Industrial managed switch | Control loops, building automation |
| 3 | IEEE 802.1Qbv (TAS) + 802.1AS | ~100µs over 5 hops | TSN switch + TSN-capable NICs | Safety-critical, certified systems |

All three tiers carry best-effort bulk data and safety-critical control on the same network,
differentiated by the DSCP markings defined in Tier 1.

