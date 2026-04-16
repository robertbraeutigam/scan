# SCAN Protocol Comparison with Existing IoT Protocols

This document compares SCAN against the major IoT protocols, assuming SCAN's draft features are completed as described.

## Protocols Compared

Nine established protocols across consumer, industrial, and enterprise IoT:

| Protocol | Domain | Age | Scale |
|----------|--------|-----|-------|
| **MQTT** | General IoT | 1999 | De-facto standard, billions of connections |
| **CoAP** | Constrained devices | 2014 (RFC) | IETF standard, wide academic/industrial use |
| **LwM2M** | Device management | 2017 (v1.0) | Carrier-adopted (AT&T, Deutsche Telekom) |
| **Matter** | Smart home | 2022 | 10,400+ certified products |
| **Zigbee** | Smart home / building | 2004 | 1 billion+ deployed devices |
| **Z-Wave** | Smart home | 2001 | 100M+ devices, 4,500+ products |
| **KNX** | Building automation | 1990 | ISO standard, 500+ manufacturers |
| **DDS** | Real-time / industrial | 2004 | Military, autonomous vehicles, medical |
| **OPC UA** | Industrial automation | 2008 | 60+ companion specs, industrial standard |
| **AMQP** | Enterprise messaging | 2012 (1.0) | ISO standard, Azure/cloud backbone |

## 1. Architecture & Topology

| Protocol | Model | Central Component Required? | Discovery |
|----------|-------|-----------------------------|-----------|
| **SCAN** | Peer-to-peer | **None** | Built-in (multicast advertisements) |
| MQTT | Broker-based pub/sub | **Broker** | None (convention-dependent: Homie, Sparkplug) |
| CoAP | Client-server REST | None, but asymmetric | `/.well-known/core` + mDNS multicast |
| LwM2M | Client-server | **LwM2M Server + Bootstrap Server** | Registration with server |
| Matter | Peer-to-peer (with PKI) | Commissioner for onboarding | mDNS + BLE |
| Zigbee | Coordinator-centric mesh | **Coordinator / Trust Center** | Channel scanning + coordinator admission |
| Z-Wave | Hub-centric mesh | **Controller hub** | Inclusion process via hub |
| KNX | Bus / hierarchical | None at runtime, **ETS tool** for config | None at runtime |
| DDS | Peer-to-peer pub/sub | **None** | Automatic (RTPS multicast) |
| OPC UA | Client-server (+ PubSub) | Server (or broker for PubSub) | mDNS, LDS, GDS (multi-tier) |
| AMQP | Topology-agnostic (usually broker) | Broker in practice | **None** |

SCAN shares the fully decentralized, no-single-point-of-failure topology with DDS. This is a strong differentiator against all broker/hub/coordinator-dependent protocols (MQTT, LwM2M, Zigbee, Z-Wave). Matter is architecturally similar but requires PKI infrastructure and commissioners. KNX is decentralized at runtime but completely dependent on ETS tooling for configuration.

SCAN's built-in discovery via periodic identity advertisements is simpler than DDS's RTPS discovery (which propagates type and QoS metadata) but more integrated than MQTT (none) or AMQP (none).

## 2. Security

This is one of SCAN's strongest areas.

| Protocol | Encryption | End-to-End? | Forward Secrecy | Auth Model | PKI Required? |
|----------|-----------|-------------|-----------------|------------|---------------|
| **SCAN** | Noise_KKpsk1_25519_AESGCM_SHA256 | **Yes** (native) | **Yes** (per-session) | PSK roles | **No** |
| MQTT | TLS (transport) | **No** (broker reads all) | TLS only (hop) | Username/password, X.509 | Yes (for TLS) |
| CoAP | DTLS (hop-by-hop); OSCORE (E2E extension) | Via OSCORE only | EDHOC extension only | PSK, RPK, or X.509 | Depends on mode |
| LwM2M | DTLS/TLS; OSCORE (v1.1) | Via OSCORE only | EDHOC or TLS 1.3 | PSK, RPK, or X.509 | Depends on mode |
| Matter | AES-CCM, CASE (SIGMA/ECDHE) | **Yes** | **Yes** (CASE sessions) | X.509 certificates (PAA/PAI/DAC/NOC) | **Yes** (full hierarchy) |
| Zigbee | AES-128-CCM* | Network key = hop-by-hop; link keys = E2E | **No** | Trust Center + install codes | No |
| Z-Wave | AES-128 (S2) | Yes (within security class) | **No** (static network keys) | ECDH inclusion + DSK | No |
| KNX | AES-128-CCM (KNX Secure) | KNX Data Secure = E2E | **No** | Factory Device Setup Keys | No |
| DDS | AES-GCM/CCM | **Yes** | **Yes** (per-session DH) | X.509 certificates | **Yes** (full PKI) |
| OPC UA | AES-256 (UA-SecureConversation) | **Yes** | Depends on policy | X.509 application + user certs | **Yes** (complex) |
| AMQP | TLS | **No** (broker reads all) | TLS only | SASL (various mechanisms) | Yes (for TLS) |

