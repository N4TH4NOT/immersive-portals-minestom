# Network audit

[English](network-audit-26.2.md) · [Français](network-audit-26.2.fr.md)

Review of October 24, 2026: custom payloads, bridge messages, native remote-world packets and their send sequences. Wire format remains v2. Message definitions and bounds are in [`BridgeMessage`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeMessage.java) / [`BridgeCodec`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeCodec.java); accepted native types in [`BridgeClient`](../src/main/java/qouteall/imm_ptl/core/network/bridge/BridgeClient.java).

## Removed redundancy

| Sequence | Decision |
| --- | --- |
| Unchanged remote entity at each scan | Compare wire snapshots per viewer/view; publish only changed metadata indices, attributes and equipment slots. |
| Spawn followed by the same position/head | Seed history from the encoded spawn; send precision/ground information only when it adds state. |
| Explicit entity update followed by automatic scan | Observe the published partial state before comparing. |
| Equivalent portal definitions or world registrations | Value equality, including NBT bytes; publish only changed definitions/tables. |
| Identical block delta within a service tick | Coalesce ordinary identical state; retain A → B → A transitions and prediction barriers. |
| Delta before initial/refreshed chunk | Let the authoritative chunk snapshot supply it. |
| Correction followed by identical gravity; Clear followed by per-view closes | Record gravity carried by the correction; Clear already closes the session's views. |
| Fabric performance/global-portal updates | Send performance only to negotiated Fabric servers on change; an immediate broadcast consumes the pending global-portal sync. |

## Necessary repetition

Absolute entity anchors and gravity refreshes reassert authority. New appearances and recycled numeric IDs require fresh state. Each denied block prediction retains its state/ack pair. Sounds, damage, particles, effects and RPC commands may be separate events despite identical bytes.

Root-world traffic uses Minestom's native stream; remote views carry other worlds. Redirected packets replace the corresponding original delivery. Fragment headers identify bounded reassembly contexts; they are not duplicate game state.

`OpenView` retains 28 legacy bytes for center/radius, unused by the current client cache. Removing them would require negotiated protocol evolution. Application-requested resynchronizations are preserved; arbitrary explicit events are not deduplicated by bytes.

## Evidence

For an idle living entity after spawn, 99 scans previously emitted 495 native packets. The tracker emits nine absolute anchors, 531 native bytes: 98.2% fewer packets in this scenario, excluding gravity, chunks, framing and connection setup.

[`RemoteEntityTrackerTest`](../minestom/src/test/java/io/github/n4th4not/immersiveportals/minestom/RemoteEntityTrackerTest.java) and [`PortalNetworkTest`](../minestom/src/test/java/io/github/n4th4not/immersiveportals/minestom/PortalNetworkTest.java) cover partial updates, slot resets, ID reuse, view re-entry, precision, block transitions, acknowledgements and identical events. Minestom runtime capture confirms three block states for three acknowledgements and no unsupported performance RPC. [Runtime commands](port-26.2.md) reproduce the exercised paths.
