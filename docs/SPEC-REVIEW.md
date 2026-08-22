# Specification Review — Open Inconsistencies and Faults

A review pass over `README.md`, `TYPES.md`, `COMPARISON.md` and `TODO.md` as of commit
`4b88fb7`. Each item is an actionable checkbox. Line references are to that commit.

This is a *findings* list, not a design backlog — genuinely open design questions live in
`TODO.md`. Where an item overlaps a known TODO entry, that is noted.

---

## 1. Type System Expressiveness

The type language as specified in `TYPES.md` cannot express several shapes that `README.md`
depends on. These are the most load-bearing items in this document.

- [x] **Union-of-named-types has no syntax — fixed by flattening, no new construct.**
  `TYPES.md:24-30` defines a constructor as either a bare identifier carrying no data, or an
  inline named structure (`Some { value: T }`). There is no form meaning "this arm *is* the
  existing type `X`", so read strictly, each of `FrameContent = Control | Payload | Presence`
  (`README.md:498`), `Control` (`:526`), `Payload` (`:637`), `Presence` (`:709`),
  `InitiatorMessage` (`:1104`) and `ResponderMessage` (`:1205`) was a payload-free enum rather
  than a carrier of the named message types.

  Resolved by collapsing each of these into one flat union whose arms are inline named
  structures. `Control`, `Payload` and `Presence` no longer exist as types; they survive as
  section headings and as the grouping the prose rules quantify over ("Presence frames are not
  encrypted"), which was never type-enforced anyway. Notes on the change:

  - **The wire is unchanged or cheaper.** Per `TYPES.md:372` a union writes a `⌈log₂n⌉`-bit
    discriminator, so nested `FrameContent`→`Control` cost 2 + 2 bits and the flat 9-constructor
    union costs `⌈log₂9⌉` = 4. `InitiatorMessage` stays at 3 bits, `ResponderMessage` at 2.
    Nesting only ever wastes bits when a sub-union's arm count is not a power of two.
  - **Declaration order is now normative** for these types — it is what fixes each constructor's
    discriminator value. `README.md` states this at the `FrameContent` definition.
  - **`SingleChunkPayload = EncryptedPayload`** was an alias arm; it is now
    `SingleChunkPayload { encryptedPayload: EncryptedPayload }`. A single-constructor type writes
    a 0-bit discriminator and inlines its fields, so this is byte-identical and keeps
    `EncryptedPayload` shared with the two chunk constructors.
  - **`State`, `Busy` and `Ready` are written out in both `InitiatorMessage` and
    `ResponderMessage`.** That is textual repetition, not two types: membership is structural
    (`TYPES.md:404`, matched by constructor name and field signature), so one value is a member
    of both unions. (`README.md` already carried the `State` block twice before this change.)
  - The `Tag { field: X }` wrapper remains available for any future case that wants to keep a
    grouping type, at the same zero wire cost. The language never lacked the expressive power —
    only the notation the spec was using.

  Corollary, now moot for these types but still live as a rule: `TYPES.md:82` requires a `Set`
  element type to be "a sum of bare-identifier constructors only", so before the change
  `Set(Presence)` would have been legal under the grammar.

- [ ] **Type parameters bound by a preceding field's value are not in the language.**
  `InitiatorMessage(keyType, valueType)`, `ResponderMessage(keyType, valueType)` and
  `IndexedModalityReference(keyType)` all require the decoder to read `modalityIndex`, look up
  `modalities[index].keyType`, and only then decode the key/value. `TYPES.md:139` states the opposite: a parameter bound at definition time
  is a compile-time constant not encoded on the wire. There is no late-binding/dependent
  mechanism. Flattening the message unions did not touch this: the parameters that used to sit on
  `Subscribe`/`State`/`Busy`/`Ready` individually are now hoisted onto the two message unions,
  which makes the problem more visible rather than less — a top-level frame type cannot be
  parameterized by a value read from the wire. Note `scan.messages` already works around this by
  erasing keys to `Array(Byte)` (`README.md:2010-2016`), which also means instance keys are
  encoded differently there than in real frames.

- [ ] **`Expression` has no list literal, so `Values` constraints cannot be serialized.**
  `TYPES.md:328-333` defines `Expression = Invocation | Integer | Float | String`, and
  `TYPES.md:340` makes a `Type` value an `Expression`. But `Constraint` includes
  `Values { allowed: Array(Number) }` (`TYPES.md:180`) — an array-valued argument that cannot be
  written in the AST. Any transmitted `Type` containing a `Values` constraint is unrepresentable.

- [ ] **`Values(...)` is written two different, undefined ways in the examples.** `TYPES.md:207`
  uses `Values({n})` (set-brace notation that appears nowhere in the surface grammar);
  `TYPES.md:224` uses `Values(50)` (a scalar where `Array(Number)` is declared). Neither matches
  the declared field type, and there is no array-literal syntax defined at all.

- [ ] **`Number` is never defined.** `TYPES.md:180` says "`Number` stands for any numeric literal",
  but it appears as a field type inside a concrete, encodable ADT.

- [ ] **Aliases erase, so `LocalizedMarkdownText` carries no information.** `README.md:2259` defines
  `LocalizedMarkdownText = LocalizedText` and comments that "the marker exists so administrative
  interfaces know the resolved output should be rendered as CommonMark". Per the alias rule
  (`TYPES.md:151-160`) and `TYPES.md:342` ("Aliases are resolved during compilation and do not
  appear in the AST"), the marker does not survive to the wire or to `typeCatalog`. The same
  applies to `URI = String`, `Duration = TimeInterval` and `Icon = Media`, which undercuts the
  `typeCatalog` self-description claim at `README.md:1231`.

- [ ] **`Set` encoding is specified two contradictory ways.** `TYPES.md:82` says "a bitmask of
  `⌈K/8⌉` bytes"; `TYPES.md:397` says "a *K*-bit run in the bit stream … no padding to a byte
  boundary is added". These differ for any `K` not a multiple of 8 — including
  `Set(BringUpCapability)` (K=3) in the QR payload.

- [ ] **Constructor names are not scoped, but collide.** `TYPES.md:42` puts types and constructors
  in separate namespaces and `TYPES.md:190` selects a variant by bare constructor name. But
  `Error` is a constructor of both `Severity` and `HealthStatus`; `Disabled` of `Wifi`, `Ipv4Mode`
  and `Ipv6Mode`; `Union` is both a `Constraint` constructor and the `TypeDefinition.union` field.
  The wire is unambiguous (membership is decided against a known target type), but the surface
  language cannot disambiguate a bare `Error`.

- [ ] **`VariableLengthInteger(8)` holds 57 bits, not 64.** Seven continuation bytes of 7 bits plus
  a final 8-bit byte. `TYPES.md:74` bounds the wire size without stating the resulting value
  range. Separately, nothing requires *minimal* encoding, so the format is non-canonical — the
  same value has several valid encodings, which matters for anything hashed or byte-compared.

- [ ] **`Array` count-width rule is order-ambiguous.** "*v* is the smallest size in `1..8` such that
  `VariableLengthInteger(v)` can represent every length admitted by *C*" combined with "when *C*'s
  lower bound is a positive number *m*, the value written is `actualCount − m`". For
  `Range(min=1000, max=1010)`, is *v* chosen over `1000..1010` (→ 2) or over the offset range
  `0..10` (→ 1)? Both readings are supported.

---

## 2. Structural Bugs in Concrete Type Definitions

- [ ] **`scan.info`'s output type is unreadable past the icon.** `README.md:1561` —
  `DeviceData { name, description, icon: Icon, vendor, web }`, where `Icon = Media` and
  `Media.content: Stream(Byte)` (`README.md:2242`). `TYPES.md:106` is explicit that nothing after
  a `Stream` is ever written or read. As specified, `vendor`, `web`, all of `versionData`, and all
  of `userData` (including the user's `applicationName`, `location` and `tags`) are unreachable.

- [ ] **`Wire` is malformed twice.** `README.md:2155-2159`:
  ```
  Wire {
     remoteModality:     RemoteModalityReference,
     localModality:      RemoteModalityReference   // Has to reference local peer
  }
  ```
  `RemoteModalityReference(keyType: Type)` is parameterized (`README.md:2201`) and the argument is
  missing on both fields; and the second field should be `LocalModalityReference`, with a comment
  standing in for what the type system should enforce.

- [ ] **`minimumIntentWait` is declared and never used.** `README.md:1222` puts it on every
  `Modality`, but no rule obliges an Initiator to honour it — while `Subscribe.minimumSendWait`
  (`README.md:1114`) gets a full paragraph of MUSTs. §Rate Limiting (`README.md:2668`) claims all
  receivers define a maximum consumption rate "for both data and control settings", which is only
  true if `minimumIntentWait` is normative. It is also the last surviving use of the abandoned
  "intent" vocabulary (see also `README.md:1819`, `1864`, `2065`).

- [ ] **`Nothing` vs `Unit`.** `README.md:1077-1094` and `README.md:1236` use `Nothing` as the "no
  input/output" type. `TYPES.md` defines no such type, and every actual modality declaration uses
  `Unit`. `README.md:1080` describes `Nothing` as encoding to zero value bytes — which is exactly
  `Unit`. Pick one.

- [ ] **`Optional` vs `Option`.** `README.md:2083` and `TYPES.md:602` both write
  `Optional(member.outputType)`; the library type is `Option` (`TYPES.md:262`).

- [ ] **`Log` has no timestamp.** `README.md:1795` — `Log { severity, message }`. A log stream that
  cannot be ordered in time is of limited use, and `TYPES.md:33`'s own illustrative `LogLine`
  carries a `time` field.

---

## 3. Modalities Layer and LWW Semantics

- [ ] **LWW converges the Lamport pair but can lose the value.** `README.md:1059` says the value "is
  not stored separately" and is re-acquired via the subscribe flow. `README.md:1092` says a
  restarting device reinitialises to `(counter = 0, writer = self)` with its configured default,
  and peers echo back a later pair. Result: `seenCounter` jumps forward, so the device's default
  is ordered *behind* a write whose value no participant holds. Ordering recovers; the value does
  not.

- [ ] **"Authoritative for its own outputs" contradicts `scan.vmods`.** The Modalities Layer
  overview requires every device to keep a reproducible snapshot of each modality's current output
  so it can answer any `Subscribe` immediately. `README.md:2085-2090` says cluster-member outputs
  exist only as a byproduct of an arriving input, with no comparison against any prior output. A
  freshly-subscribed peer to a cluster member has nothing to receive. *(Partially tracked by
  TODO.md "Default state for modalities (cold boot)" — but README currently asserts the stronger
  guarantee as settled.)*

- [ ] **`scan.reboot` puts device-authored and user-authored values in one LWW slot.**
  `README.md:1516-1533` — `inputType == outputType == BootState`. The device writes
  `currentGeneration` on boot; the user writes `rebootGeneration`. Both are writes to the same
  instance's single shared state, so one clobbers the other rather than merging. The protocol
  works only if the two fields are separately owned, which LWW does not provide.

- [ ] **`scan.reboot` specifies a reboot loop.** `README.md:1533` — "there is a non-zero chance the
  generation comes back the same, in which case the device needs to reboot again."

- [ ] **`scan.reset` has sticky-state semantics that fight the Resolution Principle.**
  `README.md:1370` — `inputType = outputType = Boolean`, "whether the device should be in the
  'reset' state". After the reset the device is in factory state with no admin PSK, so the output
  is unobservable; and any peer that re-sends the retained `True` after reconnect resets it again.
  No transition back to `False` is defined.

- [ ] **Does a subscription survive a TCP reconnect?** Three passages disagree.
  `README.md:482` / `857-860` imply state persists ("continue sending messages without additional
  handshakes"). §Resolution Principle says devices resend "upon the connection is established
  **and the data is requested again**", implying a re-`Subscribe`. The `Ready` section says "On
  reconnection the subscription starts fresh." State it once.

- [ ] **Reconnect-without-handshake is largely unusable as specified.** Noise AESGCM nonces are
  per-message counters. An unclean TCP close loses in-flight ciphertext and desynchronises the
  counters, so the next frame fails to decrypt and `README.md:862-866` mandates
  `CloseConnection`. The feature is presented as a core capability (`README.md:482`) but succeeds
  only after a clean close — i.e. only the initiator's intentional-offline path. Either scope the
  claim or add a resync.

- [ ] **`minimumSendWait = 0` means two opposite things.** `README.md:1116` — "Zero means as fast as
  possible". `README.md:2698` (§Rate Limiting) — "The device should set the rate for data it
  doesn't need to 0". Also, `README.md:1858` refers to "infinite maximum wait", for which
  `Duration = UnsignedInteger(8)` has no encoding.

- [ ] **`IndexedModalityReference` indices are per-caller with no stability rule.**
  `README.md:1242` requires `Modalities` to list only modalities the caller has rights to, so
  `modalityIndex` is connection-scoped. Nothing says what happens when the modality set changes
  (e.g. a `scan.vmods` install) and `Modalities` is re-sent — all outstanding subscriptions'
  indices silently shift. Meanwhile `scan.wiring` and `scan.messages` reference modalities by
  string id, so two incompatible naming schemes coexist.

- [ ] **Virtual modality names can never be rendered.** `ClusterMember.modality: Modality`
  (`README.md:2058`) includes `name: LocalizedText`, which resolves against the source peer's
  `scan.i18n` bundle — and `README.md:1615` says bundles "travel only with firmware … generated by
  the vendor as part of the build". A user-defined cluster member's name cannot be in a firmware
  bundle, so it always renders as its raw id. `LocalizedText` is the wrong type for user-authored
  names.

- [ ] **Cluster accumulator is per-cluster but members are keyed.** `VirtualModalityCluster` has one
  `accType` accumulator (`README.md:2052`), while each `ClusterMember` is a full `Modality` with
  its own `keyType` and therefore potentially many instances. `transform_m : (acc, input)` has no
  access to the instance key, so all instances share one accumulator with no way to tell them
  apart. `VirtualModalityCluster` also carries no `typeCatalog`, implicitly restricting member
  types to built-ins without saying so.

- [ ] **"bounded" is not the same as "no `Stream`".** `README.md:2053` comments `accType` as
  "bounded; must contain no `Stream`". `Array(T)` with `size = All`, or an unconstrained `String`,
  contains no `Stream` and is unbounded. The needed constraint is stronger than the stated one.

---

## 4. Internet and Logical Layer

- [ ] **Solicited replies bump `generation`, which evicts a multi-homed peer's other addresses.**
  `README.md:748-755` issues a new generation for a solicited reply, and receivers evict entries
  "absent from two or more consecutive generations from its emitter" — a rule justified by the
  emitter advertising *on every active interface in every generation*. A solicited reply is a
  unicast on one path carrying one `(IP, port)`. Two solicitations therefore age out every other
  interface of a multi-homed peer, defeating the §Redundancy design that depends on those entries.
  Suggested fix: solicited replies should not bump the generation.

- [ ] **Shared TCP breaks the "only the initiator may close" rule.** `README.md:187` tells devices to
  reuse one TCP between two physical peers; `README.md:496` allows two logical connections between
  two devices. On that shared TCP each side is Initiator for one and Responder for the other.
  `README.md:166-170` then says only the initiator may close intentionally and "a responder does
  not close TCP to go offline" — but A going offline necessarily tears down the connection in
  which A is the Responder. The same setup breaks the "Heartbeats are one-directional" claim
  (`README.md:838`): both ends will emit them.

- [ ] **Heartbeat is not attributable when multiplexed.** `README.md:830-834` has Heartbeat flowing
  "from TCP establishment onward", before any logical connection exists, and `Heartbeat = Unit`
  carries nothing. But `livenessTimeout` is negotiated per subscription per logical connection
  (`README.md:1129-1140`), and a gateway TCP carries many logical peers while the gateway itself
  is none of them. Who emits Heartbeat on a gateway link, and against which timer, is
  unspecified.

- [ ] **DSCP-per-socket collides with the gateway model.** `README.md:2790` requires setting a
  connection's DSCP to the highest effective priority among its subscriptions, justified by device
  pairs having narrow relationships and firmware arriving on a separate connection. A gateway
  multiplexes every peer onto one TCP, so a single `Critical` subscription marks bulk firmware
  traffic EF. The stated justification does not hold for the gateway topology the spec mandates.

- [ ] **The 1200-byte MTU claim overstates its margin.** `README.md:416` and `README.md:465` say
  1200 bytes sits below the 1280-byte IPv6 minimum "after **worst-case** TCP/IP header overhead".
  Worst case is IPv6 40 + TCP 60 (max options) = 1300 > 1280. The real margin is 80 bytes, which
  covers a 40-byte TCP header. The conclusion holds in practice; the wording does not.

- [ ] **`Noise_KKpsk1` is mis-described.** `README.md:559-562` — "The '*KK*' variant comes from the
  fact, that the frame already contains the public static key of both the sender and responder."
  In Noise, KK means both static keys are known out of band *before* the handshake; the frame
  fields are transport metadata explicitly excluded from the end-to-end scheme (`README.md:520`)
  and rewritable by gateways (`README.md:352`). The spec never states that the responder takes
  `sourcePeer` as the remote static key — but it must, or KK cannot run.

- [ ] **Handshake choreography does not match the mandatory pattern.** `README.md:889-893` step 4
  says both devices "continue to send 'Continue Handshake' messages … in turn". `KKpsk1` is a
  two-message pattern: exactly one `ContinueHandshake` (responder → initiator), and none from the
  initiator.

- [ ] **Version/protocol negotiation is ambiguous.** `README.md:551` — on disagreement the responder
  "may respond with an Initiate Handshake of its own with the counter proposal". Since
  `InitiateHandshake` carries handshake bytes, the responder becomes the Noise initiator, which
  inverts the PSK/authorization asymmetry the Modalities Layer rests on. And the prologue must
  bind "the protocol name, as well as the versions" (`README.md:549`) — which of the two proposals
  goes in the prologue is unstated.

- [ ] **`AdvertisementRequest` reply path is described inconsistently.** `README.md:775` says a
  solicited reply is "sent to the solicitor's IP with a small random jitter";
  `README.md:801-809` says it goes over a freshly-opened TCP connection the replier then closes.
  That path also relies on the request's `sourcePeer` being present, but `Frame.sourcePeer` is
  `Option(PeerAddress)` with no rule making it mandatory here — unlike `InitiateHandshake`, where
  `README.md:600` states it explicitly.

---

## 5. Authorization and Security

- [ ] **Read access to `scan.grant`, `scan.keys` or `scan.netconfig` is full privilege escalation,
  and this is never stated.** `Right { modalityId, readOutput, writeInput }` (`README.md:1406`).
  `Rights` contains every PSK in cleartext (`README.md:1399`), `Keys` contains every PSK for every
  peer (`README.md:1436`), and `NetworkConfiguration.Gateway` contains gateway PSKs
  (`README.md:1963`). Granting read on any of these hands over the device — and, via `scan.keys`,
  its peers. Either mark these read-restricted or state the equivalence explicitly.

- [ ] **Rights are per-modality, not per-instance.** `Right.modalityId: String` has no instance key.
  You cannot grant `scan.logs` at `Info` but not `Debug` (keyed by `Severity`), nor grant a tenant
  channels 1–2 of an 8-channel power strip. Given that keyed instances are a core concept, this is
  a coarse mismatch.

- [ ] **Bring-up blobs are replayable to the same device.** `README.md:2379-2391` — AD is the
  device's `peerAddress` only, with no counter, timestamp or one-shot marker. The spec correctly
  scopes its claim ("against a *different* device"), but an attacker in radio range can replay a
  captured blob to the same device once it re-enters bring-up, forcing it onto a stale or
  attacker-chosen SSID.

- [ ] **Bring-up re-arm after a bad `scan.netconfig` write is undefined.** `README.md:1988-1991`
  makes revert optional ("a vendor MAY revert"). `README.md:2467` tears down bring-up channels on
  success, and the re-arm MUST at `README.md:2487` applies only to devices that implemented the
  inactivity timeout. A device that never armed a timeout and is pushed a bad WiFi config has no
  specified recovery path.

- [ ] **§Enroll and §Rights disagree on the administrative PSK's status.** `README.md:1350`
  (Enroll): enrollment installs the administrative PSK and wipes state. `README.md:1417` (Rights):
  "the administrative PSK is not listed here. If this list is empty, the master administrative key
  stays valid" — implying a non-empty list can invalidate it, contradicting `README.md:1338`
  ("must only be possible with the initial enrollment key") and the recovery story that depends on
  the factory PSK being permanent.

---

## 6. Bytecode VM (`TYPES.md`)

- [ ] **Jump offsets have no defined unit and cannot work as written.** `Jmp { offset }` "advances
  the current handler's instruction cursor by `offset`", and `JumpTable.targets` are
  "instruction-relative offsets". But instructions are bit-packed and variable-width — "a `Dup`
  occupies 6 bits; a `LoadSlot 0, 3` is roughly three bytes". You cannot seek to instruction *N*
  without decoding from the handler's start, and the unit (instructions / bits / bytes) is never
  stated. Control flow is unimplementable as specified.

- [ ] **`ReadChunk`'s static `count` cannot match a runtime chunk event.**
  `ReadChunk { depth, slot, count }` "consumes `count` bytes from a `Chunk` event", but
  §Incremental Consumption says bulk-byte batching is "an implementation choice, not pinned by the
  protocol". A compile-time constant cannot match a runtime-variable event size.

- [ ] **Node numbering is required of devices but never specified.** `AtNode { node }` — node IDs
  are "assigned by the compiler in a canonical pre-order walk of the input type", and the runtime
  "maintains the current node as it advances". The device must reproduce the compiler's numbering
  exactly, but the canonical walk is not defined anywhere (unlike the value encoding).

- [ ] **The worked example uses the wrong event.** §Worked Example fires
  `AtNode { node = <values stream>, event = OnStartContainer }`, but §Handlers is explicit that
  `OnStartContainer` is for finite containers (`Array`, `Set`) and `OnStartStream` fires on entry
  to a `Stream`.

- [ ] **The worked example frames fields inconsistently.** The `acc` field is emitted with
  `EmitStartField 0` / `EmitEndField 0` around it, while `Some`'s `value` field is emitted bare.
  One of the two is wrong.

---

## 7. Cross-Document Drift

- [ ] **`COMPARISON.md:44`** claims discovery via "**periodic** identity advertisements". The
  README model is strictly event-driven (`README.md:757`: "Advertisement is emitted only on
  events, never at a steady periodic rate"), with liveness moved to `Heartbeat`.

- [ ] **`COMPARISON.md:188`** (Device Management table) predates `scan.i18n`, `scan.netconfig` and
  `scan.messages`. Worth a pass now that `scan.netconfig` is mandatory.

- [ ] **`TODO.md:52` is stale.** "Rework vmod transform for streaming model" describes a
  `(state: Array<MemberValue>) -> Array<MemberValue>` signature that `README.md` no longer
  contains — §Virtual Modalities is already the streaming/accumulator model.

- [ ] **`TODO.md:95` is stale.** "Messages … does not contain key for modality instance" is done:
  `MessageEvent` carries the key as `Array(Byte)` (`README.md:2010-2016`).

- [ ] **`.claude/CLAUDE.md` is stale.** Its mandatory-modality list omits `scan.i18n` and
  `scan.netconfig`, and its Logical Layer summary still says "advertisements (~1/sec, offline =
  3 missed)". Only the `heartbeatInterval` default of 1 s and `livenessTimeout` of 3 s remain.

---

## 8. Typos

- [ ] `README.md:1396` — "expect the master adminitration key" → *except* / *administration*
- [ ] `README.md:1763` — "the average time packets are acknowledges by TCP/IP" → *acknowledged*
- [ ] `README.md:1858` — "It is expected that this will modality will be used" → duplicated *will*
- [ ] `README.md:2143` — "what modalities on this device is wired to" → *are wired*
- [ ] `README.md:141` — "so that it's list remains ordered" → *its*
- [ ] `README.md:2679` — "it's controller is notified" → *its*

---

## Suggested Starting Point

Four items are unambiguous bugs with a mechanical fix rather than open design questions, and
everything else is easier to reason about once they are settled:

1. ~~Union-of-named-types grammar (§1, first item)~~ — done: the unions are flat.
2. Late-bound type parameters (§1, second item)
3. `scan.info` stream ordering (§2, first item)
4. `Wire` field types (§2, second item)