SCAN achieves the security trifecta -- end-to-end encryption, forward secrecy, and strong authentication -- without requiring PKI. Only DDS and OPC UA match the first two properties, but both require full X.509 certificate infrastructure. Matter has strong security but also demands a deep certificate hierarchy (PAA -> PAI -> DAC -> NOC) that is antithetical to DIY.

The Noise framework choice is validated by the IETF's own direction: EDHOC (RFC 9528), designed for CoAP/OSCORE, is explicitly inspired by Noise patterns. SCAN adopts Noise natively rather than as a bolt-on extension.

The PSK-as-role authorization model is unique and elegant -- it avoids ACLs (OPC UA, Matter, LwM2M), Trust Centers (Zigbee), and centralized broker auth (MQTT, AMQP) while providing per-device, per-capability granularity.

## 3. Transport & Framing

| Protocol | Transport | Max Message | Fragmentation | Multiplexing | Streaming |
|----------|-----------|-------------|---------------|--------------|-----------|
| **SCAN** | TCP/IP + UDP multicast | **Unlimited** (chunked) | Built-in (chunk-based, per-chunk encrypted) | Yes (multiple logical connections per TCP) | **Yes** (native) |
| MQTT | TCP | 256 MB | **No** | No | No |
| CoAP | UDP (+ TCP ext.) | ~1 KB (UDP) | Block-wise transfer (finite) | Token-based | No |
| LwM2M | CoAP transports | ~1 KB (UDP) | Inherited from CoAP | Limited | No |
| Matter | UDP/MRP or TCP | 1280 bytes (UDP) | Not at Matter layer | Exchange IDs | No (WebRTC for cameras) |
| Zigbee | 802.15.4 radio | ~70 bytes payload | APS fragmentation (rare) | Endpoints (up to 240) | No |
| Z-Wave | Sub-1GHz radio | 54 bytes payload | Firmware-only | No | No |
| KNX | TP bus / IP | 16 bytes (TP) | No | No | No |
| DDS | UDP/TCP/shared mem | Unlimited | Built-in (DATA_FRAG) | Topics | Yes (continuous pub) |
| OPC UA | TCP/HTTPS/WS | Negotiable | Chunk-based (signed/encrypted) | No (Client/Server) | No |
| AMQP | TCP | Negotiable | Multi-frame transfer | Sessions + Links (3-level) | Approximated |

SCAN's transport layer is well-designed. The chunk-based fragmentation with per-chunk encryption and validation is superior to MQTT (no fragmentation), CoAP (finite block transfers only), and most radio protocols (tiny frames). The multiplexing of multiple logical connections over a single TCP connection is comparable to AMQP's session/link model.

True native streaming (potentially infinite messages) is a capability only SCAN and DDS offer. This matters for video surveillance (marine, building security), continuous sensor data (automotive), and firmware transfers.

The message mixing capability (interjecting urgent messages during a long stream) is unique to SCAN and addresses a real problem that no other protocol solves at the transport layer.

## 4. Data Model & Type System

