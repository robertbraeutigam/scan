# SCAN, Simple Control and Acquisition Network

*Draft Version*

SCAN is an open, free, easy-to-use, extensible, DIY-friendly
way to collect data and control devices on a network.

## Functional Goals

The protocol is specifically created to support a wide-range of possible use-cases:

- *Home automation use-cases*. Having a wide array of sensors, devices and controls controlling each other,
  possibly including everything from doors, windows, lights to audio or video feeds.
- *Building control*. Controlling access, security cameras, doors, windows, AC, etc.
- *Automotive*. Controlling actuators, servos with feedback in a closed system.
- *Marine*. Controlling all functions of a boat, including gps, plotters, lights, engines, video surveillance in one
  standardized yet extensible, 3rd party friendly way.

## Non-Functional Goals

The main considerations driving the design of this protocol:

- **Security first**! State-of-the-art end-to-end peer-to-peer encryption, perfect forward secrecy, no hardcoded defaults,
  per-device distributed authorization, etc., without complex certificate management, central servers,
  authority nor any third-party involvement.
- **DIY** friendly. No proprietary components, processes or certification requirements. No central registries.
  A private individual should be able to easily create a compatible device completely
  independently, which can then be used by any end-user to its full extent and be combined with any
  other device that has or needs similar enough features.
- **Discoverability**. It should be very easy to *discover* which devices need what inputs and react to-, or control
  what other devices. Not out-of-band, for example through documentation, but through dynamic discovery
  through the protocol itself.
- Enable **interoperability** **without a perfect list of codes** of
  semantic identifiers, and without everybody needing to agree on a single unified set of messages
  for devices to be interoperable.
- Prevent proprietary extensions and the need to debug or reverse-engineer devices,
  by using transparent **dynamic wiring**, instead of devices directly hardcoded for each other or for specific codes.
- **Failure tolerance** build into the protocol. The whole system system should be **eventually consistent** after errors are resolved.
- **Optimal network usage**. Enable optimal usage of network resources by default.
- **Minimal effort** implementations. The effort to implement compatible devices
  should be linear to the extent of features the device will support. I.e.
  simple devices (like a Light source or a Button) should be almost no effort to implement, while
  more complicated devices may require more thorough analysis and design.
- **Minimal setup** required by end-users, ideally plug-and-play in most cases to a *fully-secure* production installation.
- Should work **over the internet** and in any network topology, trusted or not.
- **Driven by individuals**, not industry. I.e. does not have to support complicated
  use-cases which are almost never used, or used only because of historical reasons.

## Out-of-scope

This protocol does not replace radio-based protocols such as Bluetooth, Bluetooth Low Energy, LoRaWAN, etc.

Although devices using these protocols may be connected through some gateway into a SCAN network, they can not
directly participate as these protocol have their own definitions of security, data exchange and semantics, which are
incompatible with SCAN. Alternatively, embedding SCAN traffic in any of these would not be an optimal use of these
protocols.

## Solution Overview

### The Protocol

The SCAN protocol is divided into four layers:

- Internet Layer (Packet Communication and Announcement over IP)
- Logical Layer (Security, Multiplexing, Fragmenting, Logical Connections, Messaging)
- Data/Command Layer (Subscribing to Data and issuing Commands)
- Application Layer (Required and common Data and Command definitions)

The Internet Layer is the actual transport infrastructure on top of IP that facilitates the transport of single
packets between devices and enables announcements. It supports different IP topologies, including
local networks, connections over gateways, etc.

The Logical Layer is responsible for providing a secure, flat, multiplexing and mixing capable layer
for communications between devices. Devices are identified, addressed based on static cryptographic keys instead
of hardware addresses and communicate point to point using a packet-based protocol that supports easy
multiplexing as well as unlimited length streaming messages.

The Data/Command Layer adds minimal quasi-request-response based interface that enables:

* Subscribing to *data* emitted by devices, which are a stream of changes in the state of the device.
* Invoking *commands*, which may change the state of the device.

The application layer defines common data elements and commands that are either optional
or required for every device, such as setting up roles, resetting, unified software update command,
debugging and logging commands and data, and most notably wiring.

Wiring is an application layer tool that describes how devices interoperate. It describes which
data from which devices invokes which commands at what other devices and it can also describe
how to transform data to be compatible with the required command.

### Operational Summary

SCAN is a peer-to-peer, distributed protocol, where all devices are potentially
data acquisition or control devices or both. Therefore two devices are already capable
of working together without any dedicated control device or server. Introducing
more components does not require any central component to exist either, although the
option is available if needed.

SCAN devices from the "factory" need to be provisioned first, which means that they need
to generate a new key for the administrator (the end-user) to use. After they are provisioned,
they can be *wired* to get data from, or send commands to another device or multiple other
devices.

The SCAN protocol goes well beyond being just a transport protocol to deliver messages among
devices. It has semantic rules, which constrains what messages may mean, in order to give
stability guarantees, such as guaranteed recovery from error states, recovery from network
congestion, restarts, and other failure modes.

