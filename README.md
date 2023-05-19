# SCAN, Simple Control and Acquisition Network

*Draft Version*

SCAN is an open, free, easy-to-use, extensible, DIY-friendly
way to collect data and control devices on a network.

## Functional Goals

The protocol is specifically created and tested to support a wide-range of possible use-cases:

- *Home automation use-cases*. Having a wide array of sensors, devices and controls controlling each other,
  possibly including everything from doors, windows, lights to audio or video feeds.
- *Automotive*. Controlling actuators, servos with feedback in a closed system.
- *Marine*. Controlling all functions of a boat, including gps, plotters, lights, engines, video surveillance in one
  standardized yet extensible, 3rd party friendly way.

*Problem statement*: All of these do have "standardized" protocols already, but these are not DIY friendly. They
are not open, require codes or ids that need to be agreed upon by all parties, they are not secure, some require
third parties such as central servers to be in the loop, they do not interact with each other and despite standardization
even devices using the same protocol can't sometimes interact if they are from different vendors with no way for the end-user
to correct this.

## Non-Functional Goals

The main considerations driving the design of this protocol:

- **Security first**! State-of-the-art end-to-end encryption, perfect forward secrecy, no hardcoded defaults,
  per-device distributed authorization, etc., without complex certificate management, central servers,
  authority nor any third-party involvement.
- **Minimal effort** implementations. The effort to implement compatible devices
  should be linear to the extent of features the device will support. I.e.
  simple devices (like a Light source or a Button) should be almost no effort to implement, while
  more complicated devices may require more thorough analysis and design.
- **Driven by individuals**, not industry. I.e. does not have to support complicated
  use-cases which are almost never used, or used only because of historical reasons.
- **DIY** friendly. No proprietary components, processes or certification requirements. No central registries.
  A private individual should be able to easily create a compatible device completely
  independently, which can then be used by any end-user to its full extent and be combined with any
  other device that has or needs similar enough features.
- **Discoverability**. It should be very easy to *discover* which devices need what inputs and react to-, or control
  what other devices. Not out-of-band, for example through documentation, but through dynamic discovery
  through the protocol itself.
- Does **not require** a complete and **perfect list of codes** nor a **complete dictionary** of
  some semantic identifiers, nor does it require everybody to agree on a single unified set of these,
  nor a perfect usage on the part of the devices to be *fully* usable and for all devices to be
  compatible with each other.
- Prevent proprietary extensions and the need to debug or reverse-engineer devices, as far as possible,
  by using transparent **dynamic wiring**, instead of devices directly hardcoded for each other or for specific codes.
- **Minimal setup** required by end-users, ideally plug-and-play in most cases to a *fully-secure* production installation.
- Should work **over the internet** and in any network topology, trusted or not.

## Out-of-scope

This protocol does not replace radio-based protocols such as Bluetooth, Bluetooth Low Energy, LoRaWAN, etc.

Although devices using these protocols may be connected through some gateway into a SCAN network, they can not
directly participate as these protocol have their own definitions of security, data excmhange and semantics, which are
incompatible with SCAN. Alternatively, embedding SCAN traffic in any of these would not be an optimal use of these
protocols.

## Solution Overview

SCAN is divided into three layers:

- Physical Layer (Packet Communication and Announcement)
- Network Layer (Security, Multiplexing, Fragmenting, Logical Connections, Messaging)
- Application Layer (Data, Commands, Wiring)

The Physical Layer is the actual transport infrastructure on top of IP that facilitates the transport of single
packets between devices and enables announcements. It supports different IP topologies, including
local networks, connections over gateways, etc.

The Network Layer is responsible for providing a secure, flat, multiplexing and mixing capable layer
for communications between devices. Devices are identified, addressed based on static cryptographic keys instead
of hardware addresses and communicate point to point using a packet-based protocol. 

The Application Layer adds a uniform quasi-request-response based interface, which mainly consist of these categories:

* Data emitted by devices. These are changes in state of the device.
* Controls. Invocations of these can change the state of the device.
* Wiring. Dynamic description of the relationships between data and controls.

