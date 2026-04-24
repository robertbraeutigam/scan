# SCAN Protocol — Open Design Questions

## Type System

- **Tier −1.** Introduce Tier −1: types with fixed length, typed with variable-length integer indicating size — makes it easier to skip and thus extendable. Alternatively: make value type code contain size on 3 bits, exact semantics on lower bits.

- **Locale.** Do we really need Locale?

- **Usage type validation.** Do we really need to validate usage type definitions? That would require including all definitions on the device. If not required, a device would only need the definitions it actually uses.

- **De-duplicating repeated values.** Values should de-duplicate repeated values. For example, `PeerAddress` for Text should be listed only once and then referenced.

- **Array support.** Do we need to deal with arrays? (Multiple video outputs, multiple days of measurement, multiple log lines, etc.)

- **Data packet structure.** The data packet should not have to explicitly repeat data element ID and length.
  - Tier 1 should also mention what Tier 0 it is, so it can be skipped without explicit length.
  - Clarify what "parameters" are.

- **Metrics.** We want to enable generic displays of data. So a data element would need to carry some meta-data, similar to TS DBs.
  - Things whether a piece of data is a Gauge, Counter, Histogram, etc. 

---

## Wiring

- **Remove wiring from Application Layer.** Devices have data and actions (protocol); wiring just creates virtual data and actions on top but does not influence the application layer itself.

- **Wiring stability.** Wiring should be stable for a given data/action. If a device's capabilities change but the same data is still present, the wiring should continue to work.
  - Example: how to submit all cell voltages and temperatures? Do we want permanent identifiers for things in the data packet (not just indices) so wiring survives added data elements?

- **Wiring repository.** Define a repository of how devices can be connected and offer it to users. Also: remember where wiring came from and upgrade it if it changes.

- **Wiring marketplace knobs** (for a future community repository):
  - Signature-based matching — pick transforms by what the source has (field names/paths, units, ranges, known cluster/PGN IDs, or a small probe). Avoid rigid "device classes."
  - Trust tiers — let admins choose sources: Official, Verified community, Local/unverified. For safety-critical modalities (locks, engines), default to stricter tiers.
  - Versioning & constraints — each mapping declares supported firmware ranges, transport assumptions, units, and test vectors; warn if the live device doesn't match.
  - Dry-run preview — before applying, run the transform on a snapshot and show "input → output."
  - Ratings + telemetry (opt-in, anonymized) — surface "works on 120 installs / 3 reports" to guide choice.
  - Local overlays — allow admins to tweak a community mapping with a tiny local patch (e.g. invert, clamp) without forking the whole thing.
  - Safety flags — mark transforms that touch actuation vs telemetry; gate high-risk writes behind explicit confirmation/policies.

- **Dropped/undefined data in wiring.** It is possible to "drop" certain data (e.g. a switch with "undefined" status until it determines what the light is doing). Address this in wiring.

- **Wiring failure.** What happens if a device is unable to execute its wiring?

- **Derived modality with current state.** Can you create a derived modality that takes the current state into account for a "successful turn on"? Is this needed?

- **Default state for modalities (cold boot).** A modality needs a defined value to advertise before its first measurement or write — most acutely for `scan.vmods` cluster members, whose transformations need inputs from the moment a member is subscribed, but the question is general (a push button has no physically-encoded "current" position either). Without a defined default, a freshly-booted cluster has no inputs for its first transformation, and a freshly-subscribed peer cannot get a meaningful first `State` message before something happens. Options to consider: a deterministic type-level default in `TYPES.md` (per built-in / aggregate), a per-modality `defaultOutput` override on the `Modality` struct, persistence in non-volatile memory, or "no state until first write" with an explicit rule for `Subscribe` semantics in that window. The previously-considered NVRAM route is undesirable because of the storage cost on constrained devices.

- **Cross-cluster write contention detection.** When two `scan.vmods` clusters on different devices both write into the same target modality, LWW arbitrates each individual write, but the two clusters can oscillate by repeatedly overriding each other. The host should detect this case (its own writes on a cluster member being clobbered by another peer at a high rate) and surface it through `scan.logs` and per-modality status in `scan.health`. Define the threshold and the reporting shape. Prevention proper belongs to admin tooling that has the full wiring graph across devices.

---

## Application Layer & Modalities

- **Auditing / transparency.** Make it transparent what happens.
  - Introduce tracing (similar to observability)?
  - If "intent" chains are tracked, loops/recursions can be detected.
  - Counter-argument: just don't react automatically to changes.

- **Firmware rollback.** Support controlled rollback in firmware update.

- **Device capabilities advertisement.** Devices should report on `OPTION`: what capabilities they have, what message size they support, how many connections they can handle, etc.

- **Log range queries.** How to query logs (range queries)?
  - Workaround: have manual controls for "last line", "last 5 minutes", "all", etc. Or stream the logs/output/errors.

- **Device memory full.** What if device memory is full and it cannot upload wiring or remember more data?

- **Predefined types / semantics.** Define predefined types and semantics for:
  - Metadata (device name, serial number, etc.)
  - Firmware update
  - Customizable data: name, location, floor, etc.
  - Metering domain

- **i18n.** Devices should not do i18n, but think about how display devices should handle it.
  - `Message = Struct(origin: PeerAddress, messageId: String, parameters: Array(???))`
  - A modality to fetch translations.