## Internet Layer

The internet layer supports basic network primitives based on IP-native means for the next layer.
These functionalities are:

* Open and receive a "physical" TCP/IP connection to/from a peer to send and receive data.
* Send and receive data to / from all connected devices.

This layer mimics IP closely. Meaning connections support streaming-based data exchange
with no packet demarcations, while communication with the whole network supports stateless
packet based communication.

Addressing uses native IP addresses.

There can be multiple ways of configuring the SCAN device. However
this configuration should be completely transparent for the layers above.

Devices must reuse TCP connections in each direction, therefore
at most two TCP connection must be present between two given peers at all times, one for each
direction.

### Local Network Configuration

Every device must be capable of operating in a local network, where other devices are directly
addressable and all devices can be contacted by multicast packets. In this scenario:

* Connections are made / received using TCP/IP on port 11372.
* All devices are addressed over UDP, at the address 239.255.255.244:11372.

Note, that this "local network" does not necessarily need to be "physically" local network,
it can be a virtual local network that connects multiple devices, possibly through VPNs or other means.

### Gateway-based Configuration

Devices may support connecting through "Gateways". A "Gateway" is a SCAN "Logical Layer" level software or hardware
device that does not necessarily have an "Application Layer" presence, i.e. it may be invisible
to the SCAN network, but can present all the devices that connect to it.

In this scenario the software stack on the device that connects through a Gateway maps  operations thusly:

* Connections are always made with the Gateway on port 11372 (the same SCAN port as above).
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

In addition, the network must support at least one direction of communication between devices
or devices and gateways.

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
intentionally for enery saving purposes. Offline devices may reconnect and continue sending messages without additional handshakes.

The initiating party must not retry opening connections more often than 10 times / minute, but may implement any heuristics
to distribute those reconnects inside the minute.

### Data Types

A variable length integer (VLI) is a number stored as a variable number of bytes, at most 8, in big-endian
ordering. On each byte except the last the highest bit indicates that a byte still follows. Note that
the frame header contains a 2 byte VLI, while the rest of the specification has 8-bytes VLIs.

### Frame Header

All packets have the following header:

* Header (1 byte) (clear-frame type is aad)
* (optional) Source peer (32 bytes) (clear)
* (optional) Destination peer (32 bytes) (clear)
* Number of following bytes, excluding MIC (2 byte VLI) (clear)

The header describes what this frame means and certain format parameters. It is organized as follows:
* Bit 0-5: Frame type (see next chapters)
* Bit 6: Whether the source peer is given
* Bit 7: Whether the destination peer is given

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

### Frame types

Devices must ignore frame types they do not support. Ignoring a frame means to skip the given amount of
bytes in the stream, and then sending back an "Ignored Frame" (05) message.

Frames are categorized into several intervals:
* 0-15: Control messages. These are related to establishing or closing the connection.
* 16-47: Payload messages, related to the actual payload of the communication.
* 48-63: Messages that potentially are broadcast.

All payload messages (frames 16-47) will cause the encryption keys to be rotated. Each message will
be sent with a completely new key for absolute forward security.

All payload messages are encrypted and will include a MIC after the payload message. When skipping a frame,
the appropriate MIC length of the established crypto need to be skipped as well. Note, this is not included
in the length field.

#### Frame type: 01 (Initiate Handshake)

Sent from the initiator of the connection to establish a logical connection.
If a physical connection does not exist yet, the initiator must open one first.
The frame transmits the first handshake message together with the
Noise Protocol Name.

Payload structure:
* Noise Protocol Name (string) (clear-prologue)
* Handshake (byte array)

The Noise Protocol Name is the exact protocol used for the following handshake
and data exchange. If the recipient disagrees with the protocol it must close the
logical connection.

The protocol name has to be included in the *prologue* of the Noise Handshake to
make sure it has not been tampered with.

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
This is a command each device must support (see relevant chapter). During this process however, the device
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

#### Frame type: 02 (Continue Handshake)

Sent potentially by both parties. It continues the handshake after it has been initiated.
The first continue handshake must come from the responder, then from the initiator
and continue in turn until the connection is established based on the initially selected
protocol variant.

Payload structure:
* Handshake (byte array)

#### Frame type: 03 (Close Connection)

Both parties may send this message to terminate the logical connection. After this message
all keys and state information about the connection can be discarded.

This message has no payload.

#### Frame type: 15 (Ignored Frame)

Both parties may send this message to indicate that the given frame type was not known,
therefore was ignored and skipped.

Payload structure:
- Frame type (byte)

This message supports a backward-compatible upgrade path of the protocol. Future versions may
decide to add new message types, and may fall back to an earlier version of the protocol, if
this frame is received.

#### Frame type: 16 (Application Message Intermediate Frame)

A part of an application message, including the initial frame, but not the last frame. This frame indicates
that the message is not complete, additional frames will follow for this message.

The actual payload of the application layer is described in the next chapters. This message
may be sent by both the initiator and responder.