SCAN is designed to work in a distributed fashion. All devices are potentially
data acquisition or control devices or both, with only two devices already capable
of working together without any dedicated control device or server. Introducing
more components does not require any central component to exist either, although the
option is available if needed.

All data is defined as a time-series. Each data point has an associated timestamp
and all related data elements. Data elements do not
just carry a technical format (such as how many bytes, etc.), but also meaning, whether it is an
Identification, Event or Measurement, etc.

Measurements carry unit information (like Liter, PSI, %, etc.) in addition to a name. This
way data elements don't exclusively rely on their "standardized" semantics, and can be uniformly
stored, visualized or processed even without the exact meaning they might carry.

Controls are a way to influence the device in some way. That can range from toggling a light
to sending a remote firmware update. All the controls are listed through the uniform interface,
so they are discoverable on the fly.

Wiring in SCAN is the process of connecting data to controls. 
For example connecting the data from a switch to the on/off
control of a light source. Because the *kind* of both the data and control are known, the connection
can be made even if the exact semantics of either side is unknown or not defined.

Auto-wiring, the process of automatically connecting data to controls,
can be achieved using standardized semantics of data, if applicable. Such as 
auto-connecting a Plotter to a GPS Receiver.

## Physical Layer

The physical layer supports basic network primitives based on IP-native means for the next layer.
These functionalities are:

* Open and receive a physical TCP/IP connection to/from a peer to send and receive data.
* Send and receive data to / from all connected devices.

This layer mimics IP closely. Meaning connections support streaming-based data exchange
with no packet demarcations, while communication with the whole network supports stateless
packet based communication.

Addressing uses native IP addresses.

There can be multiple ways of configuring the Physical Layer of a SCAN device. However
this configuration should be completely transparent for the layers above.

Devices must reuse TCP connections in each direction, therefore
at most two TCP connection must be present between two given peers at all times, one for each
direction.

### Local Network Configuration

Every device must be capable of operating in a local network, where other devices are directly
addressable and all devices can be contacted by multicast packets. In this scenario:

* Connections are made / received using TCP/IP on port 11372.
* All devices are addressed over UDP, at the address 239.255.255.244:11372.

Note, that this "local network" does not necessarily need to be a physically local network,
it can be a virtually local network that connects multiple devices, possibly through VPNs.

All UDP packets sent, regardless of content must be repeated 5 times with random intervals
in order: 0-100ms, 0-400ms, 0-500ms, 1-2 seconds, 1-2 seconds, having a maximum total time of 5 seconds.
If the device receives a complete answer for a packet earlier, such as a complete list of
IP addresses for queried SCAN addresses, it may stop repeating the packet.

### Gateway-based Configuration

Devices may support connecting through "Gateways". A "Gateway" is a SCAN "Physical Layer" level software or hardware
device that does not necessarily have an "Application Layer" presence, i.e. it may be invisible
to the SCAN network, but can present all the devices that connect to it.

In this scenario the software stack on the device that connects through a Gateway maps Physical Layer operations thusly:

* Connections are always made with the Gateway on port 11372 (the same SCAN port as above).
* All devices are addressed over TCP through the same connection as above.

In this case all communication is directed at the Gateway, which in turn reflects all information 
present on the "other side" of the Gateway.

## Network Layer

The network protocol is designed to be a "layer" on top of the Physical one. It is independent and
ignorant of the "application layer" protocol defined in the next chapters.

The main purpose and design goals of this layer are the following:
* Provide **security** features, such as authentication, authorization and anti-tempering features.
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

The network protocol is packet based, with each packet limited in size to a maximum of 65535 bytes
excluding the frame header.

A logical connection is a connection between two devices identified by their public static keys. All
devices have a static key pair, the public part of which identifies the device uniquely and securely
on the network. There 
can be at most two logical connection between any two devices, because the ordered pair of public static keys uniquely identifies
a logical connection. Note however, that one physical connection can tunnel more than one logical connection.

If any parties to a communication encounter any errors in the protocol or interpretation of messages
they must immediately close the logical connection. If the logical connection is the only one in
the physical connection, that needs to be closed instead. If not, a close message
needs to be sent.

