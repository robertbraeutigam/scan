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

The Internet Layer is a thin wrapper over IP. It exposes two primitives to the layer above:

* Open or accept a TCP connection to/from another physical peer, providing a stream of
  bytes in each direction. Frame boundaries on that stream are defined in the Logical
  Layer, not by IP packet boundaries.
* Send or receive UDP multicast datagrams to/from all physical peers on the local segment,
  used only for stateless segment-wide messages (advertisements and advertisement
  requests). Each datagram carries exactly one Logical-Layer frame.

### Terminology

Two notions of "peer" appear in this specification. At the Internet Layer a *physical peer*
is an IP address (and TCP port) at which a SCAN stack is reachable. At the Logical Layer and
above a *logical peer* is a 32-byte static public key (`PeerAddress`). A single physical peer
may represent multiple logical peers (the gateway case), and a single logical peer may be
reachable through multiple physical peers (a multi-homed device). Unless otherwise qualified,
"peer" in this section means physical peer.

### Addressing

Addressing uses native IP addresses. Both IPv4 and IPv6 are supported; implementations must
support at least one family and should support both where the environment permits.

A device opens TCP only to a peer whose `(IP, port)` it has learned, either from a
recent `Advertisement` or from its persistent address cache. A device sends
`AdvertisementRequest` frames (see Logical Layer) for the peers it needs an address
for. Cache sizing and entry lifetime are implementation-defined; the protocol does
not mandate either. Implementations should be prepared for networks with many peers
and may choose to cache only the peers the device actually needs to reach (for
example, those referenced by its configured wiring). Gateways are the exception: a
gateway's address is configured out-of-band, so no prior advertisement is required to
open TCP to it.

The mapping from a logical peer to its `(IP, port)` comes from `Advertisement`
source addresses combined with the `port` field of the frame. A receiver maintains
an ordered cache of recent `(IP, port)` entries per logical peer, head-first by
most-recent-advertisement, bounded to a small implementation-defined cap (at least
2). Each cache entry is tagged with the `generation` and emitter of the advertisement
that installed or last refreshed it. A new advertisement from the same emitter that
carries an already-cached `(IP, port)` refreshes that entry's generation and moves it
to the head; a previously unknown `(IP, port)` is inserted at the head; the tail is
evicted when the cap is exceeded. This allows a multi-homed peer (for example one
reachable over both wired Ethernet and WiFi) to be cached under several concurrent
addresses rather than overwriting one with the next.

The `generation` counter is reset at reboots. When a smaller counter value
is received than the newest known, the whole cache for that peer must be evicted, so
that it's list remains ordered by the counter descending.

Entries are evicted independently of the cap when they become obsolete. Since a
device advertises on every active interface in every generation, an entry that is
absent from two or more consecutive generations from its emitter is no longer
reachable through that path and is removed. A cache is permitted to retain an entry
across a single missed generation to tolerate one lost multicast packet. A later
IPv4 advertisement therefore does not replace a previously recorded IPv6 address
merely because it arrived last: both coexist until one of them ages out by
generation.

When a device needs to (re)establish a TCP connection to a peer — on first use,
after an unexpected close, or after cryptographic teardown — it walks the cache head
first, attempting each `(IP, port)` once. The first attempt is made immediately;
subsequent in-cycle attempts are not throttled. If every cached entry fails within a
cycle, the device enters exponential backoff: 1 s, 2 s, 4 s, … up to a 60 s ceiling,
with ±25% random jitter on each delay, per logical peer or per configured gateway.
Each backoff cycle again walks the cache head first; advertisements received during
backoff update the cache (installing, refreshing, or evicting entries as above), so
the next cycle uses the current list state. The backoff resets after a successful
TCP establishment followed by a completed Noise handshake.

Only the initiator of a logical connection may close its TCP intentionally to
indicate offline status; after such an intentional close the initiator may not
immediately reconnect. A responder does not close TCP to go offline — it relies
on the initiator's own disconnect or on network-level failure to release state.
A responder under resource pressure may still close TCP to shed load; this is
not "going offline", and the initiator reconnects under the normal backoff.

For non-link-local source addresses, devices track only
the IP that advertised the peer, not the interface on which it was received; the
device's own routing table is assumed to pick a sensible interface for that IP. For
link-local source addresses — `169.254.0.0/16` (IPv4, RFC 3927) and `fe80::/10`
(IPv6) — devices additionally record the receiving interface and use that same
interface for any outbound TCP to the peer (in IPv6 terms, the zoned form
`fe80::addr%if`; IPv4 passes the interface out-of-band to the socket API).
Link-local addresses are interface-scoped by construction — the same address can
legitimately refer to different hosts on different interfaces — so the interface
is part of the identity of the address. If the same source IP is observed on more
than one local interface (e.g. bridged segments), implementations may treat the
most recent observation as canonical and replace any prior interface association;
SCAN does not attempt to disambiguate at L2.

Devices should reuse an existing TCP connection between two physical peers rather than
opening a second one; multiple logical connections multiplex over that single TCP (see
Logical Layer). In the rare case both sides initiate concurrently, producing two TCPs
between the same pair, each logical connection stays on the TCP its initiator opened.
Both TCPs are permitted to live out their natural lifetime; the only cost is wasted
resources.

### Local Network Configuration

Every device must be capable of operating in a local network, where other devices are
directly addressable and all devices can be contacted by multicast packets. In this scenario:

* The TCP listening port is chosen by the device. The default is 11372 where that
  port is available. The actual port is announced to peers in every `Advertisement`
  and `AdvertisementRequest` frame the device emits. Devices should keep the chosen
  port stable across reboots so that cached `(IP, port)` entries in other devices
  remain usable until the next advertisement propagates; a device that cannot honor
  a previously-used port (for example, the port is taken by another process) picks
  a new one and relies on its birth-burst advertisement to restore reachability.
* Source ports for outgoing TCP connections are ephemeral.
* All devices are addressed over UDP on the fixed port 11372, at multicast group:
  * `239.255.255.244` for IPv4, from the IPv4 administratively-scoped block
    (`239.0.0.0/8`), restricted to the local L2 segment by TTL=1.
  * `ff12::2c6c` for IPv6 (transient, link-local scope; flags = `1` (transient),
    scope = `2` (link-local)). The transient flag reflects that this address is
    not yet IANA-registered.
* Multicast datagrams must be sent with IPv4 TTL 1 and IPv6 hop-limit 1. SCAN discovery
  is scoped to the directly-attached L2 segment by design; routed multicast is out of
  scope, and cross-segment reachability is provided by gateways instead.
* IPv4 senders should set the Don't-Fragment bit on advertisement datagrams so that an
  oversized frame fails visibly rather than fragmenting silently.
* IPv6 senders must not include a Fragment Header on advertisement datagrams.
  Advertisement frames are sized to fit within the IPv6 minimum MTU (1280 bytes) by
  design, so end-host fragmentation is unnecessary.
* Devices must join the relevant multicast group via IGMPv2 or v3 on IPv4 and MLDv1 or
  v2 on IPv6 on every interface over which they participate. Switches should enable
  IGMP/MLD snooping to suppress flooding, but the protocol does not require it.
* Multicast loopback (`IP_MULTICAST_LOOP=0` or equivalent) may be disabled as an
  optimization; a device may in any case ignore its own advertisements, as they carry
  no useful information.

The `Advertisement` frame (see Logical Layer) carries up to 16 `PeerAddress` entries.
Devices representing more than 16 logical identities send multiple frames.
Advertisements are emitted only on events (birth burst, change re-burst, solicited
reply); see the Logical Layer's `Advertisement` and `Heartbeat` sections for the
cadence and liveness model.

Note that this "local network" does not need to be a physical L2 segment; an
L2-bridged virtual network (e.g. a layer-2 VPN, a virtual switch, a Wi-Fi mesh
operating as a single bridged segment) counts as a single segment and works without
further configuration. Routed networks, including routed VPNs and overlay networks,
do not propagate SCAN multicast — TTL=1 keeps the datagrams on the originating L2
segment by construction. Reach across routed boundaries is provided by configuring a
gateway, not by extending the multicast domain.

**Privacy and integrity note.** Multicast advertisements carry 32-byte static public keys
in the clear. Any device on the same broadcast segment can enumerate SCAN identities and
correlate them across time. They are also unauthenticated: an on-segment attacker can
forge advertisements that point a known identity at an arbitrary IP. This is harmless to
confidentiality — the Noise handshake to the wrong IP fails because the responder will
not hold the matching static key — but it can deny service. SCAN assumes the local segment
is semi-trusted; for hostile networks use a gateway reachable over a trusted tunnel.

### Gateway-based Configuration

Devices must support connecting through "Gateways". A Gateway is a Logical Layer-level
software or hardware device that does not necessarily have an Application Layer presence —
it may be invisible to the network, but presents all the devices that connect to it.

A device may be configured with any number of gateways concurrently. Each gateway represents
a distinct set of logical peers reachable through it, typically on a different network
segment. There is no ordering, failover, or primary/secondary relationship among gateways:
the device maintains an independent TCP connection to each, and treats the union of their
advertised identities, plus any locally multicast identities, as its view of the network.

A device may also be configured to operate exclusively through gateways, in which case
it does not join the multicast group, does not emit local-segment advertisements, and
ignores any local-segment traffic; the configured gateways are then the sole source of
identity-to-IP mappings.

If the same logical peer is advertised by more than one gateway (or by both a gateway and
the local segment), the device opens at most one logical connection to that peer to the
newest source gateway it had seen, with the same rules as in Addressing.

Operations through a gateway map thusly:

* A gateway is configured out-of-band as an `(IP, TCP port, PSK)` triple. The PSK
  is a 32-byte shared secret; there is no default gateway port. The device opens a
  TCP connection to the configured address and completes a TLS 1.3 handshake using
  the gateway PSK as an external pre-shared key (RFC 8446 §4.2.11), ciphersuite
  `TLS_AES_128_GCM_SHA256`, before any SCAN framing is exchanged. If the handshake
  fails, the device closes the TCP connection and reconnects under the normal
  exponential backoff. All subsequent SCAN traffic, including `Advertisement`
  frames, flows inside the TLS channel.