Payload structure:
* Message Id (variable length integer, clear-aad)
* Payload (encrypted)

If any decryption errors occur, meaning that for some reason the sender and receiver becomes
out of sync, messages were omitted or repeated, the connection must be closed.

All encryption happens with "nonce" of all zero. Note, that each message encryption will use a completely
new key, so the nonce is superfluous.

If a message is too large to fit
into one Application Message frame, it must be fragmented, with each fragment having the
same Message Id. A sender may also choose to fragment messages for other reasons, for example
to get video frames that are already available quicker to the receiver to reduce lag.

The Message Id identifies this message and all frames it consists of. Message Ids should
be re-used to be able to keep the Id low and in one byte. All values for which
a Last Frame has been sent must be considered re-usable.

#### Frame type: 17 (Application Message Last Frame)

The last frame of an application message. This frame may also be potentially the first and only
frame the message has, although in this case the Single Frame Application Message should be preferred.
It indicates that the application message identified by Message Id is complete with this payload.

Payload structure:
* Message Id (variable length integer, clear-aad)
* Payload (encrypted)

Encryption and key management is the same as for intermediate frames.

The Message Id used in this frame must be considered reusable after this frame is sent.

#### Frame type: 18 (Single Frame Application Message)

An application message that fits a single frame.

Payload structure:
* Payload (encrypted)

Encryption and key management is the same as for intermediate frames.

#### Frame type: 33 (Identity Announcement)

Announces the identity or identities represented by a device. Every device must send
identity announcements approximately once per second.

Payload structure:
* Static keys... (32 bytes each)

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
without doing a new logical handshake. It may just continue to send messages normally, as if nothing had happened.

Note however, devices are not required to be able to persist connection information, and may even handle
offline devices with closing the connection and forgetting the keys altogether.

If, after a device has been *offline*, cryptographic keys become out of sync, or those keys simply no longer exist, the connection must be closed,
forcing the initiator to establish a new logical connection with new keys.

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

Each device may monitor identity announcements for two reasons:
* To maintain a mapping of IP address to public static address key
* To maintain "offline" status of devices

A device which does not monitor announcements all the time may need to wait a couple of seconds
to detect identity announcement it is interested in. Maintaining a cache of announcements speeds
up this discovery, but is not required.

Monitoring offline status is not required only if the device is sure this information is not needed
by the programming of the device itself.

If an IP address can not be found for a given identity key, the connection can not be established.
Devices may choose to display this to the user if capable, or may send specific error events through
other logical connections.

## Application Layer

The application layer is defined by specific payloads in Application Messages and their
choreography after a logical connection on the network layer has been established.

The Initiator of the network connection is called a Controller Device on this layer, while the Responder
is called the Controlled Device. A single logical connection only allows for one side to be the Controller.

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

### Data Types

A Value structure is a single value to a type defined elsewhere. It's structure is:
* Identifier (variable length integer)
* Length of following Value (variable length integer)
* Value (byte array)

The Identifier above refers to some definition specific for the context this
Value structure is used in. The Value, specifically its meaning is
also defined by the entity referenced by the Identifier.

Note that the length here is the maximum length of the value. If this structure is at the
end of a message, the message may end before the given length is reached. This is explicitly
allowed to support unlimited streams, which should use a maximum value length (2^58-1). 

This also means that in every message, there may only be one value structure that has its
length not given exactly.

The length field exists for the explicit purpose to skip a value structure if the contents
can not be interpreted.

### Requests

A request is a message sent from the Controller to the Controlled. Its format is as follows:
* Action (byte)
* Content

All requests contain an action (see below) and a content.

Devices must explicitly answer action codes that they don't understand with IGNORE responses.

#### OPTIONS (01)

Request the Controlled to supply meta-information about the device itself, including
what Controls it has, what Data it can provide, what Wiring it has currently configured.

Action will have at most one answer.

Content:
* Locale (string, IETF BCP-47 format)

The locale specifies the name and description language the options should be described in.
The Controlled Device should honor the locale if possible, but may fall back on a default
language if it does not have translations for the requested language.

#### STREAM DATA (02)

Request the Controlled to send values for the specified Data Packet indefinitely.

Action will have any number of responses.

Request content:
* Data Packet Id (variable length integer)
* Locale (string, IETF BCP-47 format)
* Maximum Rate (variable length integer)

The Data Packet Id specifies the data to send.

The locale applies to all potential text like names and descriptions that may be part of the data
requested. This locale may be empty, in which case the Device should use a default translation
for any data elements that are language dependent.

The Maximum Rate specifies the rate at which the data should be sent in milliseconds. More specifically
the time interval between the beginnings of sendings (i.e. not between the end of one data sending to the next beginning). 0 milliseconds
means as fast as possible, without any waiting. The Controlled Device must honor this rate
in every case, even if the data is generated by manual input. It must never send data more
frequently than specified. It may however send data less frequently.

The Controller may repeat this message if the maximum rate changes for any reason.

