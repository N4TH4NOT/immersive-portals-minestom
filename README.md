# Immersive Portals — Minecraft 26.2 / Minestom

[English](README.md) · [Français](README.fr.md)

Fabric port with native OpenGL/Vulkan rendering and a Minestom server SDK. Development continues on `port-26.2`; dependency versions are defined in [gradle.properties](gradle.properties). Java 25 is required.

## Build and run

```sh
git submodule update --init --recursive
./gradlew build validateAccessWidener
./gradlew :minestom-example:run
```

Install the mod JAR from `build/libs/` with Fabric API. Cloth Config and the gravity adapter/API are bundled. Directional player gravity additionally requires [Gravity Changer](https://github.com/N4TH4NOT/gravity-changer#fabric-installation-and-use). The Minestom example listens on `127.0.0.1:25566` in offline mode.

Use Minecraft's `--graphicsBackend vulkan` for native Vulkan. Iris is tested with OpenGL; shaderpack compatibility varies. Cloud setup is available through [scripts/setup-cloud.sh](scripts/setup-cloud.sh).

## Developer guides

- [Minestom API](docs/minestom.md) and [gravity integration](docs/gravity-repository.md).
- [Build/runtime validation](docs/port-26.2.md) and [rendering decisions](docs/rendering-26.2.md).
- [1.21 upstream review](docs/upstream-1.21-review.md), [migration findings](docs/migration-audit-26.2.md) and [network audit](docs/network-audit-26.2.md).
- [Optional generation datapack](examples/custom_portal_gen/README.md).

DimLib, Pehkui, Flywheel, Porting Lib and Requiem integrations remain excluded: no matching Fabric 26.2 releases were found in the October 2026 review. Native scaling and datapack dimensions remain available.

Based on [Immersive Portals](https://github.com/iPortalTeam/ImmersivePortalsMod) by qouteall and contributors, under [Apache-2.0](LICENSE). The [upstream wiki](https://qouteall.fun/immptl/wiki/) describes earlier engine versions.