* When a logical peer is reached via a gateway, all traffic for that peer flows over
  that gateway's TCP connection.
* On TCP loss to a gateway, the device reconnects under the same exponential backoff
  as in §Addressing.
* The gateway emits `Advertisement` frames over that TCP connection on the same event
  triggers as local multicast (initial connect, change, solicited reply), each carrying
  up to 16 `PeerAddress` entries. The `port` field of each such frame carries the
  gateway's own TCP listening port, so the receiver's `(IP, port)` mapping for every
  identity behind the gateway points back at the gateway. Because the mapping
  reported to the client is always the gateway's own address, address churn of
  peers behind the gateway (roaming, DHCP renewals, reconnects) is absorbed by
  the gateway and does not trigger a change advertisement. Change advertisements
  are emitted only when a new identity becomes reachable through the gateway,
  and carry only the newly-reachable identities, not the full set.
  Advertisements have no counterpart to announce that an identity is no longer
  reachable: their purpose is to maintain the identity-to-address mapping for
  peers that can potentially be contacted, not to track online presence. Because
  TCP is reliable, each event emits a
  single frame rather than the three-frame burst used for multicast. Gateways
  may coalesce change events over a short, implementation-defined window to
  limit fan-out under membership churn. Devices process advertisements received
  over TCP identically to those received over UDP multicast. On initial connect,
  and whenever a single change batch exceeds 16 entries, the gateway sends
  multiple frames; the identity set is eventually consistent.