The Controlled Device should send the current state as soon as possible when receiving this request,
and re-calculate the time for the next message. Sending this request with a maximum rate can therefore
be used to request one data message, essentially immitate a pull-based approach.

The Controlled Device must not send messages for the same data packet in parallel. It must always send messages
for the same data sequentially.

### INVOKE (03)

Request to invoke a command defined on the other party.

Request content:
* Command Id (variable length integer)
* Parameter Values (Value structures)

### Responses

A response is sent from the Controlled to the Controller, always as a response to a previous
request. There may be multiple responses to the same request, depending on the action
requested and other circumstances.

The format of the response is as follows:
* Response Type (byte)
* Content

The Response Type and its content is described in the next sections.

#### OPTIONS (01)

Send all the meta-information this device has, specifically
data definitions, command definitions and wiring information.

Content:
* Number of Data Packet Definitions following (variable length number)
* Data Packet Definitions
* Number of Command Definitions following (variable length number)
* Command Definitions
* Wiring

A single Data Packet Definition record consist of:
* Name (localized string)
* Description (localized markdown string)
* Number of Tag Definitions (variable length number)
* Tag Definition records
* Number of Data Element Definitions (variable length number)
* Element Definition records

Note the Data Packet Id is the index of the definition, starting at 0. Similarly
the Data Element Id and Tag Id are the indices of the definitions in this structure,
starting from 0.

Both the Tag and Data Element definitions follow the same structure, only
the meaning of the codes is different, as they are defined separately. See
appropriate Appendix.

A Definition record consists of:
* Name (localized string)
* Description (localized markdown string)
* Application Type Code (variable length number)
* Predefined Type Code (variable length number)
* Type Definition

Note that even if the semantic is defined to have a given type, everything is defined in
this definition anyway. This is to allow devices to work with semantics that are not
yet known and/or ignore them completely if they are not needed.

All definitions must correspond to the definitions given in this document however. Any errors
will be caught by the wiring, when data elements are wired to commands.

A Type Definition consists of:
* Type Code (variable length number)
* Length of following description (variable length number)
* Type specific description

The type specific description is given in the appropriate Appendix.

Command definitions are similar in structure to Data definitions, only they don't contain
Tags:
* Name (localized string)
* Description (localized markdown string)
* Number of Data Element Definitions (variable length number)
* Element Definition records

The wiring is described as a small complied binary language. See appropriate Appendix.

#### DATA (02)

Send data values.

Response content:
* Data Packet Id (variable length number)
* Tag Values (Value structures for the defined Tags)
* Data Element Values (Value structures for the defined Data Elements)

The Data Packet Id identifies the Data Definition that describes the meaning of the
values submitted here.

Note, that because of the Resolution Principle the Device must immediately
send the newest data value upon receiving a Data Request. This may involve taking
an immediate measurement, or may involve sending a cached value from memory, but
the value must always be the most current one in the given semantics.

Note also, that this semantic may include a month-end meter value for example. "Historical"
values are allowed, as long as this does not mix with "current" values. I.e. it has to have its own
data definition, which presumably includes a timestamp or interval.

#### INVOKE RESPONSE (03)

Indicate the rate the given command can be invoked.

Request content:
* Command Id (variable length integer)
* Maximum Rate (variable length integer)

Same as the maximum rate specified in STREAM DATA message, i.e. a millisecond value of time between subsequent
requests. The Controlled indicates at which rate this
command can be invoked. The Controller must honor this rate and never invoke the command
more frequently than given.

Controlled devices are not required to send this response, or they may send it multiple
times. The Controller must honor the latest supplied maximum rate at all times.

The Controller may proceed to invoke the command at any rate until the Controlled
explicitly answers with a given rate. The Controlled is also free to skip certain
command invocations, if it can be immediately replaced with a newer one.

#### IGNORE (255)

Devices must answer an unknown request code with this response.

Content:
* Action (byte)

Content is the action code that was ignored.

## Technical Discussions

### The Resolution Principle

In SCAN any piece of data or command must be replaceable by newer versions
of the same data or command.

This means that any stream of data or commands *for the same thing*
may be simply substituted by the last (newest) element. In other words, losing messages
will only decrease the *resolution*, not change the *meaning*.

"The same thing" is called a *modality* for the purposes of this specification. A *modality*
is a single semantic entity on the device. A single physical sensor or a single logical
entity that emits data or commands. For a modality any emitted data makes any old data
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
send all relevant newest data and/or commands immediately upon the connection is established
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

SCAN guarantees that *the most current data* is delivered *as fast as possible* at all times.

The first part of that guarantee is that at least the most current data for each modality *will*
be delivered eventually. That means if there is a new piece of data or there is a new command
it will not get lost, only possibly replaced by an even newer piece of data or command for that same modality.
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

SCAN networks can form a complex graph of devices communicating, issuing data and commands to each other.
Rate Limiting is the way SCAN ensures that throughout all processing chains data and commands
are generated at _exactly_ the same rate as they are consumed throughout the whole chain, _and_ at the
exact right time.

