# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Status

This repository currently contains **only a protocol specification** — there is no active codebase to build, test, or lint. The `java/` directory contains stale reference-implementation scaffolding that is out of sync with the current spec; do not treat it as authoritative and do not work in it unless explicitly asked.

All work happens in Markdown:

- `README.md` — the SCAN protocol specification (the main document).
- `TYPES.md` — the SCAN Type System specification (separate, stand-alone type/transform language used by the protocol).
- `COMPARISON.md` — reference comparison against existing IoT protocols (MQTT, CoAP, Matter, NMEA 2000, DDS, etc.). Useful for design-rationale questions.
- `TODO` — free-form running list of open design questions and follow-ups.

## What SCAN Is (Big Picture)

SCAN is a peer-to-peer, end-to-end-encrypted protocol for data acquisition and device control (home/building automation, marine, automotive). Key architectural commitments that constrain every design decision — understand these before editing the spec:

- **No central component.** No broker, no coordinator, no PKI, no registry. Any proposed feature that would require one is out of bounds.
- **Four layers**, each with a distinct responsibility; keep concerns in the right layer:
  1. **Internet Layer** — TCP/IP transport + UDP multicast advertisements (port 11372, multicast group 239.255.255.244). Supports direct LAN and gateway topologies.
  2. **Logical Layer** — Noise Protocol handshake (`Noise_KKpsk1_25519_AESGCM_SHA256` is mandatory), framing, multiplexing, fragmenting (chunks), advertisements (~1/sec, offline = 3 missed). Peers are identified by a 32-byte static public key (`PeerAddress`).
  3. **Modalities Layer** — the only way devices interact. Initiator/Responder asymmetry: the Initiator presents a PSK, the Responder authorizes. Messages: `Subscribe` / `Unsubscribe` / `State` + responder-initiated `Modalities` announcement.
  4. **Application Layer** — the mandatory/optional modality catalog (`scan.enroll`, `scan.reset`, `scan.grant`, `scan.keys`, `scan.firmware`, `scan.reboot`, `scan.info`, `scan.health`, `scan.logs`, `scan.backup`, `scan.netstat`, `scan.messages`, `scan.vmods`, `scan.wiring`, `scan.locate`). New mandatory modalities are a big commitment — don't add casually.
- **Authorization is PSK-based, not PKI-based.** A PSK acts as a role. Each device ships with a factory enrollment PSK used once; enrollment wipes device state and installs a new administrative PSK.
- **The Resolution Principle** is load-bearing: every modality must be such that losing messages only reduces *resolution*, never changes *meaning*. State is always replaceable by a newer value for the same modality. This is what enables the backpressure, reconnect, and QoS story — don't propose designs (e.g. deltas, commands, ACKs) that violate it without explicit discussion.
- **Wiring + virtual modalities (with compiled transforms)** are how interoperability is achieved instead of a centrally-agreed message catalog. The transform language is defined in `TYPES.md`.
- **Priority / QoS** — every modality declares a default `Priority` (`Critical` / `Normal` / `Management` / `Bulk`), a subscription may override it, and implementations map the effective priority to DSCP markings. Three deterministic-delivery tiers are documented (plain DSCP, 802.1Qav CBS, 802.1Qbv TSN); the protocol itself is unchanged across tiers.

## TYPES.md (Separate Sub-Spec)

The type system is intentionally minimal and uniform: aggregates are just built-in parametric types (`Array`, `DynamicArray`, `Struct`, `Union`, `Stream`, `Option`), not dedicated syntax. Numeric primitives are `UnsignedInteger` / `SignedInteger` / `VariableLengthInteger` / `FloatingPoint` parameterized by byte size, all big-endian. At most one `Stream` per message, always last. Several sections (binary representations, subset determination, transformation language) are still `TODO` — expect to be asked to draft these.

## Working on the Spec

- Both `README.md` and `TYPES.md` are marked *Draft Version*; inconsistencies are expected. When you spot one, flag it rather than silently "fixing" one side.
- Protocol structures are written in the TYPES.md syntax inside fenced code blocks. When editing a message/modality, keep that syntax consistent (`Struct(...)`, `Union(...)`, `DynamicArray(...)`, `Optional(...)`, `VariableLengthInteger(n)`, etc.) and keep field names in `camelCase` as the existing spec does.
- Modality declarations follow a fixed shape (`id`, `priority`, `keyType`, `outputType`, `inputType`); preserve that shape and the `scan.*` id convention when adding or modifying modalities.
- `TODO` markers exist inline in `README.md` (e.g. `// TODO` in backup/restore, network settings) and as a top-level `TODO` file. Treat these as the canonical list of known-open items.
- `COMPARISON.md` references specific claims in `README.md`; when you change a load-bearing property (topology, crypto, discovery, QoS), check whether `COMPARISON.md` needs a corresponding update.