* The gateway assigns its own `generation` counter to every advertisement it emits,
  independent of the counters carried by the peers behind it. The gateway's counter
  reflects only the gateway's own emissions: it is bumped when the set of identities
  reachable through the gateway changes, not when an individual behind-the-gateway
  peer re-advertises. Because the gateway is the emitter from the receiver's
  perspective (the cached `(IP, port)` is the gateway's own address), generation-based
  eviction on the receiver side operates over the gateway's counter exactly as it
  would for any other emitter. A peer that becomes unreachable behind the gateway
  does not produce an "unadvertise"; it simply stops being included in future
  advertisements that the gateway emits, and the gateway's next generation-bumping
  event ages the corresponding identity out of the receivers' caches.
* A gateway maintains, on its inside face, its own per-peer address cache exactly
  as in §Addressing, using the generation counters of the inside-connected peers to
  evict obsolete entries and to drive the same newest-first failover behavior. This
  lets the gateway exploit inside-peer multi-homing (for example a peer reachable
  over both wired Ethernet and WiFi on the inside segment) in the same way an
  end device would.
* On TCP establishment to a gateway, the device sends an `Advertisement` over that same
  connection covering the logical identities the device itself represents, with its
  own TCP listening port in the frame, so the gateway can route inbound traffic.

Gateways have no cryptographic access to payloads — end-to-end encryption is preserved at
the Logical Layer, inside the gateway-hop TLS channel. The gateway-hop TLS-PSK protects
metadata (identity keys, packet timing, traffic volumes) from observers on the path between
the device and the gateway, and prevents unauthorized parties from connecting to the gateway
and enumerating the identities reachable through it. The gateway itself can still observe that
metadata and can drop traffic, and users should treat a gateway as untrusted transit,
equivalent to any other network intermediary.

Note, that a gateway may change metadata, specifically source and target peer address information,
as part of its normal operations.

### Address Change Handling

The mapping from logical peer to physical peer is maintained by processing
`Advertisement` frames into the per-peer address cache described in §Addressing. New
advertisements do not, on their own, trigger any change to an existing connection —
they merely refresh, extend, or (by generation) age out entries in the cache.
Reconnection is driven only by closure of the current TCP connection. When the TCP
connection closes for any reason, the device walks the cache head first, attempting
each currently-cached address once per cycle before entering exponential backoff,
per §Addressing. An entry whose generation has aged out is already absent from the
cache by the time the next cycle consults it.

The logical connection (Noise keys, subscription state) is not affected and resumes
per the reconnect rules in the Logical Layer. This handles DHCP renewals, WiFi
roaming between access points, and interface changes on multi-homed peers —
including the case where the peer remains reachable on one interface while another
goes down, which simply ages out of the cache after two silent generations.

### Network Acquisition

Devices are expected to be available through a variety of network topologies and
configurations, including through static or non-static IP addresses, through WiFi, with or
without DHCP, through VPN, or through multiple network segments each with its own network
zones or firewalls.

Devices therefore must support low-level network configuration options to enable them to
participate in the SCAN network. These must at least include the following options:

* Direct connection to a SCAN network. Discovery and address resolution through multicast UDP.
* Connection through one or more gateways. Discovery and address resolution through each
  gateway directly.

Gateways present a way to configure a static set of IP addresses to speak to, where each
gateway is essentially a stand-in for all devices that are behind it. This may be necessary
for devices that are not on any local network, connected through untrusted networks such
as cellular networks or other host networks.

Zero-configuration IP acquisition is a goal; devices should combine the following mechanisms:

* DHCP (IPv4) and SLAAC or DHCPv6 (IPv6) where available.
* IPv4 link-local auto-selection (RFC 3927) when DHCP is not available, to support
  ad-hoc wired networks. IPv6 nodes always have a link-local address by construction.

How and in what order these are tried is implementation-defined; the point is that
joining a network should require zero manual configuration wherever possible.

A device must complete network address acquisition before joining the multicast group
or emitting any advertisements. Link-local addresses — `169.254.0.0/16` (IPv4,
RFC 3927) and `fe80::/10` (IPv6) — may be used as advertisement source addresses;
§Addressing describes how receivers record the receiving interface alongside a
link-local source so the zone is preserved for outbound TCP. Non-link-local
addresses (SLAAC, DHCP/DHCPv6, static) are preferred when available and are used
without an interface association.

Devices with multiple network interfaces treat each interface independently: they listen
for TCP on their chosen SCAN port on each interface, join the multicast group on each
interface, and emit their own advertisements on every interface they are active on.
A single listening port across all interfaces is sufficient; nothing requires the
device to pick a different port per interface.

NAT/firewall traversal techniques (STUN, TURN, ICE) are not part of the SCAN
protocol; a device behind NAT without an inbound path should reach the network by
initiating a TCP connection outward to a gateway.

At the end of network configuration, devices must be able to send and receive frames to
and from the rest of the network or parts thereof, so that the user can connect to it
with an administrative device.

Note that joining a network is not a security-sensitive operation. The layers above are
designed to handle communication through insecure networks just fine. The point of this
layer is to make the device available to talk to, in the most convenient way possible for
the user.

This section presumes that the device already has the credentials necessary to attach to a
network (for WiFi: an SSID and passphrase; for Ethernet: a physical connection). Delivering
those credentials to a device in factory state is a separate concern covered by §Bring-Up.

### Networking Considerations

SCAN frames are capped at 1200 bytes (see Logical Layer §Frames), which sits below the
IPv6 minimum path MTU of 1280 bytes after worst-case TCP/IP header overhead. Combined
with a single `write()` of a whole frame on a `TCP_NODELAY` socket, each SCAN frame is
sent as exactly one IP packet on any path that meets the IPv6 minimum. Path-MTU
discovery failures, ICMP black holes, and MSS clamping on tunnels therefore do not
affect SCAN frames specifically — a path whose effective MTU falls below 1280 bytes is
broken by IPv6 standards, not by SCAN.

All connections should use **TCP_NODELAY** to prevent the TCP/IP stack from holding
data for batching purposes. Not using this option could delay frames up to several
hundred milliseconds.

### IANA Allocations

The following allocations are intended but not yet registered:

* TCP port **11372** as the default SCAN listening port. The actual TCP port a
  device listens on is announced in its `Advertisement` frames and may differ from
  the default — for example, when the default port is taken by another process or
  when several SCAN devices share a host. Devices should bind 11372 when they can.
* UDP port **11372** for multicast advertisements only. This port is fixed: it is
  the rendezvous point at which devices listen for discovery traffic, and cannot
  itself be discovered.
* IPv4 multicast group **239.255.255.244** from the administratively-scoped block
  (`239.0.0.0/8`), scoped to the local L2 segment by TTL=1.
* IPv6 multicast group **ff12::2c6c** (transient, link-local scope).

Until registration is complete, the IPv6 group uses the transient flag (`ff12` rather
than `ff02`).

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

The communication on this layer is packet based. All frames are designed to fit in a 1200 byte
buffer. This is below the IPv6 minimum path MTU of 1280 bytes minus worst-case TCP/IP header
overhead, so a frame written in a single `write()` on a `TCP_NODELAY` socket is sent as exactly
one IP packet on any compliant path. Payloads larger than a single frame are split across multiple
chunks (see Payload Messages).

A logical connection is a connection between two devices identified by their public static keys. All
devices have a static key pair, the public part of which identifies the device uniquely and securely
on the network. There can be at most two logical connections between any two devices, because
the ordered pair of public static keys uniquely identifies a logical connection. Note however, that one physical connection can tunnel more than one logical connection.

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
Frame {
   sourcePeer:      Option(PeerAddress),
   destinationPeer: Option(PeerAddress),
   content:         FrameContent
}

FrameContent = Control | Payload | Presence

PeerAddress = Array(Byte, size = 32)
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

Content delimiting is provided by the types parser. All peers, as well as intermediaries (like gateways) must be able to parse
all message types on this layer. If a message type is unknown (parsing fails), a device must close the connection, although
this shouldn't happen given the version number included in the handshake.

### Control Messages

```
Control = InitiateHandshake | ContinueHandshake | CloseConnection
```

#### Initiate Handshake

Sent first from the initiator of the connection to establish a logical connection.
If a physical connection does not exist yet, the initiator must try to open one first.
The frame transmits the first handshake message together with the
Noise Protocol Name and version of the logical layer.

```
InitiateHandshake {
   noiseProtocolName: String(MaxInclusive(128)),
   protocolVersion:   Version,                                    // 1.0 for this specification
   handshake:         Array(Byte, size = MaxInclusive(128))
}
```

The Noise Protocol Name is the exact protocol used for the following handshake
and data exchange. 

The protocol name, as well as the versions have to be included in the *prologue* of the Noise Handshake to
make sure it has not been tampered with.

If the responder disagrees with the version or the Noise protocol, it may respond with an Initiate Handshake
of its own with the counter proposal. If the initiator does not agree to that, the handshake failed.

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

Note, that the handshake does not identify the PSK used explicitly, it is only part of the hash calculations implicitly. The responder
might therefore need to try multiple of its configured PSKs to know which one the initiator is using.
The protocol is designed so a single try takes a single hashing operation only. Still,
in the worst case, the responder may need to execute as many hashing operations as the
number of configured PSKs it knows about.

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
ContinueHandshake {
   handshake: Array(Byte, size = MaxInclusive(128))
}
```

#### Close Connection

Both parties may send this message to terminate the logical connection. After this message
all keys and state information about the connection can be discarded.

```
CloseConnection {
   reason: String(MaxInclusive(128))
}
```

It contains a diagnostic message, a human readable reason for closing the connection.

### Payload Messages

```
Payload = IntermediatePayloadChunk | LastPayloadChunk | SingleChunkPayload

// Used later
EncryptedPayload {
   payload:   Array(Byte, size = MaxInclusive(1100)), // Sized so the enclosing frame fits in 1200 bytes under worst-case overhead
   mac:       Array(Byte, size = 16)
}
```

#### Intermediate Payload Chunk

A part of an application message, including the initial chunk, but not the last chunk. This frame indicates
that the message is not complete, additional chunks will follow for this message.

The actual payload of the application layer is described in the next chapters. This message
may be sent by both the initiator and responder.

Structure:

```
IntermediatePayloadChunk {
   messageId:          VariableLengthInteger(8),
   encryptedPayload:   EncryptedPayload
}
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
LastPayloadChunk {
   messageId:          VariableLengthInteger(8),
   encryptedPayload:   EncryptedPayload
}
```

Encryption and key management is the same as for intermediate frames.

The Message Id used in this frame must be considered reusable after this frame is sent.

#### Single Chunk Payload

An application message that fits a single chunk.

```
SingleChunkPayload = EncryptedPayload
```

Encryption and key management is the same as for intermediate frames.

### Presence Messages

Presence frames manage the discovery and liveness of peers on the network. They are not
encrypted and do not advance the Noise cipher state of any connection.

```
Presence = Advertisement | AdvertisementRequest | Heartbeat
```

#### Advertisement

Announces the identity or identities represented by a device. Announces that these
static keys are reachable at the address the frame is sent from. A device such as a
gateway may represent multiple logical identities on behalf of other devices, which is
why multiple static keys may reside at the same IP address.

```
Advertisement {
   port:       UnsignedInteger(2),
   generation: VariableLengthInteger(8),
   peers:      Array(PeerAddress, size = MaxInclusive(16))
}
```

The `port` field is the TCP port on which the sender accepts SCAN connections for the
logical identities listed in `peers`. Receivers combine it with the source IP of the
datagram (for multicast) or the remote end of the TCP connection (for advertisements
arriving over TCP) to form the `(IP, port)` mapping cached per `PeerAddress`. A frame
may contain up to 16 static keys; if a device represents more logical identities than
that, it sends multiple frames with the same `port` and the same `generation`, and the
identity set is eventually consistent.

The `generation` field is a per-emitter monotonic counter that identifies a single
advertisement *event*. Every frame produced for the same event carries the same
value: all three frames of a birth burst, every frame of a multi-frame split when
more than 16 identities are represented, and every copy emitted on every active
interface. A new event (change re-burst or solicited reply) carries a new, strictly
greater value. Receivers use `generation` to recognize which cached `(IP, port)`
entries a given emitter still stands behind: because the emitter advertises on every
active interface in every generation, a cached entry whose generation falls
sufficiently behind the newest seen for that emitter can be evicted as obsolete (see
§Addressing). This generation counter is reset to 0 on reboot.

The emitter of a generation is the device whose advertisement reaches the receiver:
for multicast advertisements it is the originating device, and for advertisements
forwarded over a gateway TCP connection it is the gateway itself. Gateways issue their
own generation counter independent of the counters of the peers behind them: a change
behind the gateway does not bump the gateway's counter unless the *set* of identities
reachable through the gateway changes (see §Gateway-based Configuration). This keeps
the receiver-side eviction rule simple — it operates on whichever emitter actually
delivered the advertisement.

Advertisement is emitted only on events, never at a steady periodic rate:

* **Birth burst.** A device sends three `Advertisement` frames spaced 0-500 ms apart
  (uniform random jitter per inter-frame gap) when it first becomes reachable on the
  network, so that segments of devices booting together — e.g. after a power restore —
  do not burst in lockstep. This tolerates multicast packet loss and makes the device
  visible to any already-present peer.
* **Re-burst on change.** The same burst is repeated on any change that affects
  reachability or identity: IP address change, interface change, or addition or
  removal of a represented logical key. Re-bursts are rate-limited to at most one
  per 5-second window per device; multiple change events occurring within a window
  are coalesced into a single re-burst at the window boundary, advertising the
  state as it stands at that moment.
* **Solicited reply.** A unicast reply to any `AdvertisementRequest` whose filter
  matches the device (or is empty), sent to the solicitor's IP with a small random
  jitter (0-500 ms) to spread response storms.

Advertisement is not a periodic keep-alive. Liveness of an established connection is
tracked on the TCP connection itself using `Heartbeat` (see below).

When sending to a gateway, an `Advertisement` frame may be delivered over the TCP
connection to the gateway. A gateway must send `Advertisement` frames over its TCP
connection to each connected device on the same event triggers (on TCP establishment,
on change, in response to a solicitation arriving via that TCP connection), covering
every logical identity reachable behind it. Because TCP is reliable, advertisements
over a gateway TCP connection are emitted once per event rather than as a three-frame
burst.

#### AdvertisementRequest

Solicits `Advertisement` replies from one or more specific peers, or from everyone on
the segment.

```
AdvertisementRequest {
   port:      UnsignedInteger(2),
   peers:     Array(PeerAddress, size = MaxInclusive(16))
}
```

The `port` field is the TCP port at which the solicitor accepts SCAN connections, so
the responder can reach the solicitor even though it may not yet have observed an
`Advertisement` from it. If `peers` is empty the request is a broad solicitation:
every device on the segment replies with its own `Advertisement`. If `peers` is
non-empty, only devices holding at least one of the listed logical keys reply.

Replies are sent unicast over a TCP connection to the solicitor's IP address at the
`port` carried in the request, with a small random jitter (0-500 ms) to spread
response storms. The reply is the full `Advertisement` for the replying device (all
identities it represents), not just the matching keys. The responder's receipt of the
request (which carries the solicitor's `PeerAddress` in the Frame header, sourced from
the solicitor's IP, and the solicitor's TCP port in the request body) satisfies the
"must have observed an `Advertisement`" precondition for opening that TCP (see
Internet Layer §Addressing). If no logical connection has been opened over the
newly-established TCP after the reply has been sent, the replier closes it.

Typical uses:

* A newly-booted device with wired peers sends one `AdvertisementRequest` listing the
  32-byte keys of those peers and receives up to that many unicast replies, resolving
  every needed IP mapping in a single round trip.
* An administrative interface refreshing its view sends an empty
  `AdvertisementRequest` and collects replies for a short window before updating the
  UI.
* A device that has lost the IP mapping for a peer (e.g. after its own reboot) sends
  a filtered request for that one key.

A request may be sent over UDP multicast (for a local segment) or over a gateway's TCP
connection, in which case the gateway forwards it to its connected devices and relays
the unicast replies back.

#### Heartbeat

An explicit liveness probe sent from the Responder to the Initiator over an open TCP
connection.

```
Heartbeat = Unit
```

`Heartbeat` carries no payload, is not encrypted, and does not advance the Noise
cipher state. Because it is unencrypted and role-agnostic at the wire level, Heartbeat
flows from TCP establishment onward and does not wait for the logical handshake to
complete — a stalled handshake is therefore caught by the same liveness timer as any
other silence.

Heartbeats are one-directional. The Initiator is the party with a liveness interest —
it is consuming state from the Responder and needs to know when that stream becomes
stale — so only the Responder emits `Heartbeat`. The Responder learns of the
Initiator's disappearance on its next `Heartbeat` or `State` send failing at the TCP
layer, which reclaims subscription state with a slower but sufficient guarantee. This
matches the Initiator/Responder asymmetry of the protocol overall.

From the moment the TCP connection is open, the Responder must send a `Heartbeat` if
no other frame has gone out in `heartbeatInterval` (default 1 second). The Initiator
considers the Responder offline if no frame has been received in `livenessTimeout`
(default 3 seconds). Any received frame — `Heartbeat`, `Payload`, `Advertisement`, or
otherwise — resets the Initiator's liveness timer. The effective `livenessTimeout`
may be tightened by active subscriptions (see `Subscribe` in the Modalities Layer);
`heartbeatInterval` is always `livenessTimeout / 3`.

A device that is "connected" (logical connection exists, keys retained) but has no
current TCP connection is considered *offline*, whether it went silent intentionally,
due to network conditions, or because the liveness timer expired. Other devices must
not send to an offline device; the offline device may reconnect later using existing
Noise keys and resume without a new handshake.

If, after reconnect, cryptographic keys are out of sync or no longer exist, the
connection must be closed, forcing the initiator to establish a new logical connection.
Receivers must send a `CloseConnection` frame in response to unsolicited encrypted
application frames whose keys they do not have.

Devices are not required to persist connection information across reboots. An
implementation may treat any offline event as a full disconnection and forget the keys.

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

The initiator is free to close the physical connection at any time to mark itself
"offline" but still "connected".
A responder does not close the physical connection
intentionally — any close it observes is treated by the initiator as unexpected and
triggers the immediate-reconnect/backoff rule in §Addressing.

#### Initiator re-establishes a physical connection

1. Initiator sends Application Messages with previously established keys.
2. If Responder does not remember previous keys, or is unable to decrypt, it sends a Close frame.
3. Otherwise, logical connection is still established.

### Address Resolution

A device learns the mapping from logical peer (32-byte key) to physical peer (IP
address) by receiving `Advertisement` frames. These arrive in three ways: as part of
another device's birth burst, as a re-burst after a change, or as a unicast reply to an
`AdvertisementRequest` this device sent.

There is no passive periodic announcement, so a device that needs a mapping must either
wait for the peer's next event-driven burst or actively solicit it. A device should
solicit whenever it needs a mapping it does not have cached — for example, on startup
for every peer it has wiring for, or when the user of an administrative interface asks
to see the network.

Caching of advertised mappings is recommended but not required. A cached mapping should
be refreshed opportunistically whenever a new `Advertisement` is observed, and may be
evicted when the corresponding logical connection closes.

If no IP address can be found for a given identity key after a reasonable solicitation
attempt, the connection cannot be established. Devices may surface this to the user if
capable, or emit specific error events through other logical connections.

Offline status of connected peers is tracked on the TCP connection itself, not by
monitoring advertisements. See `Heartbeat` above.

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
one at the earliest time possible.

A device is **authoritative** for the outputs of its own modalities: whatever it publishes, it must be able to reproduce on demand.
It must therefore keep one consistent snapshot of each of its modalities' current output state accessible in some form — the raw
value in memory, a derived form (a sensor reading, a computed state), a reference to stored content, whichever the implementation
chooses — so that it can emit a complete `State` frame for that modality whenever a subscriber needs one. The encoded size of a
modality's output is bounded statically by its type (up to any trailing `Stream`, whose items are produced live from underlying
state as they are emitted), so the resource budget per modality is bounded.

Inputs arriving from remote peers, in contrast, may be **stream-processed**: a device may consume the bytes of an incoming `State`
as they arrive, apply them to its local state event by event, and discard the input bytes as they pass — without ever holding the
full input. This lets modalities accept inputs arbitrarily larger than the device's available memory — firmware blobs, backups,
video streams — provided the device's input processing is event-driven. See *Value Decoding Events* in `TYPES.md` for the event
model.

Beyond this, the only per-instance state the protocol itself requires is the small Lamport metadata used to order writes (see
*Last-Writer-Wins Ordering* below).

**Message atomicity.** A receiver processes at most one incoming `State` at a time for a given modality instance. If a peer
attempts a new `State` for the same instance while one is already in progress, the receiver rejects the attempt with a `Busy`
message (see *Shared Messages* below). The rejected sender stops transmitting the in-flight message, discards its partial-send
record, and keeps only its authoritative output copy (already required above). Any further local state changes that occur while
the sender is waiting are coalesced into that held copy. When the receiver finishes processing and is willing to accept another
`State` for that modality, it sends a `Ready` message; the sender then transmits whatever value the held copy has at that
moment.

For `scan.vmods` cluster members the unit of serialization is the whole cluster rather than a single instance: a `State` in
progress for any cluster member causes `Busy` on every other member of the same cluster. This is the cost of the cross-member
consistency that a cluster exists to provide. See *Virtual Modalities* in the Application Layer for the cluster model.

The cost of this mechanism is paid only under actual contention. In the common case of a single writer per modality instance
no `Busy` or `Ready` message is ever exchanged — the sender sends, the receiver consumes, and no per-message acknowledgement
traffic crosses the wire. This is what lets SCAN avoid per-chunk acknowledgements entirely, which would otherwise impose a
fixed tax on every subscription whether it contended or not.

**Endless-stream modalities.** A modality whose `inputType` contains an unbounded `Stream` holds the receiver's attention for
as long as the stream continues. Contending writers on such an input remain in `Busy` until the active writer finishes, which
for a truly endless stream is never.

While connecting modalities is semantically symmetric, as data is moving back and forth the same way between devices, with exactly same rules, the
connection itself is not symmetric. One device, the *Initiator*, connects to the other device, the *Responder*. The Initiator will make requests
to the Responder, which will reply. Note also, that the Initiator will present the PSK, therefore the Responder will authorize the Initiator to
make requests, not the other way around.

### Modality Instances, Groups, and State Convergence

A *modality instance* is a concrete occurrence of a modality on a device, identified
by a value of the modality's `keyType`. A device hosts one or more instances of every
modality it declares; an instance lives on exactly one device and is the smallest
unit that state is attached to.

Two instances may be *wired* together if their types are complementarily compatible:

* `A.outputType` equals `B.inputType`, and
* `B.outputType` equals `A.inputType`.

A symmetric modality (`inputType == outputType`) is the degenerate case. Asymmetric
pairs are equally valid: one side's types may be `(X, Y)` while the other's are
`(Y, X)`. Both sides of the connection agree on what the state *means*; they just
encode it in the type appropriate to each direction.

A *modality instance group* is the transitive closure of instances connected by
wiring. The protocol has no group-level machinery -- no membership list, no
coordinator, no group identifier. Groups emerge from the wiring graph and dissolve
when it is removed.

#### Last-Writer-Wins Ordering

Every `State` message carries a `counter` and (optionally) a `writer`. Together
these form a Lamport clock per instance that establishes a Last-Writer-Wins ordering
across all participants:

* Each peer maintains, per instance, a local `counter` (its own progress) and a
`seenCounter` (the highest counter it has observed from anyone).
* On local write: `counter := max(counter, seenCounter) + 1`. The frame is sent
with `(counter, writer = self, value)`. The `writer` field is omitted on the wire
because the sender of the frame is the writer.
* On receive: the Lamport header `(counter, writer)` is encoded before `value` in a `State` frame, so the receiver decides the LWW
outcome before any value bytes are committed. If `(received.counter, received.writer)` is lexicographically greater than the stored
`(counter, writer)` for this instance — counter first, ties broken by lexicographic comparison of the 32-byte `PeerAddress` of the
writer — update `seenCounter := received.counter`, replace the stored pair with the received one, and accept the incoming value:
apply it to local behaviour and/or relay it to downstream peers chunk by chunk as the bytes stream in. Otherwise the frame is stale;
consume and discard the value bytes with no effect on local state and no relay to downstream peers. The incoming wire value is never
required to be held in memory — the Lamport pair is the only per-instance state LWW itself keeps.

All peers that have seen the same set of `State` messages converge to identical Lamport pairs `(counter, writer)` per instance, and
therefore agree on which write is currently in effect. The associated value is carried on the winning `State` frame and is not stored
separately; a peer that needs the value at a later moment re-acquires it by the normal subscribe flow (either because the writer
retransmits, or because a new write produces a fresh pair). Message order, duplication, connection drops, and device restarts do
not affect the result.

When a peer relays a value it did not originate -- as happens naturally inside an
instance group where writes from any participant may be reflected back by another
participant -- it includes the original `writer` explicitly, so the Lamport data
is preserved across any number of hops.

#### Asymmetric Groups

The `(counter, writer)` pair describes the group's state independent of direction.
The `value` is that state's representation in the type the receiver expects. LWW
merges on `(counter, writer)` alone; the value's wire representation is whatever the
direction demands. This makes asymmetric groups work exactly the same way as
symmetric ones -- the Lamport stream is uniform, only the value encoding varies.

#### `Nothing` Value Types

A value type of `Nothing` does not exclude an instance from a group. A `State`
frame with `value` of type `Nothing` encodes as zero value bytes and carries only
the Lamport metadata. These metadata-only frames are what allow write-only sides
of a modality -- a pure sensor's input, a firmware receiver's output -- to
participate in the group's convergence.

They matter most for restart recovery. A subscriber that still holds the Lamport pair from a write made before a device crash can
echo that pair back to the restarted device through a metadata-only `State`, even when the device's input type is `Nothing`. The
restarted device's `seenCounter` catches up, its next write carries a strictly higher counter, and any lingering phantom writes
are ordered behind the recovered state everywhere.

#### Restart Recovery

On restart, a device reinitialises each of its instances to `(counter = 0, writer = self)` with the configured default as its
current value, and rejoins its groups normally. Peers that still carry a later Lamport pair for the instance send it back as part
of the normal state exchange (in either direction, including via `Nothing`-valued frames). The restarted device's `seenCounter`
updates from those messages, and subsequent writes carry strictly higher counters than any pre-crash ghost.

No persistent Lamport state is required on the device. If no reachable peer carries a later Lamport pair, the restarted device is
effectively alone in the group and its configured default is as authoritative as any -- which is the correct behaviour, as no
external source of truth exists to contradict it.

### Initiator Messages

```
InitiatorMessage = Subscribe | Unsubscribe | State | Busy | Ready
```

#### Subscribe

Request the Responder to send state values indefinitely for the specified modality.

```
Subscribe {
   modality:            IndexedModalityReference,
   minimumSendWait:     Duration,
   priority:            Option(Priority),
   livenessTimeout:     Option(Duration)
}
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

The `livenessTimeout` field, if present, requests a tighter liveness guarantee for this
subscription than the connection default of 3 seconds. While the subscription is active,
the Responder must ensure that it sends a `State`, a `Heartbeat`, or some other frame
at least every `livenessTimeout / 3`; the Initiator uses `livenessTimeout` as its
offline-detection threshold for this subscription. When a connection carries multiple
active subscriptions, the effective connection `livenessTimeout` is the minimum of
3 seconds and all subscription overrides — the tightest requirement wins. Typical use:
an autopilot subscribing to a rudder angle sensor sets this to 500 ms for prompt loss
detection, while a dashboard displaying the same sensor leaves it unset and rides the
3 s default.

The Initiator may repeat this message if the waiting period, priority, or liveness
timeout changes for some reason.

A Responder has at most one `State` in flight per subscribed modality instance at a
time, per the *Message atomicity* rule in the Modalities Layer overview. Values that
change while a `State` is being transmitted are coalesced into the authoritative
output copy and transmitted after the current message completes.

#### Unsubscribe

Request the Responder to stop sending state values.

```
Unsubscribe {
   modality: IndexedModalityReference
}
```

The Responder must immediately stop sending state updates, and interrupt any outstanding streams.

The Initiator may send this message, if it does not use the state updates anymore (for example the data is not on screen).

#### State 

Signal a change of the visible state of a modality.

```
State {
   modality:      IndexedModalityReference,
   counter:       VariableLengthInteger(8),
   writer:        Option(PeerAddress),
   value:         DynamicValue
}
```

The `modality` must reference the target modality on the Responder device.

The exact type of the `value` should be the `inputType` of the target modality.

The `counter` is a monotonically increasing per-instance value that the writer
advances on every state change. It lets receivers order updates deterministically
across device restarts, reconnections, and forwarding through multiple paths.

The `writer` is optional. When absent, the sender of the frame is taken as the
writer. This is the common case -- a peer transmitting its own latest state --
and eliminates the 32-byte address from every such frame. The `writer` field
is only carried when the sending peer is relaying a state originally produced
by a different participant, which is what happens when multiple peers contribute
to the same modality instance through a group.

What these fields are for, and how shared state behaves across a group, is
described in the *Modality Instance Groups and Shared State* technical
discussion.

### Responder Messages

```
ResponderMessage = Modalities | State | Busy | Ready
```

#### Modalities

Responders send this message as soon as a connection is established, unsolicited.

```
Modalities = Array(Modality)

Modality {
   id:                 String,                // Identifier of this modality on this device
   name:               Text,
   description:        MarkdownText,
   minimumIntentWait:  Duration,              // Minimum time to wait between inputs
   priority:           Priority,              // Default traffic priority for this modality
   keyType:            Type,                  // The type identifying modality instances
   outputType:         Type,                  // The type of the visible state of this modality
   inputType:          Type                   // The type of the changeable part of the state
}
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
State {
   modality:      IndexedModalityReference,
   counter:       VariableLengthInteger(8),
   writer:        Option(PeerAddress),
   value:         DynamicValue
}
```

Note, that because of the Resolution Principle the Device must immediately
send the newest data value upon receiving a Subscribe. Since full values are not
required to be held in memory, this typically means producing the value on demand
-- taking a fresh measurement, reading configuration from persistent storage,
re-opening a streaming source, etc. -- so long as the value reflects the most
current state in the given semantics. A device is free to cache a value that is
small and cheap to keep, but is never required to.

Note also, that this semantic may include a month-end meter value for example. "Historical"
values are allowed as long as its defined that way.

The value must be of type `outputType` defined in the modality.

The `counter` and `writer` fields follow the same rules as on the Initiator
side: `counter` is always present and monotonically increasing per instance;
`writer` is omitted when the sender is itself the writer of the current value.
See the *Modality Instance Groups and Shared State* technical discussion for
what these fields are for.

### Shared Messages

These messages may be sent by either party. They implement the *Message atomicity*
rule of the Modalities Layer: the receiver of `State` traffic pushes back when it
cannot accept another message for a modality, and later invites the sender to resume.
Neither message is sent in the absence of contention, so well-behaved single-writer
subscriptions never exchange these messages at all.

#### Busy

Reject an incoming `State` because the receiver is already processing another `State`
for the same modality instance (or, for `scan.vmods` cluster members, for any member
of the same cluster).

```
Busy {
   modality: IndexedModalityReference
}
```

A peer that receives `Busy` for a modality must:

* immediately stop transmitting further chunks of any in-flight `State` for that
  modality on this connection (the receiver has already discarded the partial bytes),
* discard its record of the rejected send — its Message Id becomes reusable,
* keep its authoritative output copy for the modality (already required by the
  Modalities Layer rules), and
* not send another `State` for that modality on this connection until it receives a
  matching `Ready`.

Any local state changes that occur while waiting are coalesced into the held copy;
only the value the sender holds at the moment `Ready` arrives is transmitted.

`Busy` is sent only in reaction to an attempted send. A peer does not pre-emptively
`Busy` a subscription that is silent.

#### Ready

Invite a previously-rejected sender to resume `State` transmission for a modality.

```
Ready {
   modality: IndexedModalityReference
}
```

`Ready` is sent exactly once per preceding `Busy` on a given connection, by the peer
that sent the `Busy`, once processing of the blocking `State` has completed and the
receiver is again willing to accept a `State` for the referenced modality. On receipt,
the target peer sends its current authoritative value for the modality as a new `State`.

If the connection drops between `Busy` and `Ready`, no replay is required. On
reconnection the subscription starts fresh; normal send behaviour resumes and any
held state is sent as a normal `State`, subject to `Busy`/`Ready` again if contention
re-emerges.

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
   id          = "scan.enroll",
   priority    = Management,
   keyType     = Unit,
   outputType  = Unit,
   inputType   = PSK
)
```

The modality is "called" with the proposed administrative ("root") PSK. There is no output state, but if the call succeeded, 
the connection will be terminated, and the PSK can be used to connect to the device and receive all modalities.

The device must disconnect all connections as part of the enrollment and must not allow new connections until the new PSK is registered. The device may reboot as part of the enrollment.

#### Reset

Reset the device to factory settings, including removing any and all user settings and data.

```
Modality(
   id          = "scan.reset",
   priority    = Management,
   keyType     = Unit,
   outputType  = Boolean,
   inputType   = Boolean
)
```

Both the input and output denote whether the device should be in the "reset" state.

Usable only with the administrative PSK. Note: After the call that administrative PSK is not valid anymore. Device must terminate
all connections.

#### Rights

All the PSKs and associated rights on this device, expect the master adminitration key used for enrolling.

```
Modality(
   id          = "scan.grant",
   priority    = Management,
   keyType     = Unit,
   outputType  = Rights,
   inputType   = Rights
)