For example, if one device specifices that a command only need to be invoked once per second, it's
controller is notified of this fact (see message descriptions). From this point, the controller
is responsible for the timing of the controlled device. If that controller relies on another device
for some events, it pushes the rate limiting further upstream, making the first device in the chain
responsible for the timing of all downstream devices. Devices can also use "pull"-based event
reception for more complex dependency trees or graphs.

Note that network backpressure alone does not lead to an ideal producer-consumer synchronization. 
Network backpressure is not specific to a single command or data, but may influence all the
communication on the network. It would be difficult to isolate which communication channel, if any,
caused network congestion. This uncertainty and imprecision may cause various communication artifacts,
including network over-use, messages coming in batches or waves, or even not finding a steady state.

Rate Limiting is an explicit measure based on application-level feedback to senders from receivers. All
receivers must define the maximum rate at which they can consume messages, or more accurately, they
should define the minimum practicable rate for the given use-case. This is for both data and
command invocations. This measure is trivially independent of network problems, therefore can work
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
* **Controller**: The *Initiator* on the application layer. The party who invokes commands
on the other party.
* **Controlled**: The *Responder* on the application layer. The part who responds to commands
from the other party.
* **Modality**: A single physical or logical entity in a device, for which any stream of
consecutive data items (or commands) can be replaced with the latest one without altering
the meaning of the stream.
* **Operator**: A human user configuring the network and/or devices.

## Appendix B: Types

SCAN has a tiered type system. This means types are defined on multiple tiers (layers), where
each tier is using the tier below to define itself. The lower tiers are more technical and generic,
while upper tiers are richer in semantics, but narrower in usage.

The purpose of this tiered system is for devices to define as much meaning as possible for each
data element or command parameter. Even if the corresponding meaning is not found on a given
level, there may be an appropriate lower level meaning to be found.

All of these types on all Tiers may be extended by later specifications. However, they are
created in a way that devices may simply ignore or skip values they don't understand. 

A type is always defined with at least the primitive type that specifies the physical length
of the value and some minimal semantics, whether it is a number of string, etc. All the layers
above are optional and only add context and meaning to this one value, with the top layer
actually defining the exact use for the specific application at hand.

So to full define a type, the following structure is used (Type Definition):
* VLI, the length of following bytes
* Tier 0 code (1 byte), the actual primitive type of the value under definition.
* Tier 1 code (1 byte), the format of the value or how to interpret it.
* Tier 2 code (1 byte), the usage-specific semantics of the value.
* Tier 1 definition of the Tier 2 type (length depending on Tier 1 code)
* Tier 3 code (1 byte), the application specific semantics

### Tier 0: Primitive Types

Value Types are the actual values in data messages or actual parameter values for invoking
a command. All following Tiers just add more meaning but don't change the actual format described
here.

All value types are defined in 1 byte, where the byte itself is organized as follows:
- Bit 7-5: The size of the value
- Bit 4-0: How the bytes are to be interpreted.

The size of the value may be the following:
* 0 = Value will start with an max 8-byte VLI that will define its size.
* 1 = 1 Byte
* 2 = 2 Byte
* 3 = 4 Byte
* 4 = 8 Byte
* 5 = Unused
* 6 = Unused
* 7 = VLI

Following interpretations are available for the above sizes:
* 0 = Byte Array
* 1 = String (UTF-8 characters)
* 2 = Unsigned big-endian number
* 3 = Signed big-endian number
* 4 = Floating Point Number
* 5 = Boolean (0=False, 1=True)

Note, that there are some combinations that are not defined and must produce an error when encountered:
* Boolean is only defined for 1 byte.
* Floating point is only defined for 4-byte (single) or 8-byte (double) precision.
* Numbers are only defined for 1 to 8 bytes.

### Tier 1: Definition Types

These are types that have enough meaning to be used in definitions. This is the minimal meaning
all data packet elements and command parameters must carry.

These are the currently supported Definition Types:

| Name                | Code    | Definition  | Tier 0      | Description                                      | Example Semantic  |
|---------------------|---------|-------------|----------------|--------------------------------------------------|-------------------|
| Media-Type          |       1 | String (the Media-Type name) | Byte Stream | Format is defined by the given Media-Type. Note that this type is opaque for SCAN, therefore no parameters can be defined here. | Video stream.
| Defd. Number Enum   |       2 | Tier 0 Code, Count (variable length numer), Values (Count number of values of specified Type) | Integer (Index of value) | A predefined set of number values. | Presence, Switch state, Multi-state switch state. |
| Undef. Number Enum  |       3 | Tier 0 Code | (As specified)  | A dynamic or larger, but bound set of number values. | I2C Address. Or even IP address, if known to be bound for use-case. Window name (for String). |
| Measurement         |       4 | Unit (See Appendix.)  | Double | A measured value of some unit. | Voltage of a pin, temperature, remaining battery capacity. |
| Measurement Aggregate |     5 | Id of parent Measurement, Aggregate Type (01-Min, 02-Max, 03-Avg, 04-Count of measurements that were aggregated) | Double | A measured or calculated minimum of the given parent measurement over a context/description-defined time period. | Voltage of a pin, temperature, remaining battery capacity. |
| Localized String    |       6 | Empty       | String         | Arbitrary, unlimited value set string. | Current status localized string. Manufacturer name. |
| Markdown             |       6 | Empty       | String         | Arbitrary, unlimited value set string. | Current status localized string. Manufacturer name. |
| Arbitrary String    |       6 | Empty       | String         | Arbitrary, unlimited value set string. | Current status localized string. Manufacturer name. |
| Unix Timestamp      |       7 | Empty       | Integer     | Unix timestamp with millisecond precision. | Effective date, Billing date. Last update time. |