The initiating party must not retry opening connections more often than 60 times / minute, but may implement any heuristics
to distribute those reconnects inside the minute.

### Data Types

All number types, if not stated otherwise, are network byte order (big-endian, most significant byte
first) and are unsigned.

A byte array is a concatenation of bytes. The length is not explicitly
supplied but should be available from either the message overall length or by some other means.

A string is a length (2 bytes) followed by a byte array that contains UTF-8 encoded bytes of characters.

A variable length integer is a number stored as a variable number of bytes, at most 8, in big-endian
ordering. On each byte except the last (the 8th byte) the highest bit indicates that a byte still follows.

### Frame Header

All packets have the following header:

* Header (1 byte) (clear-frame type is aad)
* (optional) Source peer (32 bytes) (clear)
* (optional) Destination peer (32 bytes) (clear)
* Number of following bytes (2 bytes) (clear)

The header describes what this frame means and certain format parameters. It is organized as follows:
* Bit 0-5: Frame type (see next chapters)
* Bit 6: Whether the source peer is given
* Bit 7: Whether the destination peer is given

The source is the sending peer's public identity key. The destination is the public identity key of the target device. 

If both the source and destination are unique in a given TCP connection, meaning that the TCP
connection only carries this single logical connection, there is no need to
continuously send peer identifications. In this case both identifiers can be omitted.

This is also true for cases when either one of the identifications is superfluous. Devices need
to track logical connections in TCP connections and know when this is the case. This information
is therefore essentially redundant, but may help some implementations.

The receiving device of a frame may ignore superfluous identifiers without further validation.

Since a single logical connection may traverse multiple TCP connections, when routed through
proxies or gateways, the presence of peer identifications may be added or removed as needed
by intermediaries.

### Frame types

Devices must ignore frame types they do not support. Ignoring a frame means to skip the given amount of
bytes in the stream.

Frame types 0-31 may only be sent on TCP connections, while frames 32-63 may be sent
and received on both a TCP connection and on the UDP broadcast port.

After the handshake is completed all frames between 0-31 must cause the sender and receiver
to rotate the appropriate keys. That applies even if the frame did not contain
a payload and even if the frame is unknown and is being skipped.

#### Frame type: 01 (Initiate Handshake)

Sent from the initiator of the connection to establish a logical connection.
If a TCP connection does not exist yet, the initiator must open one first.
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

Both devices should however also do *authorization*, that is, check
what the other device is allowed to do. Devices are free to implement
any allow-, or deny-listing based on the public static key, or implement
other restrictions based on either the public static key, time of day
or any other information gained during communications.

All handshakes contain a PSK (Private Shared Key).

A PSK works as a "role" during authorization. Since the responder may assign
privileges to certain PSKs, the PSK presented by the initiator categorizes
it to have those privileges. PSKs can be potentially published to multiple devices,
effectively creating a role or group of devices.

Every device must come with a unique PSK already set up for its initial or following enrollments.
This permanent factory PSK must not be allowed to be used for anything else than setting up a
custom administrative (full access) PSK, when the device is being enrolled by the end-user.
This is a command each device must support. During this process however, the device
should reset all state to factory defaults and purge all information potentially
stored on the device. This way the protocol guarantees, that the device will always
generate a previously unknown PSK for usage, but still allow the user to recover / re-enroll
the device in case that gets lost, or the device itself changes hands, without potentially
leaking previous data.

Note, that the handshake does not identify the PSK used explicitly. The responder
might therefore need to try multiple PSKs to know which one the initiator is using.
The protocol is designed so a single try takes a single hashing operation only. Still,
this mechanism is designed with a limited set of possible PSKs in mind.

Both the sender and destination identifier must be present in this frame.

#### Frame type: 02 (Continue Handshake)

Sent potentially by both parties. It continues the handshake after it has been initiated.
The first continue handshake must come from the responder, then from the initiator
and continue in turn until the connection is established.

Payload structure:
* Handshake (byte array)

#### Frame type: 03 (Close Connection)

There can be more than one logical connection in a single TCP connection. In this case
the party trying to terminate a logical connection must use this message to indicate
that the communication is considered terminated. 