Rights = Array(PskRight)

PskRight {
   psk:           PSK,
   rights:        Array(Right)
}

Right {
   modalityId:    String,
   readOutput:    Boolean,
   writeInput:    Boolean
}
```

Each PSK must have only one entry in the array and list all rights associated with that PSK.

Usable only with administrative PSK. Note, that the administrative PSK is not listed here. If this list is empty,
the master administrative key stays valid. Additional "administrative" keys may be created, if needed, through this interface,
not through enrollment, by assigning rights to all relevant modalities.

Setting the PSKs and their rights will only affect authorizations *after* the rights were set on the device. Already
connected devices will still stay connected, regardless what they used for authorization. If the user wishes everything
to reconnect anew, a reboot/reconnect should be triggered.

The user may "rotate" PSKs at any time by supplying a fresh set of PSKs with similar rights. Since this will not cause any
disconnects, these rotations can be done without any downtime.

#### Keys

All the keys to other devices this device possesses.

```
Modality(
   id          = "scan.keys",
   priority    = Management,
   keyType     = Unit,
   outputType  = Keys,
   inputType   = Keys
)

Keys = Array(Key)

Key {
   device:        PeerAddress,
   psk:           PSK
}
```

When the device connects to another device, it must use the registered PSK to do so. There can be only one PSK for each
target device, to which all the rights necessary are granted on the target device.

#### Firmware

Represents the firmware on the device. All devices must support firmware updates.

```
Modality(
   id          = "scan.firmware",
   priority    = Bulk,
   keyType     = Unit,
   outputType  = FirmwareState,
   inputType   = FirmwareUpdate
)