The "Definition" column specifies what parameters this type requires for the definition in a Data Packet.

### Tier 2: Usage Types

Types that are usage specific, but are generic enough to be not application specific. It is *not necessary* for a device to use
usage types, all types can be ad-hoc defined.

Devices should however use usage types wherever they can to make *wiring*
easier for an operator.

| Name             | Code    | Tier 1           | Description                                      |
|------------------|---------|------------------|-------------------------------------------------|
| None             |       0 | Any              | No defined usage type applies to the data at hand. |
| On-Off           |       1 | Enum 0=Off, 1=On | Indicates an operational status of either on or off. |
| Latitude         |       2 | Measurement, deg | |
| Longitude        |       3 | Measurement, deg | |

TODO: Manufacturer Name
TODO: Add constraints to Lat/Long ? And other stuff.

### Tier 3: Application Types

The purpose of application types is for the device to express what a data element
or a command parameter means *in the current application*. It
is *not necessary* for a device to use application types. Nor is it necessary for
a device to know these.

Application types are merely a *convenience* to enable easy *wiring* of data and
commands. Even without them operators are still perfectly capable
of connecting data and commands based on their understanding.

| Name             | Code    | Tier 2          | Description                            |
|------------------|---------|-----------------|----------------------------------------|
| None             |       0 | Any             | Data element has no defined type specific to this application. Use when no application types apply. |
| Main Power       |       1 | On-Off          | The power state of the whole system. Use when the power state of the whole system represented by the SCAN network is involved, if there is such a thing.              |
| Misc. Power      |       2 | On-Off          | Power state of a part of the system. Use for miscellaneous power states across the system if no more appropriate semantics can be applied.                         |

## Appendix C: Unit System

The Unit System of SCAN describes what Units (in the general sense, not strictly in the SI sense)
can be used for measured values. Examples include: m³, kg, bit, m³/h, %, etc.

The SCAN Unit System is not a complete SI-derived system. It is specifically designed to be easy
to use, minimal as possible and customizable. Dimensions (such as Power, Current, etc.) are not defined
at all. Instead each measurement must define the exact Unit its values are published in, or for a command,
define what unit the values are expected in. Possible conversion happens in the wiring, not in the device
itself. The device itself does not have to convert, validate or calculate between units.

Whether the units are "compatible" is also not explicitly defined in this specification. It is left
up to the wiring software or the human operator to figure out how to convert between compatible
units or indeed to determine whether two units are compatible in the first place. Devices do not have to
include any of this knowledge, instead must execute the wiring as configured and should assume that the
result of any calculation given there will match the unit defined by the receiver.

Prefixes or prefix multipliers, like Kilo, Mega, Milli, KiBi, etc. are also not defined. Definitions
can instead contain literal values matching the desired multiplier. Presenting devices may choose to
interpret and show corresponding multiplier symbol if needed.

This specification defines mostly Base Units only (some are actually derived units, but are included for convenience anyway)
and a description language to arbitrarily combine Base Units
and Scalar values.

All values for all units are typed double (that is, 8 bytes).

### Base units

|   Code |   Symbol | Description                       |
|--------|----------|-----------------------------------|
|      1 |        m | meter                             |
|      2 |        g | gram                              |
|      3 |        s | second                            |
|      4 |        A | ampere                            |
|      5 |        K | kelvin                            |
|      6 |        C | degrees Celsius                   |
|      7 |       cd | candela                           |
|      8 |      mol | mole                              |
|      9 |       Hz | hertz                             |
|     10 |      rad | radian                            |
|     11 |      deg | degree                            |
|     12 |       sr | steradian                         |
|     13 |        N | newton                            |
|     14 |       Pa | pascal                            |
|     15 |        J | joule                             |
|     16 |        W | watt                              |
|     17 |        C | coulomb                           |
|     18 |        V | volt                              |
|     19 |        F | farad                             |
|     20 |      Ohm | ohm                               |
|     21 |        S | siemens                           |
|     22 |       Wb | weber                             |
|     23 |        T | tesla                             |
|     24 |        H | henry                             |
|     25 |       lm | lumen                             |
|     26 |       lx | lux                               |
|     27 |       Bq | becquerel                         |
|     28 |       Gy | gray                              |
|     29 |       Sv | sievert                           |
|     30 |      kat | katal                             |
|     31 |        l | liter                             |
|     32 |      bit | bit                               |
|     33 |        B | Byte                              |
|     34 |       pH | pH                                |
|     35 |       dB | decibel                           |
|     36 |      dBm | decibel relative to 1 mW          |
|     37 |    count | 1                                 |
|     38 |    ratio | 1                                 |
|     39 |       VA | volt-ampere                       |
|     40 |      var | volt-ampere reactive              |

