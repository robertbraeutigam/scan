# SCAN, Simple Control and Acquisition Network

*Draft Version*

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
"server", nor any central software or hardware components.

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

* **Noise_KKpsk1_25519_AESGCM_SHA256**: This is a bi-directional protocol. 
The '*KK*' variant comes from the fact, that the frame already contains the 
public static key of both the sender and responder. So both static keys are
already *K*nown.
* **Noise_Kpsk1_25519_AESGCM_SHA256**: This is an uni-directional protocol,
suitable only for sending. After this message the device is free to send
payload messages immediately.

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

Every device must come with a unique PSK already set up for administrative (full) access.
It should be possible to reset the device to this "factory" PSK along with all other
configuration to allow for lost or forgotten PSKs. This "factory PSK" should be
changed before using in a production setting.

Note, that the handshake does not identify the PSK used explicitly. The responder
might therefore need to try multiple PSKs to know which one the initiator is using.
The protocol is designed so a single try takes a single hashing operation only. Still,
this mechanism is designed with a limited set of possible PSKs in mind.

This frame may contain an optional payload. If the handshake is complete after
the initial handshake message, the initiator is free to send the first payload
in the same message, if it fits into the remaining bytes for the frame.
This allows devices that do not maintain a connection,
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
may then contain the first message payload, provided it fits the remaining bytes of
this frame.

#### Frame type: 03 (Close Connection)

There can be more than one logical connection in a single TCP/IP connection. In this case
the party trying to terminate a logical connection must use this message to indicate
that the communication is considered terminated. This means the initiator will need
to begin again with an Initiate Handshake if it wants to re-establish the connection.

If the TCP/IP connection has only this one logical connection, then
that needs to be closed instead of sending this message.

This message may be sent by intermediaries between the initiator and responder. For example
a proxy might generate a Close Connection message if a TCP/IP connection to one party is lost
and the other one is still connected through a TCP/IP connection that has other active
connections.

This message has no payload.

#### Frame type: 04 (Application Message)

The actual payload of the application layer is described in the next chapters. This message
may be sent by both the initiator and responder.

Payload structure:
* Message Id (4 bytes, clear-aad)
* Payload (encrypted)

After a frame is sent, the sender is required to "rekey" its sending key. After a frame
is received, the receiver is required to "rekey" its receiving key. This means
frames are perfectly forward secure, as each frame is encrypted with a different key.

If any decryption errors occur, meaning that for some reason the sender and receiver becomes
out of sync, the connection must be closed.

All encryption happens with "nonce" of all zero.

The Message Id must be a uniformly increasing number. Senders must never repeat
the same Message Id in a session. If this can not be fulfilled, the connection must be closed.

The Receiver must ignore messages with a lower Message Id, than the already processed one. This
makes sure that if messages are repeated the Receiver will not try to decrypt messages for which
it doesn't have the keys anymore.

If a message is too large to fit
into one Application Message frame, it must be fragmented, with each fragment having the
same Message Id.

The last fragment of a message must have a payload that is less than the maximum size of the message.
Conversely, if the payload length (in the frame header) is 65535 (the maximum), there must be a
following fragment. If the last fragment's length is exactly 65535 by chance,
then a new fragment needs to be sent with 0 net payload, which is the length
of the decrypted application level payload. Note: 0 net payload will not result
in a 0 frame length.

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
through WiFi with DHCP or even random link-local addresses. For this reason a device should attempt an address resolution every time before
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

If the number of static keys in the response exceed 100, the responding device
must repeat this UDP reply message until all known static keys are sent.

#### Non-local Networks, NAT or Firewalled Networks

If one or more, or even all of the devices are scattered across different
IP networks, local address resolution will obviously not work. In case
any of these devices need to initiate a connection to another device,
they need to be configured with a static resolution table as 
described above.

It may be the case that devices behind firewalls or NATs will only be able
to initiate connections, but not receive them. This must be taken into account
when configuring the application layer later.

#### Announcement

Devices must always announce themselves when they become available on the local
network. They must send their static address with the UDP packet (type 02) as above.

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

### Requests

A request is a message sent from the Controller to the Controlled. Its format is as follows:
* Action (byte)
* Headers (a map, detailed below)
* Content

All requests contain an action (see below), some headers that describe an optional dynamic
parameter list and an optional content.

### Responses

A response is sent from the Controlled to the Controller, always as a response to a previous
request. There may be multiple responses to the same request, depending on the action
requested and other circumstances.

The format of the response is as follows:
* Reference Id (number, 4 bytes)
* Headers (a map, detailed below)
* Content

The Reference Id is the Message Id from the Network Layer that contained the Request.
There may be multiple Responses with the same Reference Id.

### Actions

#### OPTIONS

Represented by the byte value: 01.

Request the Controlled to supply meta-information about the device itself, including
what Controls it has, what Data it can provide, what Wiring it has currently configured.

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
send all relevant newest data and/or commands immediately upon the connection is established.

This applies also to the case if the device itself crashes and gets restarted. The device
must send the newest messages for all modalities, or measure/acquire it explicitly
again if those messages are no longer available.

As a side-effect messages are also repeatable. Since a stream of two messages with
the same content would also mean the same thing as one of those messages.

### Backpressure

Backpressure is the mechanism by which consumers of messages can tell producers to slow
down producing messages in the event that they can't consume them fast enough, or if
the network is saturated and can't handle more traffic.

In SCAN backpressure is done via the already built-in mechanisms of TCP. If a consumer
is not ready to process another message it will not empty the TCP/IP receive buffer,
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
or "Exactly Once". That is because the purpose of the network is not just getting data,
but also *controlling* devices based on the most current data available as fast as possible.

SCAN guarantees that *the most current data* is delivered *as fast as possible* at all times.

The first part of that guarantee is that at least the most current data for each modality *will*
be delivered eventually. That means if there is a new piece of data or there is a new command
it will not get lost, only possibly replaced by an even newer piece of data or command for that same modality.
This holds under all circumstances, even in the face of network errors and intermediaries having random errors.

This is easy to prove using the following observations:
* The device is only allowed to throw away "obsolete" messages, so the device will always eventually
try to send the most current message for each modality.
* If the device crashes or has other issues, it will restart and then it must start
with sending the current state again.
* Messages can not be silently dropped. 
The very first message that can't be delivered will cause the connection to eventually break either because of
TCP timeouts or because the encryption keys become out-of-sync. As the connection closes and is re-established
the device is again obligated to send the newest of all modalities.

The second part of the guarantee is that the newest data will be delivered as fast as possible. As fast
as the network and the receiver allows, because if any of those is slow the device will drop obsolete messages
in favor of new ones, which will both help solve the problem and reduce the time the most current data gets delivered to
a minimum.

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