- **Command confirmation / ACK.** Do commands need confirmation?
  - If based on data: data will be repeated (safe). But data coming back may not be the same + long-running jobs + firmware update.
  - If not based on data: look at events whether the change is reflected.
  - ACK all intents? Yes, but slowly (batch acknowledgements with increasing IDs).

- **Explicit ACK / error handling.** Do we need explicit ACK or error handling?
  - Ideal: every data message gets eventually delivered with no explicit ACK.

- **Reconfiguring a button.** How to handle reconfiguring a button from 2 states to 3 states?

- **Button/control input ranges.** Ranges for control inputs (0–100%, etc.).
  - Or: expressions as validations (problem: how to detect two controls accepting the same range?).

- **Network stats.** Make it per interface, reflecting netconfig

---

## State & Consistency

- **Modality state consistency.** How do you make modality "states" consistent? Do we want an authoritative source?

- **Recall / restore.** Store the state of a system ("Recall")?
  - If restore sets the recalled state, how do you allow others to set it to something else?
  - How to "prevent" it from reverting to a restored state?
  - Configuration: store everything except things set by the admin app?

- **Scheduler use case.** A scheduler that in the morning starts coffee, opens blinds, etc. — how would you program it? Use restore state?

---

## QoS & Priority

- **Priority among packets.** When video is using the network but is not high priority, you don't want engine-control packets to stall. Data flows don't know about each other, so the strategy must be self-contained.
  - Only an issue in case of congestion affecting everybody.
  - Introduce a backoff coefficient configurable on nodes?

- **Rate limiting correctness.** Does rate limiting work when:
  - A device uses data at 1/sec but is connected to a manual switch turned on then off within one second — it will not use the most recent information.
  - Resolution: straight chains — push rate limit further upstream; that triggers the downstream. (Some difficulty when multiple subscribers are interested; must push to the common denominator.)
  - For joins: use a push model on other paths by submitting a subscription with unlimited repeat.

---

## Device Lifecycle & Topology

- **Sleep / wake devices.** Revise how to support devices that wake for only a few milliseconds (e.g. BLE). We can't require devices to stay up for seconds. How to communicate with such devices?
  - Advertisement is optional, so no need to stay awake.
  - Device can issue a command instead if it can't stay awake.

- **Pseudo-devices / deferred queue.** There can be a device that, upon seeing an advertisement, generates a pseudo-device for time-deferred communication (a Queue). Any operations (commands) on the pseudo-device are deferred until the actual device is seen.

- **Subscription with last known state.** Subscription may optionally contain "last known state", so the peer does not need to retransmit data.

- **Link check to command.** Somehow link a check to a command so it can be verified:
  - Link a firmware update to the version number in some data packet?
  - Link a light-switch command to the actual light data packet?
  - Is this what the modality ID is for?

- **Require "reset" button.** The device joined some network, has some data it can't deal with, changed hands and is in unknown state. We need a way for the device to be reset to
  factory state, without contacting it. Or de we require that the device maybe reset by other means (BLE interface for 5 minutes after restart, etc., also in the QR code?)

---

## Resolved / Sort-of Decided

- **Type system approach.** Use TYPES — don't do a tiered semantics system. Define primitive types and some advanced types (Measurement, Enums, etc.). Don't define semantics; just let things be "tagged" (GPS position, main power, depth, etc.). Just have Types.

- **Unit system.** Ditch the elaborate unit system — just have e.g. "Kg".

- **"Bound" sync network vs. commands.** Use modality groups instead of commands.

- **Long-running operations & optimistic locking.** Make these user-space. Define state as its own type, with no relation to the original type (can be transformed for backwards compatibility, e.g. remove the long-running wrapper when wiring to a simple switch).

- **Intent / authoritative device.** Intent tracking on the authoritative device — there is always exactly one; no need to track long-running jobs. Node guarantees eventual feedback. Also: batch acknowledgements.

- **Multiple instances of a modality (e.g. video feeds).** Use a key type — a normal light has `Unit` as key type (only one instance), video can have `String` (video stream name) as key type.

- **Transformation placement.** Do the transformation on the sending side (avoids sending when not needed). Cannot support multiple inputs that way — this is an accepted trade-off.

- **Message size.** Max 1200 B per frame (sized to fit IPv6 minimum MTU minus TCP/IP headers, so each frame is one IP packet). Just require this — nothing to communicate.

- **Resource limits.** No capability advertisement on `OPTION`. Max frame size is already a universal MUST (1200 B), larger payloads are handled via streaming, and `scan.health` already carries memory, CPU, network-error, latency, and per-modality connection counts — there is nothing left to advertise. Overload is handled by dropping TCP: a responder under resource pressure closes the TCP connection, and the initiator reconnects under the normal exponential backoff. Persistent overload surfaces through `scan.health` counters rather than a dedicated signal.

- **BLE fragmentation.** Mandate GATT Long Write (ATT Prepared Write + Execute Write) unconditionally for the bring-up blob characteristic, regardless of negotiated ATT MTU. This keeps the device independent of MTU negotiation (default 23 B, not raised by every stack), gives both sides a single code path, and avoids a SCAN-specific chunking header. Most IoT provisioning stacks (Matter BTP, ESP-IDF, Improv, Nordic UART, Bluetooth Mesh proxy) build their own framing because they need bidirectional comms; SCAN's bring-up is strictly unidirectional (push blob, reconnect on the real network), so Long Write is the natural fit.