### Unit description

The unit description is a reverse polish notation format expression of units and constants. It is used
to express the exact type of measurement or input in a machine readable format.
It is capable of describing derived units that are multiplications or divisions
of other units or constants. It can not describe offset or logarithmic relationships.

The format is:
* Length of bytes following (variable length integer)
* Description bytes

Where description bytes are a sequence of the following possible phrases:
* Put Base Unit Code (1 byte value less than 250) on the stack
* Value 250, followed by a double value for putting a constant on the stack
* Value 251, multiply the top 2 things on the stack
* Value 252, divide the top 2 things on the stack

For example "g" (gram) would be written: 01 (length) 02 (base code for gram)

"Kg" (kilogram) would be: 0B (length) 250 (constant follows) 1000d (1000 in double format) 02 (base code for gram) 251 (multiply)

Derived units, both multipliers and the units themselves should be formulated in the order
its most commonly used in for easier consumption. I.e. "Kg" should be 1000 g * and
"kmh" should be 1000 m * 3600 s * /.

## Appendix D: Wiring Language

Wiring is a description of how to transform current states of modalities into derived states and/or
commands. While it might look like an event processing system or an information flow processing system,
it is not. Wiring is a stateless process that does not have any notion of time nor flow.

This is on purpose and a consequence to the Resolution Principle. Any sequence of events of the same
modality can be represented by the newest event. Therefore there are no sequences. The network can optimize
any sequence of events or flows to the most current item.

In practice this means that wiring can't express anything that involves *how* and in what sequence
a state came to be. For example: Wiring can't determine whether the temperature is rising or falling
by looking at the current temperature, can't determine whether some state was present for a given period of time.

Note however, that all those use-cases are actually possible if the required data is part of the current
state of modality. For example including the change of temperature with the current temperature data packet.

### Structure

The wiring language is a binary structure either generated directly or compiled from some textual or graphical
description (language not defined here). Structure as follows;
* 'S','W' (2 bytes for ASCII characters 'S' and 'W')
* Version (byte)
* Number of Access Table Entries (variable length integer)
* Access Table Entries
* Number of Data Packet Definition (variable length integer)
* Data Packet Definition (same strucutre an in OPTIONS)
* Number of Transformation Entries (variable length integer)
* Transformation Entries

The Version is 01 for this specification.

The Wiring may emit additional derived Data Packets, those have to be declared
in the Wiring structure. These definitions will be also returned in the OPTIONS
reply from Device.

An Access Table Entry is composed of:
* Public Static Key of Controlled Device (32 bytes)
* PSK for logical connection to that Device (32 bytes)

Transformation Entries describe a single filter condition and the transformation
that should happen in case it is executed. Its contents are as follows:
* Filter Expression (see below)
* Statement (see below)

### Expression

Expressions are typed according to Tier 0 types. Expression are reverse polish notation
sequence of operators and values. Following terms are possible:
* Referenced Values. Values from Data Packets.
* Referenced Tag Values. Values from Data Packet Tags.
* Constants. Different representations for all types.
* Internal Function Call. Call one of the predefined Functions on the values on the stack.
* Operators.

Referenced Values are:
* 01 (Byte)
* Device Id (variable length integer)
* Data Packet Id (variable length integer)
* Data Element Id (variable length integer)

Referenced Tags are:
* 02 (Byte)
* Device Id (variable length integer)
* Data Packet Id (variable length integer)
* Data Tag Id (variable length integer)

Constants are:
* 03 (Byte)
* Tier 0 Code (variable length integer)
* Value based on Type Code

See next chapters for Functions and Operators.

#### Operators

For all bounded Enum types that have exactly 2 values, Wiring assigns "false" to value 0
and "true" to value 1. Using this interpretation, following logical operators are available:

| Code     | Symbol  | Description                        |
|----------|---------|------------------------------------|
|       17 |      && | And                                |
|       18 |      || | Or                                 |
|       19 |       ^ | Xor                                |
|       20 |       ! | Not                                |

Following relational operators are available among Integer and Double types:

| Code     | Symbol  | Comment                            |
|----------|---------|------------------------------------|
|       33 |      == |                                    |
|       34 |      != |                                    |
|       35 |       < |                                    |
|       36 |       > |                                    |
|       37 |      <= |                                    |
|       38 |      >= |                                    |

Following relational operators are available among Byte Streams and Strings:

