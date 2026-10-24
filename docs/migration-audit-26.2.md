# Minecraft 1.21 → 26.2 migration findings

[English](migration-audit-26.2.md) · [Français](migration-audit-26.2.fr.md)

Review performed October 22, 2026, against the inherited 1.21 branch. [Upstream scope](upstream-1.21-review.md) records the baseline; [runtime validation](port-26.2.md) is maintained separately.

## Corrections from the review

| Area | Finding and resolution |
| --- | --- |
| World creation | The constructor mixin captured obsolete 1.21 arguments; capture only the callback it uses. |
| Item resources | Add current item definitions referencing the retained models. |
| Reverse generation | Register validated reverse rules for all trigger families. Tests cover valid, invalid and one-way rules. |
| Teleport hooks | Distinguish local teleportation from dimension changes to avoid generation/tracker side effects. |
| Chunk loading | Wait for native unloaded-ticket sentinel promotion rather than releasing a still-pending slot. |
| Remote terrain | Copy reusable deltas per view, replay initial chunks and propagate empty/nonempty, replacement and unload transitions. |
| Example datapack | Migrate registry paths/tags and data format; declare dimensions through datapacks instead of DimLib. |

## Evidence and limits

Review covered source/resources, Mixin signatures, access widener, codecs, rendering, physics and optional adapters. Bytecode inspection and real client/server runs supplement compilation: resource loading and captured mixin arguments are not established by a successful build alone.

Runtime scenarios exercise Fabric world creation/datapacks, shapes, recursive views, GUI rendering, Minestom traversal/containers, gravity and Sodium/Iris. Native 26.2 entity references and teleport transitions replace the old projectile-owner/ender-pearl mixins.

Unavailable optional integrations are listed in the [README](../README.md). Their absence of a matching release does not imply project abandonment. Application-specific third-party components and arbitrary hardware/shaderpacks are outside the exercised scenarios.