FirmwareState {
   currentVersion: URI,  // The URI the current firmware is from
   nextVersion:    URI   // The URI where the next version will be (or is) available
}

FirmwareUpdate {
   version: URI,         // The URI of this firmware
   firmware: Media       // The firmware itself
}
```

Firmwares are simply identified by where it was downloaded from or where it will be downloaded from.
A GET to the "nextVersion" URI should get an updated firmware image, if available. This means the URI
will need to likely include the current (or the next) version number, as the server will need to determine whether a new version is available.
If the server returns HTTP status code `200` for the GET, the admin interface (or whatever software is doing the downloading)
should assume there is a firmware update available for this device with the current firmware. If it returns `404`, it should assume
no update is available, i.e. the device is up to date.

This supports a vendor-independent way to update every device on the network.

It is the responsibility of the device to include any and all mechanisms to verify the authenticity of the firmware, like checking
checksums, or cryptographic markers. All of this should be included in the media shipped. All of these operations should be
offline capable.

It is assumed that firmware updates will be applied through the administrative interface or dedicated servers. The devices themselves
should not assume that they have, or will eventually have access to the internet.

Note, that the device may reboot as part of the update to finish installing. The device will do nothing if the "version" in
the update is the same already installed.

#### Reboot

Reboot the device.

```
Modality(
   id          = "scan.reboot",
   priority    = Management,
   keyType     = Unit,
   outputType  = BootState,
   inputType   = BootState
)

BootGeneration = UnsignedInteger(4)

BootState {
   currentGeneration: BootGeneration,
   rebootGeneration: BootGeneration
}
```

On every bootup a device must generate a random "generation" number, the "currentGeneration". So the device updates
this part of the state as soon as it comes up.

The user side updates the "rebootGeneration" to match the "currentGeneration" if it wants the device to reboot. The
device reboots only if it receives a state where its own generation is current and the reboot generation matches.

This guarantees that it will eventually reboot, and when it comes back and sees this state again, it will not reboot
again.

Note: there is a non-zero chance the generation comes back the same, in which case the device needs to reboot again.

### Operational Modalities

Modalities supporting the day-to-day operations of the device.

All devices must implement all of these.

#### Device Information

Provide mostly static information about the device.

```
Modality(
   id          = "scan.info",
   priority    = Management,
   keyType     = Unit,
   outputType  = DeviceInformation,
   inputType   = UserDefinedData
)