| Code     | Symbol  | Comment                            |
|----------|---------|------------------------------------|
|       49 |      == | Needs to wait until end of stream or string. This might take multiple frames, or even unlimited number of frames. |
|       50 |      != | May also need unlimited number of frames to terminate. |

For Integer and Double types following arithmetic operator are available:

| Code     | Symbol  | Comment                            |
|----------|---------|------------------------------------|
|       65 |       + | Exact add. For doubles this always works. For integers a wiring error is raised for overflow. |
|       66 |       - | Exact subtract. For doubles this always works. For integers a wiring error is raised for underflow. |
|       67 |       * | Exact multiple. For doubles this always works. For integers a wiring error is raised for overflow. |
|       68 |       / | Exact divide. Dividing with zero raises error. For integers the result is a division without remainder. |
|       69 |      +d | Double add. Convert both arguments to Double then do Exact variant. |
|       70 |      -d | Double subtract. Convert both arguments to Double then do Exact variant. |
|       71 |     \*d | Double multiple. Convert both arguments to Double then do Exact variant. |
|       72 |      /d | Double divide. Convert both arguments to Double then do Exact variant. |
|       73 |       - | Unary operator "negate". Result is always a Double.                |
|       74 |       % | Modulo / remainder. Result is always an Integer. |

#### Functions

Following functions are available. These work similarly to operators except for potentially
returning different types.

| Code     | Name           | Description                                                                          | 
|----------|----------------|-------------------------------------------------------------------------------------|
|       81 |      length(a) | Returns the length of a Byte Stream or String. For Byte Streams this must wait for the stream to terminate. |
|       82 |         abs(n) | Absolute value for Double. Nop for Integer. |
|       83 |        ceil(n) | Ceiling for Double, returns an Integer. Nop for Integer. |
|       84 |       floor(n) | Floor for Double, returns an Integer. Nop for Integer. |
|       85 |      max(n, m) | Maximum of two values of Double or Integer. |
|       86 |      min(n, m) | Minimum of two values of Double or Integer. |
|       87 |       round(n) | Round Double, returns an Integer. Nop for Integer. |
|       88 |      signum(n) | Signum of Double or Integer. Result is always a Double. |

### Statement

A statement has the format:
* Statement Code (Byte)
* Content

Specific statements described below.

#### Emit a Data Packet

Format as follows:
* 01 (Byte)
* Data Packet Id (variable length number)
* Tag Values (Value structures)
* Data Element Values (Value structures)

This format is the same as in Data Responses, with the difference that all Values contain
Expressions instead of constant values directly.

The Packet Id is the Index of the Data Packet definition in the Wiring.

#### Invoke Command

Format as follows:
* 02 (Byte)
* Device Id (variable length number)
* Command Id (variable length number)
* Parameter Values (Value structures)

Device Id is the Index of the device in the Access Table Entries.

Parameter Values all have Expressions instead of direct constant values.

### Security Considerations

Wiring contains access credentials, therefore should be stored with appropriate security.

### Deployment of a Wiring

A Wiring is not Device specific. Any piece of any Wiring can be located on any Device on the
network, provided it has the necessary access to other Devices needed for the Wiring to work.
A Wiring may be located on a Device that is neither the source of the Data
nor the target of any Commands described in the Wiring.

Wiring can be designed globally for the whole network, or per Device locally, depending on
the use-case.

Deploying a Wiring should involve planning network capabilities and usage. Each individual
transformation should be located either at the source or the target to minimize network
usage, where the source should be preferred.

### Processing Model

When a Wiring is already deployed on a Device, that Device must establish logical connections
to all Data sources, except when the source is the Device itself. When any data arrives, it must evaluate all the guard conditions
to every transformation defined and those entries that evaluate to true must be then
executed.

The runtime implementation is free to cache and simplify and optimize evaluations as
long as it results in the same values.

If there are any errors while evaluating a wiring script an error must be raised with the predefined
error data type.

## Appendix E: Communication Examples

This addendum shows "physical" TCP/IP payloads for certain selected / common use-cases.

Note for all examples: Payload bytes (except for MIC) are always encrypted with symmetric cipher, even though they are shown here as clear text.

### Data coming from a Light

Full state information coming from a two-state light that is either on or off. We assume the logical connection is already established:

Bytes:
* 18 (1 Byte): This is the frame for single-frame application messages, since our message will fit in 64K. Also, we assume that no other logical connections are
present, thus we don't have to include the sender or receiver address.
* 05 (1 Byte): The number of bytes following.
* 02 (1 Byte): Indicate that his is the "Data" package presumably as a response to an earlier "Stream Data" request by the initiator.
* 00 (1 Byte): Identifies the "Data Packet" this Light defined in the "Options" response.
* 00 (1 Byte): Identifies the data element in the packet.
* 01 (1 Byte): Length of data follows.
* 00 (1 Byte): Enum value of 0, indicating "off"
* MIC (16 Bytes): Message integrity code. Makes sure the message has not been tampered with. We assume the default Noise Protocol.