| Protocol | Model | Self-Describing? | Type System | Dynamic Adaptation |
|----------|-------|------------------|-------------|--------------------|
| **SCAN** | Modalities (input/output typed) | **Yes** (on connect) | Custom (structs, unions, arrays, streams, constraints, transforms) | **Virtual modalities + transformation language** |
| MQTT | None (opaque bytes) | No | None | No |
| CoAP | REST resources | Partial (CoRE Link Format) | None (media types only) | No |
| LwM2M | Object/Instance/Resource tree | Yes (numeric IDs) | Fixed (8 primitive types) | No |
| Matter | Endpoint/Cluster/Attribute/Command | Yes (Descriptor Cluster) | Fixed (spec-defined) | No |
| Zigbee | Endpoint/Cluster/Attribute | Yes (ZDO queries) | Fixed (ZCL types) | No |
| Z-Wave | Command Classes | Partial (interview) | Fixed per command class | No |
| KNX | Group addresses + Datapoint Types | **No** (requires ETS project) | Fixed DPTs | No |
| DDS | Topics with IDL types | **Yes** (XTypes) | Rich IDL (structs, unions, enums, sequences, inheritance, evolution) | No |
| OPC UA | Node graph (Object/Variable/Method) | **Yes** (browsable address space) | Rich OO hierarchy + 60 companion specs | No |
| AMQP | None (message envelopes) | No | Self-describing encoding (primitives) | No |

SCAN's modality model occupies a unique middle ground. It's richer than MQTT/CoAP/AMQP (which have no data model at all) and more flexible than the rigid catalog approaches of Matter/Zigbee/Z-Wave/LwM2M/KNX (which require central standardization for new device types).

The **virtual modalities with compiled transformation programs** are SCAN's most innovative feature. No other protocol offers protocol-level dynamic type adaptation. Every other protocol either:
- Requires all vendors to agree on formats in advance (Matter, Zigbee, KNX, LwM2M, OPC UA)
- Requires external middleware for translation (MQTT + Node-RED, cloud rules engines)
- Says nothing about interoperability (CoAP, AMQP, DDS)

SCAN's approach -- "interoperability at the edge" via user-deployable transformations -- is philosophically distinct and could be a major advantage for diverse ecosystems.

However, DDS's IDL/XTypes and OPC UA's information model are *richer* type systems. OPC UA in particular, with its browsable object graph, 60+ companion specs, and full OO inheritance, is the gold standard for semantic device description. SCAN's type system is more ambitious than most but still has significant sections marked as TODO.

## 5. Interoperability

| Protocol | Approach | Certification Required? | Handles Novel Devices? |
|----------|----------|------------------------|----------------------|
| **SCAN** | Dynamic transformations at deployment | **No** | **Yes** (via virtual modalities) |
| MQTT | External conventions (Sparkplug, Homie) | No | Requires new convention |
| CoAP | None at protocol level | No | N/A |
| LwM2M | OMA Object Registry (IPSO Smart Objects) | OMA TestFest (voluntary) | Requires registration |
| Matter | Standardized clusters | **Yes** ($7K/yr + test fees) | Requires spec update |
| Zigbee | ZCL standardized clusters | **Yes** ($7K/yr + lab fees) | Requires spec update |
| Z-Wave | Standardized command classes | **Yes** (~$4K/yr + $2.5K/product) | Requires Alliance approval |
| KNX | Standardized DPTs | **Yes** (manufacturer certification) | Requires KNX standardization |
| DDS | IDL type agreement + RTPS wire compat | No formal certification | Requires type agreement |
| OPC UA | Companion specifications | OPC Foundation certification (paid) | Requires companion spec |
| AMQP | None | No | N/A |

SCAN's zero-certification, zero-fee, dynamically-adaptable approach is the most DIY-friendly interoperability story by far. The tradeoff is that it pushes work to deployment time (someone must write transformations), whereas certified ecosystems provide plug-and-play at the cost of gatekeeping.

For home/marine/automotive use cases with diverse vendor devices, SCAN's model may be more practical than waiting for industry consortiums to standardize every device type.

## 6. Quality of Service & Consistency

