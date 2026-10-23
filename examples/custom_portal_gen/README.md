# Custom portal generation — Minecraft 26.2

This optional datapack requires this fork of Immersive Portals. Build it with
`./gradlew examplePortalDatapack`; the ZIP is written to `build/examples/`.
Install it when creating a world, so its dimension definitions are loaded.
Back up an existing world before adding or removing dimensions.

The seven rules live in the current custom registry directory
`data/i_am_namespace/immersive_portals/custom_portal_generation/`. Block tags
are written with an explicit `#`; `supports_bamboo` replaces the removed
`bamboo_plantable_on` tag. The pack targets data format 107.1.

The pack declares `immersive_portals:skyland`, `immersive_portals:chaos` and
`immersive_portals:void` through vanilla datapack dimension registration, using
the terrain generators retained by the mod. DimLib is not required. These
dimensions are only added when this pack is installed; the mod does not create
or remove dimensions dynamically. The skyland example uses the explicit seed 0.

Example triggers include water on a glowstone frame, flint and steel on lapis,
quartz thrown into a soul-soil/lava square, and a compass on a plank frame.
These are demonstration rules, not a required part of the mod installation.