DeviceInformation {
   deviceData:        DeviceData,
   versionData:       VersionData,
   userData:          UserDefinedData
}

DeviceData {
   name:              Text,                  // Name of the device
   description:       MarkdownText,          // Description of the device and its operation
   icon:              Icon,                  // Embedded icon for the device
   vendor:            String,                // The vendor's readable (non-localized) name
   web:               Option(URI)            // The product's web page, if given
}

VersionData {
   hardwareVersion:   Option(String),        // The vendor's own hardware version
   firmwareVersion:   Option(String),        // The vendor's own firmware version
   serialNumber:      Option(String)         // The vendor's identifier for this exact product instance
}

UserDefinedData {
   applicationName:   Option(String),        // User editable name for the current application / environment
   location:          Option(String),        // User editable location
   tags:              Array(String)   // User editable set of tags
}
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
   id          = "scan.health",
   priority    = Management,
   keyType     = Unit,
   outputType  = DeviceHealth,
   inputType   = Unit
)

DeviceHealth {
   status: HealthStatus,                     // Overall health summing everything up

   uptime: TimeInterval,
   lastBootReason: BootReason,

   temperature: Option(HardSoftLimited(Temperature)),
   voltage: Option(HardSoftLimited(Voltage)),
   current: Option(HardSoftLimited(Current)),
   battery: Option(HardSoftLimited(Percent)),

   nonVolatileMemory: HardSoftLimited(Information),
   volatileMemory: HardSoftLimited(Information),
   cpuUsage: HardSoftLimited(Percent),

   networkConnections: HardSoftLimited(Long),
   networkErrors: HardSoftLimited(Long),
   networkLatency: HardSoftLimited(TimeInterval),

   modalityStatus: HealthStatus,             // Sum status of all modalities
   modalityHealths: Array(ModalityHealth)
}

HealthStatus = Ok | Degraded { reason: String } | Error { reason: String }

BootReason = PowerOn
           | SoftwareReset
           | Watchdog
           | Brownout
           | Crash
           | Other { reason: String }

ModalityHealth {
   modalityId: String,
   status: HealthStatus,
   outgoingCount: Long,
   incomingCount: Long,
   failedIncomingCount: Long,
   connectedCount: Long
}
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
   id          = "scan.logs",
   priority    = Management,
   keyType     = Severity,
   outputType  = Logs,
   inputType   = Unit
)

// Categorization of log entries
Severity = Fatal   // Entry indicates a device-wide error condition
         | Error   // Entry indicates function specific error condition
         | Warn    // Function may be degraded or did not execute fully as intended by user
         | Info    // No errors, but user may want to know this
         | Debug   // Messages helpful for tracking problems

Logs = Stream(Log)

Log {
   severity:      Severity,
   message:       Text
}
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
   id          = "scan.backup",
   priority    = Bulk,
   keyType     = StateType,
   outputType  = Media,
   inputType   = Media
)

StateType = FunctionState | ConfigurationState | FullState
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
   id          = "scan.netstat",
   priority    = Management,
   keyType     = Unit,
   outputType  = NetworkStatistics,
   inputType   = Unit
)

NetworkStatistics = Array(NetworkPeerStatistics)

NetworkPeerStatistics {
   remotePeer:         PeerAddress,
   sentCount:          Long,
   sentBytes:          Long,
   receivedCount:      Long,
   receivedBytes:      Long
}
```

#### Network Configuration

Read, write, and re-apply the device's network configuration. This
modality carries everything the device needs to stay on the network over its lifetime:
per-interface WiFi credentials and IP configuration, plus the device-wide choice between
local-segment discovery and routed reach through SCAN gateways (§Gateway-based
Configuration). 

```
Modality(
   id          = "scan.netconfig",
   priority    = Management,
   keyType     = Unit,
   outputType  = NetworkConfiguration,
   inputType   = NetworkConfiguration
)

NetworkConfiguration {
   interfaces:    Array(InterfaceConfig, size = MinInclusive(1)),
   reachability:  Reachability
}

InterfaceConfig {
   name:       String,
   wifi:       Wifi,
   ipv4:       Ipv4Mode,
   ipv6:       Ipv6Mode
}

Wifi = NotApplicable
     | Disabled
     | WifiConnection { ssid: String, passphrase: Option(String) }

Ipv4Mode = Automatic
         | Disabled
         | Ipv4Static {
              address: Ipv4Address,
              prefix:  UnsignedInteger(1),
              gateway: Option(Ipv4Address)
           }

Ipv6Mode = Automatic
         | Disabled
         | Ipv6Static {
              address: Ipv6Address,
              prefix:  UnsignedInteger(1),
              gateway: Option(Ipv6Address)
           }

Ipv4Address = Array(Byte, size = 4)
Ipv6Address = Array(Byte, size = 16)
IpAddress   = V4 { address: Ipv4Address } | V6 { address: Ipv6Address }

Reachability = LocalOnly
             | GatewayOnly { gateways: Array(Gateway, size = MinInclusive(1)) }
             | Mixed       { gateways: Array(Gateway, size = MinInclusive(1)) }

Gateway {
   ip:     IpAddress,
   port:   UnsignedInteger(2),
   psk:    PSK
}
```

A written configuration is applied as a single snapshot. The output state reflects the
configuration currently in effect on the device, which may differ from the last input if
application failed (bad passphrase, unreachable gateway, invalid static address). Because
the Resolution Principle guarantees that the latest `State` message wins, the
administrative application observes the outcome by reading the modality back.

The `interfaces` array is non-empty; every SCAN-participating device has at least one
network interface. The `name` of each interface is a device-assigned stable identifier
(for example `wlan0`, `eth0`, or a vendor-assigned label); the administrative application
discovers the set of names by reading the output state before writing.

`Wifi.NotApplicable` indicates that the interface has no WiFi radio at all. `Disabled`
indicates a WiFi-capable interface whose radio is administratively off. `WifiConnection`
associates the interface with a specific network; `passphrase` is absent for open
networks, matching the `BringUpBlob` shape. Writing `NotApplicable` to an interface that
does have a WiFi radio, or anything other than `NotApplicable` to an interface that does
not, is invalid.

`Ipv4Mode` and `Ipv6Mode` `Automatic` selects the §Network Acquisition mechanisms
appropriate to the family (DHCP for IPv4, SLAAC or DHCPv6 for IPv6, with link-local
fallback); `Disabled` disables the family on the interface; `Ipv4Static` / `Ipv6Static`
carry an address, prefix length, and optional default gateway.

`Reachability.LocalOnly` is the default post-enrollment mode: the device participates in
the local-segment multicast discovery described in §Presence Messages and does not use
any SCAN gateways. `Mixed` adds one or more gateways alongside local participation.
`GatewayOnly` disables local-segment participation entirely — no multicast join, no
local-segment advertisements, no accept of local-segment connections — and routes all
SCAN traffic through the configured gateways. Both non-local variants require at least
one `Gateway`. A `Gateway` carries the `(IP, TCP port, PSK)` triple required by
§Gateway-based Configuration; the PSK is used as the TLS 1.3 external pre-shared key
that precedes SCAN framing on the gateway connection. A gateway has no `PeerAddress`:
it is Logical-Layer infrastructure with no application-layer identity.

If application of a newly-written configuration fails to produce a working network
presence within an implementation-defined window, a vendor MAY revert to a previous
configuration. Whether, when, and how to revert is left to the implementation; this
specification neither mandates nor forbids automatic recovery.

#### State Messages

Listen in into all the state messages this device is sending and receiving. Intended to be
able to debug which remote states the device is receiving and what it reacts in turn.

```
Modality(
   id          = "scan.messages",
   priority    = Management,
   keyType     = Unit,
   outputType  = Messages,
   inputType   = Unit
)

Messages = Stream(MessageEvent)

MessageEvent = Sent     { localModality:  LocalModalityReference,
                          remoteModality: RemoteModalityReference,
                          state:          State }
             | Received { localModality:  LocalModalityReference,
                          remotePeer:     PeerAddress,
                          state:          State }
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

Define clusters of local modalities whose values are coupled by transformations.

```
Modality(
   id          = "scan.vmods",
   priority    = Management,
   keyType     = Unit,
   outputType  = VirtualModalityClusters,
   inputType   = VirtualModalityClusters
)

VirtualModalityClusters = Array(VirtualModalityCluster)

VirtualModalityCluster {
   members: Array(ClusterMember)
}

ClusterMember {
   modality:   Modality,
   transform:  Array(Byte)    // Compiled transformation program
}
```

A cluster defines a set of local modalities (its *members*) on the device. Each member is a fully ordinary modality:
it appears in the device's `Modalities` advertisement, can be subscribed to, can receive intents, and is wired to
remote modalities through `scan.wiring` like any other modality. Other peers see no difference between a cluster
member and a native modality. The cluster itself is a host-internal arrangement and does not appear on the wire.

Each member declares a transformation program. Transformations are platform-independent,
interpreted programs, whose interpreter is described with the SCAN Type System.

A transformation has the signature `(state: Array<MemberValue>) -> Array<MemberValue>`. The input is the current
value of every cluster member, in the order declared in `members`. The output is the new value of every member,
in the same order, with the same types. Transformations are pure functions of the cluster state.

A member's transformation runs exactly once whenever that member receives an external state write (a `State` message
from a peer in its LWW group). When that change is received, it will see all other current state of all the other
members.

After a transformation runs, the host compares each output element against the corresponding member's current state.
Elements that differ are written as ordinary LWW writes on each member's group, using the host's own monotonically
increasing counter. Writes the transformation produces do not retrigger any cluster member's transformation. A single
external write therefore produces at most one transformation run and at most one outgoing write per member.