| Protocol | Delivery Model | Backpressure | Rate Limiting | Consistency Guarantee |
|----------|---------------|--------------|---------------|-----------------------|
| **SCAN** | Resolution Principle (newest replaces old) | TCP propagation + drop policy | **Explicit** (minimumSendWait/minimumIntentWait) | **Eventual consistency** (formally argued) |
| MQTT | QoS 0/1/2 (at-most/least/exactly once) | None (broker absorbs) | None | None |
| CoAP | CON/NON (reliable/unreliable) | NSTART limit only | None | None |
| LwM2M | Inherited from CoAP | None | pmin/pmax notification attributes | None |
| Matter | MRP (reliable UDP) | None formal | Subscription intervals | None |
| Zigbee | MAC + APS ACKs | CSMA/CA implicit | Attribute reporting intervals | None |
| Z-Wave | MAC ACKs + S2 supervision | None | None | None |
| KNX | Bus-level ACKs | CSMA/CA + priority levels | None | None |
| DDS | 22+ QoS policies (RELIABLE, DEADLINE, OWNERSHIP, etc.) | RELIABLE QoS blocks writer | TIME_BASED_FILTER on reader | Ownership + durability |
| OPC UA | Subscription model (sampling/publishing intervals, deadband, queue) | Publish tokens | Per-MonitoredItem config | None formal |
| AMQP | At-most/least/exactly once (settlement) | **Credit-based** (link-level) | Session windows | Transactions |

The Resolution Principle is SCAN's most distinctive semantic contribution. It provides a single, coherent model for handling:
- Network congestion (drop obsolete, keep newest)
- Device restarts (retransmit current state)
- Partial connectivity (eventually converge)
- Producer-consumer mismatch (explicit rate negotiation)

No other protocol unifies these concerns under one principle. DDS comes closest with its rich QoS system but requires configuring 22+ policies correctly. MQTT/CoAP treat delivery and consistency as separate concerns (if they address them at all). AMQP's credit-based backpressure is more explicit than SCAN's TCP-based approach, but lacks any semantic model for what happens to stale data.

The formal eventual consistency argument in the spec (messages are either delivered, intentionally dropped for newer ones, or trigger reconnection which retransmits state) is unique among IoT protocols.

## 7. Device Management

| Protocol | Firmware Update | Health Monitoring | Logging | Backup/Restore | Provisioning |
|----------|----------------|-------------------|---------|----------------|-------------|
| **SCAN** | `scan.firmware` (mandatory) | `scan.health` (mandatory, comprehensive) | `scan.logs` (mandatory, severity-keyed streaming) | `scan.backup` (mandatory, encrypted) | `scan.enroll` + factory PSK |
| MQTT | Platform-specific | LWT + custom | Custom | None | Platform-specific |
| CoAP | Not defined | Not defined | Not defined | Not defined | Not defined |
| LwM2M | Object 5 (PUSH/PULL) | Device Object 3 | Not standard | Not standard | Bootstrap server (4 modes) |
| Matter | OTA Provider/Requestor | Diagnostics clusters | Not standard | Not standard | BLE commissioning + CASE |
| Zigbee | OTA Upgrade Cluster | Power Descriptor only | Not standard | Coordinator backup only | Install codes + Trust Center |
| Z-Wave | Firmware Update MD CC | Battery CC + polling | Not standard | Not standard | S2 inclusion + DSK/QR |
| KNX | ETS 6.4+ (new, limited) | Not standard | Not standard | Not standard | ETS tool |
| DDS | **Not defined** | LIVELINESS QoS only | Not defined | Not defined | Not defined |
| OPC UA | DI Spec (3 methods, state machines) | Server/session diagnostics | Not standard | Not standard | GDS certificate provisioning |
| AMQP | **Not defined** | **Not defined** | Not defined | Not defined | Not defined |

SCAN mandates the most comprehensive device management suite of any protocol. Every SCAN device must implement: enrollment, reset, rights management, key management, firmware update, reboot, device info, health monitoring, logging, backup/restore, network statistics, state message debugging, virtual modalities, and wiring.

Only OPC UA's DI companion spec approaches this level of standardization (and only for firmware and diagnostics). LwM2M is strong on firmware and provisioning but lacks logging, backup, and the breadth of SCAN's health modality.

The `scan.health` modality alone (temperature, voltage, current, battery, memory, CPU, network errors, network latency, per-modality health with connected/failed counts) is more comprehensive than any single protocol's health reporting.

## 8. DIY Friendliness