If the TCP connection has only this one logical connection, then
that must be closed instead of sending this message.

This message may be sent by intermediaries between the initiator and responder. For example
a proxy might generate a Close Connection message if a TCP connection to one party is lost
and the other one is still connected through a TCP connection that has other active
connections.

This message has no payload.

#### Frame type: 04 (Application Message Intermediate Frame)

A part of an application message, including the initial frame, but not the last frame. This frame indicates
that the message is not complete, additional frames will follow for this message.

The actual payload of the application layer is described in the next chapters. This message
may be sent by both the initiator and responder.

Payload structure:
* Message Id (variable length integer, clear-aad)
* Payload (encrypted)

If any decryption errors occur, meaning that for some reason the sender and receiver becomes
out of sync, messages were omitted or repeated for example, the connection must be closed.

All encryption happens with "nonce" of all zero.

If a message is too large to fit
into one Application Message frame, it must be fragmented, with each fragment having the
same Message Id. A sender may also choose to fragment messages for other reasons, for example
to get video frames that are already available quicker to the receiver.

The Message Id identifies this message and all frames it consists of. Message Ids should
be re-used to be able to keep the Id low and in one byte. All values for which
a Last Frame has been sent should be considered re-usable.

#### Frame type: 05 (Application Message Last Frame)

The last frame of an application message. This frame may also be potentially the first and only
frame the message has, although in this case the Single Frame Application Message should be preferred.
It indicates that the application message identified by Message Id is complete with this payload.

Payload structure:
* Message Id (variable length integer, clear-aad)
* Payload (encrypted)

Encryption and key management is the same as for intermediate frames.

The Message Id used in this frame should be considered reusable after this frame is sent.

#### Frame type: 06 (Single Frame Application Message)

An application message that fits a single frame.

Payload structure:
* Payload (encrypted)

Encryption and key management is the same as for intermediate frames.

#### Frame type: 07 (Renegotiate)

The responder may send this message to instruct the initiator to reopen the connection with
a new handshake.

There is no payload in this message.

When a connection is closed or lost, upon re-establishing the initiator may
continue to send frames with the already established keys for the previously lost logical connection.
That is, it may continue sending application messages instead of starting with a handshake again.

Devices may support remembering already established keys, or may even persist them to survive
restarts. Therefore an initiator may try to continue communication with previously established keys.

If this assumption does not hold, the responder must send a Renegotiate frame to indicate that
it can not in fact decrypt the received messages, either because it does not remember the necessary
keys or it became out of sync with the initiator and a new handshaking process is needed.

The initiator should assume that the messages it sent in the meantime were not received
and must remove its old keys and close the connection.

#### Frame type: 33 (Identity Query)

This frame is used to establish the IP addresses belonging to static identity keys,
or to find out what devices are on the network.

Payload structure:
* Query Id (4 bytes) // TODO: Why 4 bytes? 1 should be enough, since it only must be remembered for 10 seconds
* Target query static keys... (32 bytes each)

The query can contain any number of target addresses between 0 and 100, for which the
IP address is to be returned. 

The query Id is a strictly increasing number for each query. Devices should remember
the last query Id for each host for 10 seconds and not respond if they already done so.

This frame may get sent over UDP or TCP. In case the sender is set up to use a gateway,
and presumably is not on the same administrative network as other devices, it may send
the query through TCP directly to the gateway. Otherwise it is a broadcast UDP frame.

If the frame is sent as a broadcast UDP message, all
devices with the listed keys must respond to this query. If it is sent to a
gateway, the gateway must respond.

A query with 0 target keys is a wildcard query, which means *all* devices in the local network
must respond if sent as a broadcast frame. If sent to a gateway, the gateway must respond with
all the identity keys that are reachable through the gateway.

Note that wildcard queries may or may not return all devices depending on 
online/offline status, or network topology, timeouts or network congestion.

#### Frame type: 34 (Identity Announcement)

Announces the identity or identities represented by a device.

Payload structure:
* Static keys... (32 bytes each)

This reply tells the requester that these static keys are reachable at
the address this frame is from. A device, such as a gateway,
may represent multiple devices on the local network, that is why
multiple static keys may reside at the same IP address.

