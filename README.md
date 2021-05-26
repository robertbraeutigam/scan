# SCAN, Simple Control and Acquisition Network

*Version 1.0, 2021-05-25*

The SCAN is an open, free, easy-to-use, extensible, DIY-friendly
way to collect data and control devices on a network.

## Goals

These are the main considerations driving the design of this protocol:

- **Security first**, but only to an extent dictated by proper threat modeling.
- **Minimal effort** implementations. The effort to implement compatible devices
  should be linear to the extent of features the device will support. I.e.
  simple devices (like a Light) should be almost no effort to implement, while
  more complicated devices may require more thorough analysis and design.
- **Driven by individuals**, not industry. I.e. does not have to support complicated
  use-cases which are almost never used, or used only because of historical reasons.
- **DIY** friendly. No proprietary components, processes or certification requirements.
  A private individual should be able to create a compatible device easily and completely
  independently.
- **Transparent**. It should be very easy to discover which devices need what inputs and react or control
  what other devices. Not out-of-band, like through documentation, but through the actual 
  protocol itself.
- Does **not require** a complete and **perfect list of codes**, nor a perfect usage on the part
  of the devices to be *fully* usable.

These other design decisions were also used:

- Minimal setup required. Having only two devices should already work, without any
  additional components.
- Should work over the internet and in any network topology, trusted or not.

## Solution Overview

SCAN is defined on top of the Internet Protocol (IP), so off-the-shelf internet networking tools 
and devices, such as routers, repeaters, cables, WiFi or even VPNs,
can be used to build the physical network.
There is no requirement on the physical network topology, nor on any particular
network service to be available, as long as individual devices can communicate with each other.

All communication is peer-to-peer and end-to-end encrypted with automatically
rotating keys. This means there is no central component or server, and all
communication is secured against third parties even through untrusted infrastructure.

All devices have a uniform interface, which mainly consist of these things:

* The specification of Data they produce
* The specification of Controls they offer
* A uniform wiring interface

All data is defined as a time-series. That is, each data point has a time
when it was produced, and all the related data elements produced at that instant.
Data elements are not defined in terms of technology (like 32bit or 64bit numbers),
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

Of course auto-wiring, the process of automatically connecting data to controls,
can be achieved using standardized semantics of data, if applicable. Such as 
auto-connecting a Plotter to a GPS Receiver.

## Network Protocol

The network protocol is build on top of TCP/IP, using a peer-to-peer logical topology. That is,
each device is free to directly communicate with any number of other devices. There is no
"master" or "server" software or hardware component.

Since no specific network topology is required, the network protocol has to support every possible
topology. For this reason it contains additional logical routing information and is designed
to be able to be multiplexed, forwarded and proxied. 

The network protocol is packet based, with each packet limited in size to a maximum of 65535 bytes
excluding the frame header. If there is a payload of more than that it needs to be handled as
described by the message type.

If any parties to a communication encounter any errors in the protocol or interpretation of messages
they must immediately close the connection without any further messages, unless otherwise
instructed by specific messages. The initiating party must
not retry opening connections more often than 60 times / minute, but may implemenent any heuristics
to distribute those reconnects inside the minute.

### Types

All number types, if not stated otherwise, are network byte order (big-endian, most significant byte
first) and are unsigned.

An array of any type is a concatenation of values of the given type. The length is not explicitly
supplied and should be available from either the message overall length of some other means.

A string is a length (2 bytes) followed by a byte array that contains UTF-8 encoded bytes of characters.

### Frame Header

All packets have the following header:

* Destination peer (32 bytes) (clear)
* Frame type (1 byte) (clear)
* Length of payload (2 bytes) (clear)

The destination is the public identity key of the target device. Note: this device may not
be a party to this communication. Devices may communicate with proxies, gateways or other
infrastructure components without their explicit knowledge. The sending device can assume
that the receiving device is either the destination itself, or can route the message
to the destination device.

The frame type describes the payload that follows the header. Devices must ignore messages
with unknown frame type.

### Frame types

#### Frame type: 01 (Initiate Handshake)

Sent from the initiator of the connection immediately upon establishing the
TCP/IP connection. It transmits th first handshake message together with the
Noise Protocol Name.

Payload structure:
* Noise Protocol Name (string) (clear-prologue)
* Handshake (byte array)

The Noise Protocol Name is the exact protocol used for the following handshake
and data exchange. If the recepient disagrees with the protocol it must close the
connection.

The only protocol supported at this time is: **Noise_IKpsk2_25519_AESGCM_SHA256**.

Since the sender obviously has to know the public static key of the recipient
(it is in the frame header for routing purposes) the second character is
always '*K*'. Also, to be able to answer the recipient has to receive the
public static key of the sender immediately, hence the '*I*' first character.

For the recipient to "authenticate" the sender, that is, to evaluate whether
the two are allowed to talk, there may be multiple possible
strategies. White listing allowed devices is inpractical in a large installations.
Instead this specifiaction requires a shared key (PSK) in all devices that belong to the
same logical "network".

During the on-boarding of a device, it must be given the shared key for the
whole network for it to be able to communicate. See relevant chapter for
more details.

The protocol name also has to be included in the *prologue* of the Noise Handshake to
make sure it has not been tampered with.

#### Frame type: 02 (Continue Handshake)

Sent potentially by both parties. If continues the handshake after it has been initiated.
The first continue handshake must come from the responder, then from the initiator
and continue in turn until the connection is established.

Payload structure:
* Handshake (byte array)

Neither parties may send any other frame types until the handshake is concluded. Both
parties may assume implicitly that a Continue Handshake frame will come next if the
handshake is not yet finished.

### Message Choreography

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