| Protocol | Open & Free? | Certification Fees? | Minimal Implementation Size | Central Dependencies |
|----------|-------------|---------------------|-----------------------------|---------------------|
| **SCAN** | Yes | **None** | Moderate (Noise + TCP + modalities) | **None** |
| MQTT | Yes | None | ~1 KB (but need a broker) | Broker |
| CoAP | Yes | None | ~hundreds of lines of C | None |
| LwM2M | Yes | Carrier programs exist | ~132 KB flash + 32 KB RAM | LwM2M Server |
| Matter | Open source, but... | $7K/yr CSA + per-product | Hundreds of KB (massive SDK) | PKI hierarchy |
| Zigbee | Partially open | $7K/yr CSA + lab fees | Vendor SDK on specific chips | Coordinator |
| Z-Wave | Partially open | ~$4K/yr + $2.5K/product | Silicon Labs chipsets only | Hub |
| KNX | ISO standard, but... | ETS: EUR 1,000+ | Small (TP) | ETS tool (Windows-only) |
| DDS | Yes | None | Significant (enterprise middleware) | None |
| OPC UA | Free spec access | Certification program fees | ~100 KB (open62541 Nano) | Certificate management |
| AMQP | Yes (ISO standard) | None | Too heavy for MCUs | Broker in practice |

SCAN occupies a sweet spot. Unlike MQTT and CoAP, it doesn't require you to assemble a complete solution from disparate parts (broker + convention layer + security + device management). Unlike Matter, Zigbee, Z-Wave, and KNX, it has zero gatekeeping (no membership fees, no certification, no proprietary tools, no specific chipsets).

The tradeoff is implementation complexity. A minimal SCAN device must implement: TCP/IP, the Noise handshake, frame parsing/serialization, the modalities layer, and all mandatory modalities. This is heavier than bare MQTT or CoAP but lighter than a full Matter or OPC UA stack. The key advantage is that SCAN gives you everything in one integrated package.

## 9. Network Flexibility

| Protocol | Local Network | Gateway/Proxy | Internet/WAN | NAT Traversal | Offline Resilience |
|----------|--------------|---------------|-------------|---------------|-------------------|
| **SCAN** | Multicast UDP + TCP | Gateway (transparent, E2E preserved) | Via gateway/VPN | Gateway-based | Offline/reconnect without re-handshake |
| MQTT | Via broker | MQTT-SN gateways | Native (cloud brokers) | Client-outbound (natural) | Persistent sessions + retained messages |
| CoAP | Multicast + direct | HTTP-CoAP proxy (breaks DTLS) | Difficult (UDP+NAT) | Poor (UDP) | Stateless (easy reconnect) |
| LwM2M | Local server possible | LwM2M Gateway spec | Native (carrier networks) | Client-initiated | Queue Mode + SMS wake |
| Matter | IPv6 local | Thread Border Router | Via ecosystem hubs | Ecosystem-dependent | Fully local operation |
| Zigbee | 802.15.4 mesh | Required for IP | Not native | N/A | Mesh self-healing |
| Z-Wave | Sub-1GHz mesh | Hub bridges to IP | Via hub cloud | N/A | Local mesh continues |
| KNX | TP bus / KNX-IP | IP router | VPN or tunnel | N/A | Excellent (TP bus) |
| DDS | Multicast + direct | Routing Service (WAN) | Difficult (multicast) | Poor | Peer-to-peer continues |
| OPC UA | TCP + mDNS | Edge gateways | Reverse tunnel/WS | Poor (server must be reachable) | Local server works |
| AMQP | Direct TCP | Dispatch Router | Native (cloud) | Client-outbound (natural) | Broker-dependent |

SCAN's gateway design is particularly well-thought-out. Gateways are transparent at the logical layer -- they relay frames without breaking end-to-end encryption, unlike CoAP proxies (which break DTLS) or MQTT brokers (which terminate TLS). This is architecturally similar to how OSCORE works through CoAP proxies, but SCAN builds it in natively.

The offline/reconnect model (TCP disconnect without closing logical connection, resume without re-handshaking) is pragmatic and unique. Most protocols either require full reconnection (MQTT persistent session restore, Matter CASE re-establishment) or don't address it (DDS, AMQP).

## 10. Summary Matrix