Devices must always announce themselves when they become available.
The announcement is sent according to network configuration either as
a UDP broadcast or TCP connection to gateways.

Devices must answer Identity Queries through a TCP connection. If there is already
a TCP connection to the device requesting, then that connection must be used. Otherwise
a TCP connection needs to be established first. This connection must be closed after
the reply is complete, if it is otherwise unused.

Devices should remember the last query Id answered for 10 seconds, if received through either UDP or TCP. This is
because it is likely the query will be received multiple times, but should be answered only once.

### Message Choreography

There can be only at most two logical connection
between any two parties. If a logical connection already exists, that must be used.
If not, a new logical connection needs to be established. If there is already a TCP
connection between the source and the target, that TCP connection must be used. If not, a new TCP
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

### Network Configuration

Devices are expected to be available through a variety of network topologies and configurations,
including through static or non-static IP addresses, through WiFi, with or without DHCP, through VPN,
or through multiple network segments each with its own network zones or firewalls.

Devices therefore must support low level network configuration options to enable them to participate
in the SCAN network. These must at least include the following options:
* Direct connection to SCAN network. Discovery and address resolution through broadcast UDP.
* Connection through a gateway or gateways. Discovery and address resolution through gateway directly.

Gateways present a way to configure a static set of IP addresses to speak to.
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

Devices may support other methods to connect to a SCAN network, like VPN, Proxies, or other custom tunnelling
methods.

At the end of the network configuration devices must be able to send and receive frames to and from
the rest of the SCAN network.

### Address Resolution

To be able to establish a "physical" TCP connection between parties having static keys,
there must be a way to query the network what actual IP address is associated with
a given static key. This mechanism is not unlike what IP does to query the network
for a MAC address for a given IP.

Device must attempt an address resolution every time before
establishing a TCP connection. The device may use cached values of already resolved IP
addresses, it must however fall back on a resolution process if connection can not be established
with cached value.

An Address Resolution is sending an Identity Query based on the configured network either through
UDP broadcast or through TCP directly to a gateway and processing the Identity Announcement
that comes back.

Devices may continue to monitor unsolicited Identity Announcements to build an internal cache
of IP addresses.

If an IP address can not be found for a given identity key, the connection can not be established.
Devices may choose display this to the user if capable, or may send specific error events through
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

A relative timestamp is a strictly increasing, millisecond precision integer from a 
context-given reference point. It is stored as a variable length integer.

Note, that the relative timestamp is a true measure of time passed, so unix timestamps
or any derived values are *not* usable, because they may act wierd around leap seconds.
Prefer raw system clock counts or raw millis counted from system start.

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

The Maximum Rate specifies the rate at which the data should be sent in milliseconds. 0 milliseconds
means as fast as possible, without any waiting. The Controlled Device must honor this rate
in every case, even if the data is generated by manual input. It must never send data more
frequently than specified. It may however send data less frequently.

The Controller may repeat this message if the maximum rate changes for any reason.

Note that because of the Resolution Principle the Controlled device must immediately
send the newest data that is available upon receiving this request.

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
* Data Time (Relative Timestamp)
* Tag Values (Value structures for the defined Tags)
* Data Element Values (Value structures for the defined Data Elements)

The Data Packet Id identifies the Data Definition that describes the meaning of the
values submitted here.

The Data Time is measured from the last received data packet of same id. The first
transmitted value must be 0.

Note that Devices must send all Data in order for a given Data Packet.
There can not be any out-of-order times, but each Packet may advance this
time in its own context. For example a Data Packet for year-end summary data
may only advance once a year and send the same Data for the whole year.

Note also, that because of the Resolution Principle the Device must immediately
send the newest data value upon receiving a Data Request. This may involve taking
an immediate measurement, or may involve sending a cached value from memory.

#### INVOKE RESPONSE (03)

Indicate the rate the given command can be invoked.

Request content:
* Command Id (variable length integer)
* Maximum Rate (variable length integer)

Same as the maximum rate specified in STREAM DATA message. The Controlled indicates at which rate this
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
are available at the right time.