Each cluster member participates in its own LWW group, formed by wiring. Counters and writers are independent across
groups and do not propagate through transformations. The host is just another writer in each member's group. Causality
crosses the cluster — a change on one member can cause a write on another — but LWW state does not.

#### Wiring

Define what modalities on this device is wired to what modalities at other devices.

```
Modality(
   id          = "scan.wiring",
   priority    = Management,
   keyType     = Unit,
   outputType  = Wiring,
   inputType   = Wiring
)

Wiring = Array(Wire)

Wire {
   remoteModality:     RemoteModalityReference,
   localModality:      RemoteModalityReference   // Has to reference local peer
}
```

Note, that in order for these wirings to work, the necessary PSKs to contact the remote devices must be registered first.

Note, that the remote device will be the authoritative device for this wiring.

### Miscellaneous Modalities

There are optional modalities devices may choose to implement.

#### Locate Device

Help locate the device physically.

```
Modality(
   id          = "scan.locate",
   priority    = Normal,
   keyType     = Unit,
   outputType  = Boolean,
   inputType   = Boolean
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
IndexedModalityReference {
   modalityIndex:       VariableLengthInteger(8), // The index in Modalities modality array
   modalityInstanceKey: DynamicValue              // The key of the modality instance
}

// A reference to a modality in the whole system, potentially local
RemoteModalityReference {
   peer:                 PeerAddress,
   modalityReference:    LocalModalityReference
}

// A reference to a local modality, relative a peer
LocalModalityReference {
   modality:             String,
   modalityInstanceKey:  DynamicValue
}

// Traffic priority for network-level quality of service
Priority = Critical | Normal | Management | Bulk

// PSK (Pre-Shared Key), used for authorization
PSK = Array(Byte, size = 32)

// Version
Version {
   major: Byte,
   minor: Byte
}

URI = String

// Represents content that is typed by mime-type
Media {
   mimeType:    String,
   content:     Stream(Byte)
}

// --- Display / human-readable text ---

// Plain user-facing text, no markup. Alias of String for now.
Text = String

// User-facing text in CommonMark Markdown. Alias of String for now;
// the marker exists so administrative interfaces know to render it.
MarkdownText = String

// TODO: Icon — small embedded image for display in administrative UI.
//       Likely a Media constrained to image/* mime types, with a recommended
//       max size and aspect ratio. Define once UI requirements firm up.
Icon = Media

// --- Time ---

// TODO: Timestamp — absolute point in time. Probably milliseconds since
//       Unix epoch as SignedInteger(8); confirm whether ns resolution is
//       needed for any modality, and how unsynchronised devices behave.
Timestamp = SignedInteger(8)

// TODO: TimeInterval — a span of time. Probably milliseconds as Long.
//       Confirm range/resolution requirements (uptime can be years).
TimeInterval = Long

// Same wire shape as TimeInterval; named for use in subscription rate fields.
Duration = TimeInterval

// --- Physical quantities ---

// TODO: confirm units, range, resolution for the quantities below. They are
//       used by scan.health and may need wrapping in a Measurement-like
//       structure that carries the unit explicitly.

// Percentage 0–100, one byte.
Percent = UnsignedInteger(1, constraint = Range(min = 0, max = 100))

// TODO: Voltage — volts. FloatingPoint(4)?
Voltage = FloatingPoint(4)

// TODO: Current — amperes. FloatingPoint(4)?
Current = FloatingPoint(4)

// TODO: Temperature — degrees Celsius. FloatingPoint(4)?
Temperature = FloatingPoint(4)

// TODO: Information — bytes of memory/storage. UnsignedInteger(8)?
Information = UnsignedInteger(8)

// --- Limit-bracketed measurement ---

// A measured value carried alongside the soft and hard operating bounds the
// device declares for it. Used throughout scan.health.
HardSoftLimited(t: Type) {
   value:    t,
   softMin:  t,
   softMax:  t,
   hardMin:  t,
   hardMax:  t
}
```

## Bring-Up

This chapter specifies how a device in factory state is brought onto a network before any
of the protocol layers above apply. Bring-up is not part of the four-layer protocol stack;
it is a prerequisite to it, specified here because interoperability with a standard SCAN
administrative application requires a shared onboarding surface.

The bring-up surface carries only the information a device needs in order to attach to a
network (for WiFi: an SSID and passphrase; optionally a static IP configuration). Once the
device is on the network, the administrative application discovers it via the normal
Internet Layer advertisement mechanism and enrolls it via `scan.enroll` over a standard
Logical Layer connection. Everything specific to SCAN — Noise, framing, modalities —
happens on the real network, not on the bring-up surface.

### Requirements

Every SCAN device MUST provide at least one factory-state mechanism by which an
administrative application can deliver network credentials. In particular:

* Ethernet-capable devices have no additional bring-up requirement. A physical Ethernet
  connection combined with zero-configuration IP acquisition (§Network Acquisition) is
  sufficient.
* WiFi-capable devices MUST support at least one of:
  * **Soft-AP bring-up** — the device acts as an open WiFi access point until a valid
    bring-up blob has been delivered.
  * **BLE bring-up** — the device advertises a GATT service until a valid bring-up blob
    has been delivered.
* A device MAY support multiple bring-up mechanisms; the administrative application then
  chooses whichever works best on its platform. Soft-AP and BLE bring-up use separate
  radios and can run concurrently without conflict.

### Factory QR Code

Every SCAN device MUST be delivered with a factory QR code identifying the device and
carrying the enrollment PSK needed to administer it. The QR encodes a URI whose body is a
base32-encoded binary payload (RFC 4648 without padding, case-insensitive):

    SCAN:<base32-payload>

The binary payload is:

```
BringUpCapability = Ethernet | SoftAP | BLE

QRPayload {
   version:         UnsignedInteger(1),      // 0x01 for this revision
   capabilities:    Set(BringUpCapability),
   peerAddress:     PeerAddress,
   enrollmentPsk:   PSK
}
```

The `capabilities` field declares which bring-up channels the device offers. 

### Bring-Up Blob

Both Soft-AP and BLE bring-up channels carry the same payload: a *bring-up blob*
describing the WiFi network the device should attach to.

```
BringUpBlob {
   ssid:           String,
   passphrase:     Option(String)
}
```

The `passphrase` field is absent for open WiFi networks. No IP configuration is carried
in the blob: once the device has joined the WiFi network, §Network Acquisition handles
address assignment (DHCP, SLAAC, or link-local) and the device's subsequent
advertisements carry its resulting `(IP, port)` so the administrative application can
find it without any pre-arranged address. 

The blob MUST be authenticated and encrypted using a key derived from the device's
enrollment PSK, so that an attacker in physical proximity cannot push bogus network
credentials and cause the device to join an unintended network. The wire format is:

```
EncryptedBringUpBlob {
   nonce:          Array(Byte, size = 12),
   ciphertext:     Array(Byte),    // AES-256-GCM encrypted BringUpBlob
   tag:            Array(Byte, size = 16) // AES-256-GCM authentication tag
}
```

The framing reuses the AEAD already mandated by the Logical Layer Noise suite
(`AESGCM_SHA256`), so a constrained device does not need a second cryptographic
primitive just for bring-up:

* **AEAD**: AES-256-GCM.
* **Key**: `HKDF-SHA256(ikm = enrollmentPsk, salt = empty, info = "SCAN bring-up v1")`,
  taking the first 32 bytes of output.
* **Nonce**: 12 bytes, chosen uniformly at random by the administrative application and
  carried in the clear alongside the ciphertext. A fresh nonce MUST be used for every
  delivery attempt. A collision is statistically negligible at any realistic onboarding
  volume.
* **Associated data**: the device's 32-byte `peerAddress`. This binds the ciphertext to
  a specific device and prevents an attacker from replaying a captured blob against a
  different device.

A device that fails to decrypt or authenticate a received blob MUST discard it silently
(without consuming or locking anything on the bring-up channel) and remain available for
another attempt.

### Soft-AP Bring-Up

A device advertising Soft-AP bring-up MUST:

* Act as an open (unencrypted) WiFi access point. Confidentiality of the bring-up blob is
  provided by its own encryption, not by WiFi-level security.
* Use an SSID of the form `SCAN-XXXXXXXX`, where `XXXXXXXX` is the uppercase hexadecimal
  encoding of the first four bytes of the device's `peerAddress`. This lets the
  administrative application match the scanned QR to the broadcasting AP when multiple
  devices are nearby.
* Take the fixed address `192.168.4.1/24` on the Soft-AP interface. This follows the
  de-facto convention widely used by embedded WiFi stacks and avoids clashes with the
  most common home-network subnets (`192.168.0.0/24`, `192.168.1.0/24`).
* Run a DHCPv4 server on the Soft-AP interface that leases addresses inside the same
  `192.168.4.0/24` subnet to associating clients. Pool size, lease time, and any
  additional DHCP options are implementation-defined; neither iOS's
  `NEHotspotConfiguration` nor Android's `WifiNetworkSpecifier` lets an admin app assign
  a static IP to the phone side, so a DHCP-less Soft-AP would be unreachable regardless
  of the device's own address.
* Accept incoming TCP connections on port 11372 (§IANA Allocations) at `192.168.4.1`.
* Accept one `EncryptedBringUpBlob` over the connection. Regardless of outcome,
  the device closes the connection afterwards. If the data was valid, it closes
  the Soft-AP as well and proceeds with the proper network connect.

### BLE Bring-Up

A device advertising BLE bring-up MUST:

* Advertise a GATT server carrying the SCAN bring-up service UUID
  `5343414E-0000-4000-A000-000000000001`. The leading bytes `53 43 41 4E` spell `SCAN` in
  ASCII, making the service identifiable on BLE sniffer captures without a lookup table.