| Dimension | SCAN Strongest Against | SCAN Comparable To | SCAN Weakest Against |
|-----------|----------------------|-------------------|---------------------|
| **Architecture** | MQTT, LwM2M, Z-Wave, Zigbee (all centralized) | DDS (also P2P), Matter (P2P but PKI-heavy) | -- |
| **Security** | MQTT, Zigbee, Z-Wave, KNX, AMQP (weaker or no E2E) | DDS, Matter (strong but PKI-dependent) | -- |
| **Transport** | CoAP, Zigbee, Z-Wave, KNX (tiny frames, no streaming) | DDS (also has fragmentation + streaming), AMQP (multiplexing) | -- |
| **Data model** | MQTT, CoAP, AMQP (none) | LwM2M, Matter, Zigbee (fixed catalogs) | OPC UA (richer info model), DDS (richer type system) |
| **Interop** | MQTT, CoAP, DDS, AMQP (no mechanism) | -- | Matter, Zigbee, KNX (proven certification ecosystems) |
| **QoS/Consistency** | MQTT, CoAP, Matter, Zigbee, Z-Wave, KNX (basic or none) | -- | DDS (more QoS policies), AMQP (credit-based backpressure) |
| **Device mgmt** | MQTT, CoAP, DDS, AMQP, KNX (none/minimal) | LwM2M, Matter (partial) | OPC UA DI (comparable depth) |
| **DIY friendly** | Matter, Zigbee, Z-Wave, KNX, OPC UA (fees/certs/tools) | MQTT, CoAP, DDS (also open/free) | MQTT (simpler minimal impl), CoAP (lighter) |
| **Network** | Zigbee, Z-Wave, KNX (radio/bus only) | DDS, Matter | MQTT, AMQP (cloud-native) |
| **Ecosystem** | -- | -- | All (SCAN has no ecosystem yet) |

## Final Assessment

### Where SCAN is genuinely superior (assuming completion)

1. **Security without complexity** -- End-to-end encryption + forward secrecy + no PKI. No other protocol achieves all three. This is SCAN's clearest technical win.

2. **Integrated completeness** -- A single spec covers transport, security, data model, device management, interoperability, and consistency semantics. Every other protocol either covers a subset (MQTT = transport, AMQP = messaging, CoAP = transport) or assembles the full picture from many separate specs (LwM2M = CoAP + DTLS + OMA objects, Matter = IP + BLE + TLS + clusters).

3. **Dynamic interoperability** -- Virtual modalities with transformation programs solve a real problem no other protocol addresses at the protocol level.

4. **The Resolution Principle** -- A formally-argued eventual consistency model that unifies backpressure, rate limiting, failure recovery, and data freshness into one coherent semantic. Unique among IoT protocols.

### Where SCAN faces real challenges

1. **Zero ecosystem** -- The most mature competitor (KNX) has 30+ years of deployments. Even the youngest (Matter) has 10,400+ certified products. Technical superiority does not guarantee adoption -- Betamax, OSI, and countless superior protocols have lost to worse-but-earlier competitors.

2. **Cloud story is absent** -- Modern IoT increasingly assumes cloud connectivity. MQTT and AMQP are natively cloud-integrated. SCAN's gateway model can bridge to the internet but there's no defined cloud integration pattern.

3. **Constrained device suitability is unproven** -- The mandatory Noise handshake + TCP + all mandatory modalities may be too heavy for the smallest microcontrollers. CoAP on an 8-bit MCU with 32 KB flash is proven; SCAN on such hardware is not.

4. **The type system is unfinished** -- Five of six sections in TYPES.md are TODO. This is the foundation for the transformation language, which is the foundation for virtual modalities, which is the foundation for SCAN's interoperability story. The most innovative part of the protocol is also the most incomplete.

### Closest competitors by domain

- **Home automation**: Matter is the direct competitor. SCAN wins on simplicity and DIY-friendliness; Matter wins on ecosystem and industry backing.
- **Building automation**: KNX is the incumbent. SCAN wins on security, self-description, and tooling independence; KNX wins on decades of proven reliability.
- **Marine/automotive**: No dominant IP-based protocol exists here. SCAN's design (peer-to-peer, resilient, streaming-capable) is well-suited. DDS is the closest alternative but is too heavy and lacks device management.
- **Industrial**: OPC UA dominates. SCAN would need companion-spec-level data models to compete, which is outside its current scope.