SCAN networks can form a complex graph of devices communicating, issuing data and commands to each other.
Rate Limiting is the way SCAN ensures that throughout all processing chains data and commands
are generated at approximately the same rate as they are consumed throughout the whole chain.

Note that network backpressure alone does not lead to an ideal producer-consumer synchronization. 
Network backpressure is not specific to a single command or data, but may influence all the
communication on the network. It would be difficult to isolate which communication channel, if any,
caused network congestion. This uncertainty and imprecision may cause various communication artifacts,
including network over-use, messages coming in batches or waves, or even not finding a steady state.

Rate Limiting is an explicit measure based on application-level feedback to senders from receivers. All
receivers must define the maximum rate at which they can consume messages. This is for both data and
command invocations. This measure is trivially independent of network problems, therefore can work
reliably even in the face of network backpressure events.

A steady state is easily reached as producers will automatically approximate the consumer's rate at all times
and not fluctuate trying to dynamically match the consumer with some algorithm.

It also does not require constant adjustment, therefore does not require regular feedback messages.
The consumer will explicitly communicate what the best-case steady state looks like once. This
maximum rate is independent of the network and transient congestion events, therefore does not need
to be adjusted.

Device implementers should generally not concern themselves with timing,
resolution, measuring intervals or similar issues. Devices should define *how* and *what*
to measure and then let the SCAN network take care of proper timing and intervals adaptively.

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
each tier is using the tier below to define itself. The lower tiers are more stable, but also
more technical in nature, while upper tiers are richer in semantics, but more volatile.

The purpose of this tiered system is for devices to define as much meaning as possible for each
data element or command parameter. Even if the corresponding meaning is not found on a given
level, there may be an appropriate lower level meaning to be found.

### Tier 0: Value Types

Value Types are the actual values in data messages or actual parameter values for invoking
a command.

These are as follows:

| Name                | Code    |Value Format            | Comment                                          |
|---------------------|---------|------------------------|--------------------------------------------------|
| Byte Stream         |       1 |Max Size (variable length integer), Bytes | Potentially "unlimited" stream of bytes. The size given is the maximum size this value might be. It is not an error for the context to override this value, for example by ending the message. "Unlimited" streams, or streams where the size is not known may therefore safely use the maximum value for size. |
| Integer             |       2 |Variable Length Integer | Unsigned                                         |
| Double              |       4 |Double                  |                                                  |
| String              |       3 |String                  |                                                  |

All Tier 0 types must be known to an implementation in order to properly interpret definitions and values.
This table will not be extended any further.

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
| Arbitrary String    |       6 | Empty       | String         | Arbitrary, unlimited value set string. | Current status localized string. Manufacturer name. |
| Unix Timestamp      |       7 | Empty       | Integer     | Unix timestamp with millisecond precision. | Effective date, Billing date. Last update time. |

The "Definition" column specifies what parameters this type requires for the definition in a Data Packet.

Note, this table might be extended by subsequent iterations of this document. Devices may may
ignore types that they don't know. Note however that type codes can not change, therefore 
type codes can be compared even if the exact definition is not known.

### Tier 2: Usage Types

Types that are usage specific. It is *not necessary* for a device to use
usage types, all types can be ad-hoc defined.

Devices should however use usage types wherever they can to make *wiring*
easier for an operator.

| Name             | Code    | Tier 1           | Description                                      |
|------------------|---------|------------------|-------------------------------------------------|
| None             |       0 | Any              | No defined usage type applies to the data at hand. |
| On-Off           |       1 | Enum 0=Off, 1=On | Indicates an operational status of either on or off. |
| Latitude         |       2 | Measurement, deg | |
| Longitude        |       3 | Measurement, deg | |

Note, this table might be extended by subsequent iterations of this document. Devices may may
ignore types that they don't know. Note however that type codes can not change, therefore 
type codes can be compared even if the exact definition is not known.

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

Note, this table might be extended by subsequent iterations of this document. Devices may may
ignore types that they don't know. Note however that type codes can not change, therefore 
type codes can be compared even if the exact definition is not known.

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
state of modality. For example including the change of temperature to the current temperature data packet.
Or including/defining the timestamp when a state became current.

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