* Include the Local Name `SCXXXXXX` in its BLE advertisement, where `XXXXXX` is
  the uppercase hexadecimal encoding of the first three bytes of the `peerAddress`.
  The `SC` prefix is a sniffer hint matching the ASCII prefix of the service UUID;
  the hex suffix lets the administrative application correlate the advertisement
  with a specific device's QR code. The total advertising payload (Flags +
  128-bit Service UUID + Local Name) fits within the 31-byte legacy BLE
  advertisement limit.
* Expose within the service a single writable characteristic (the *bring-up blob
  characteristic*) at UUID `5343414E-0000-4000-A000-000000000002` that accepts the
  encrypted blob. The characteristic has the `Write` and `Extended Properties` GATT
  properties and supports long writes via ATT Prepared Write + Execute Write.
* Accept the blob as a GATT Long Write sequence (ATT Prepared Write + Execute Write)
  regardless of the negotiated ATT MTU. 

### Post-Bring-Up Transition

On successful decryption and validation of a bring-up blob, the device:

1. Applies the network configuration carried by the blob.
2. Tears down all active bring-up channels (Soft-AP, BLE).
3. Proceeds with §Network Acquisition on the configured network.
4. Joins the multicast group and emits advertisements per §Presence Messages.

The administrative application, having recorded the device's `peerAddress` from the QR,
waits for that `peerAddress` to appear in an advertisement on the real network. On seeing
it, the administrative application opens a TCP connection and proceeds with the Logical
Layer handshake using the enrollment PSK from the QR. From that point, enrollment and
configuration proceed identically regardless of which bring-up channel delivered the
blob.

If application of the blob fails (bad passphrase, network unreachable, DHCP timeout), the
device MUST revert to the bring-up state: re-enable its bring-up channels and wait for a
new blob. This protects against typos and temporary network outages without requiring a
factory reset.

A device MAY stop advertising its bring-up channels after a prolonged period of
inactivity (Recommended: 10 minutes since power-on with no bring-up connection or write)
in order to reduce the long-lived attack surface of an always-open Soft-AP or an
always-advertising BLE radio. A device that implements such a timeout MUST re-arm the
bring-up channels on power cycle and SHOULD also re-arm them in response to a physical
reset button if one is available.

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

### Modality Instance Groups and Shared State

This section describes how state is shared between connected modality instances.
The protocol handles the mechanism transparently -- end users do not need to
reason about it directly -- but understanding the model helps when designing
more complex wirings.

**Modality, instance, group.**

A *modality* is a single semantic unit defined on a device. "On/off of a light",
"position of a switch", "current temperature" are all modalities. Each modality
has a type and a default behaviour.

A *modality instance* is a concrete occurrence of a modality on a device.
Modalities may have many instances, distinguished by a *key*. A power strip
with eight independent channels defines one modality with eight instances
(keyed by channel number); a simple light defines one modality with a single
instance.

A *modality instance group* is the set of modality instances that have been
wired together. Wiring is how the user declares that instances are connected
and should behave as a shared logical entity. A group has no central component;
it is simply the transitive closure of instances reachable through wiring.

**Shared state.**

When instances are wired together, they share one logical state. Each
participant agrees on whose write is currently in effect and observes the
associated value through its subscriptions. Any participant may write a new
value; every other participant then observes the change. The group does not
require every member to store the value; values flow on the wire when they
are needed, so participants with limited memory -- and modalities whose value
is very large or streaming -- still take part fully.

The user-observable consequences are:

* Any button in a group can change the group's on/off state, and every light
in the group follows.
* Adding a third button or a tenth light requires no central configuration;
the user just wires the new instance into the group.
* Losing connectivity to some members does not disable the rest. Each
remaining connection keeps working at full semantic correctness.
* A device that restarts and rejoins the group recovers the current group
state automatically from the other participants. No persistent storage of the
shared state is required on the device itself.

**Last-Writer-Wins.**

Conflicting updates in a group are resolved by a rule called *Last-Writer-Wins*
(LWW): when two participants write at nearly the same time, the group eventually
settles on whichever write is considered most recent, with a deterministic
tie-break so that every participant ends at the same value.

The mechanism uses a per-instance monotonically increasing *counter* and
records which peer produced the current value as the *writer*. These are the
`counter` and `writer` fields carried on `State` messages. A peer's counter
advances on every local write and whenever it observes a higher counter from
anyone else, so newer writes always dominate older ones regardless of message
order, duplication, reconnections, or device restarts.

Users do not have to look at or set these fields. What matters is the
observable behaviour: the group state is eventually consistent, the system
tolerates partial failures gracefully, and any single device can be restarted,
reconnected, or replaced without disrupting the rest of the group.

### Network Backpressure

SCAN uses two distinct, complementary backpressure mechanisms.

**Modality-level backpressure** is an explicit application-level mechanism using the
`Busy` and `Ready` shared messages of the Modalities Layer. It is scoped to a single
modality instance — or to a whole `scan.vmods` cluster, which is serialized as one
unit — and enforces message-level atomicity: while a receiver is processing one
incoming `State`, any further `State` for the same scope is rejected with `Busy`;
the sender keeps its authoritative copy (already required), coalesces further local
updates into it, and re-sends on receipt of `Ready`. In the common case of a single
writer per modality instance this mechanism is entirely silent — no `Busy` or
`Ready` is ever exchanged, and no per-chunk acknowledgement traffic is paid for by
subscriptions that do not contend.

**Network-level backpressure** is the ambient TCP-level mechanism. If a consumer
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

The two mechanisms are orthogonal. Modality-level backpressure handles per-modality
receiver-processing contention precisely, at zero cost when there is no contention.
Network-level backpressure handles transport-wide congestion coarsely, across all
modalities that share a connection, and must always be honoured regardless.

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

### Redundancy

Deployments may want a device to stay reachable when one network path goes away:
a fixed device with wired Ethernet *and* WiFi, a mobile device roaming between
access points, or an installation with two physically separate cable runs. SCAN
supports a cheap, practical form of redundancy out of the box, without any
additional framing, without a standby negotiation, and without splitting or
duplicating application traffic.

The mechanism lives entirely in §Internet Layer and rests on three facts already
established there:

1. A multi-homed device advertises on every interface it is active on, so each of
   its addresses is independently observable by peers.
2. Receivers keep an ordered cache of recent `(IP, port)` entries per peer
   (§Addressing), head-first by most-recent-advertisement, bounded to a small
   cap.
3. Every `Advertisement` carries a per-emitter `generation` counter that is shared
   across all frames of a single advertisement event — every interface, every copy
   of a birth burst, every frame of a multi-frame split.

From these three the failover behavior follows mechanically. When the current TCP
connection closes unexpectedly the initiator walks its cache head first, attempting
each cached address once before entering backoff. If the head address is dead
(pulled Ethernet cable, downed AP) the next cached address is tried immediately;
the switch-over cost is one failed `connect()` plus one fresh Noise handshake (optional),
typically in the sub-second range on a LAN. Nothing below the Internet Layer
changes, no subscription state is lost, and no application code needs to react.

The `generation` counter handles cache hygiene without any explicit "unadvertise"
frame. Because an advertisement event is emitted on every active interface with the
same counter, an entry that is absent from two consecutive generations from its
emitter is known to be no longer reachable through that path and is evicted. One
missed generation is tolerated to absorb ordinary multicast packet loss. A stale
entry therefore disappears within two advertisement events of the interface going
silent, rather than lingering in the cache as a dead fallback target.

**Gateways.** A gateway is itself an emitter, not a relay, for the purposes of
generation and caching. The gateway issues its own generation counter (bumped on
changes to the set of identities reachable through it, not on every behind-the-gateway
event) and maintains its own per-peer address cache on its inside face using the
inside peers' counters. This means both faces of the gateway benefit from the same
fallback behavior, and a receiver talking to the gateway does not have its cache
perturbed by churn behind it.

**Common scenarios.**

* **Wired Ethernet + WiFi on the same device.** Both interfaces advertise with the
  same generation. Receivers cache both `(IP, port)` entries. If the wired link
  drops, its TCP goes down; the next connect attempts the WiFi entry and succeeds.
  The wired entry ages out after two silent generations. When wired comes back, its
  next advertisement reinstates it at the head of the cache, and the next new
  connection from that peer uses it.
* **WiFi roaming within a single ESS (one SSID, many APs).** This is handled below
  SCAN, at the 802.11 layer: the supplicant re-associates to a different BSSID
  without the device's IP changing. SCAN sees at worst a brief TCP hiccup handled
  by the existing reconnect rules and does not need a second cached address.
* **Two separate wired paths (disjoint cable runs).** Each path appears as its own
  interface with its own IP. Same behavior as Ethernet + WiFi: both cached, failover
  on TCP close.
* **Gateway plus local segment.** If the same logical peer is reachable both
  locally and through a gateway, both addresses coexist in the cache (one keyed to
  the local emitter, one to the gateway emitter). Either can serve as a fallback
  for the other, subject to the per-logical-connection rules in §Gateway-based
  Configuration.

**What this design does not provide.**

* *Zero-failover-time redundancy.* Failover is bounded by how quickly TCP notices
  the break: immediate on a clean RST, seconds on a silent link drop. Installations
  that require sub-millisecond, hitless failover should use IEEE 802.1CB (§Mixed
  and Redundant Topologies) at the Ethernet layer, which duplicates frames across
  disjoint paths with receiver-side deduplication. 802.1CB and SCAN's per-peer
  cache are complementary and can be used together.
* *Parallel use of both paths.* Only one path carries traffic at a time. Active
  deduplication across two live TCPs is out of scope.
* *Fallback across different WiFi SSIDs on a single-radio device.* SCAN does not
  carry multiple WiFi credential sets. Installations that need this should either
  rely on ESS roaming (which is sufficient for most mesh and enterprise WiFi) or
  handle it at the supplicant level as a firmware feature below SCAN. In practice
  few IoT protocols carry multi-SSID fallback as a protocol concept; it is
  deliberately left outside the SCAN spec to keep bring-up and `scan.netconfig`
  minimal.

