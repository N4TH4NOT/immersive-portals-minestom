# Minestom integration

[English](minestom.md) · [Français](minestom.fr.md)

## Dependency and lifecycle

Use a composite build of this checkout, initialized with its gravity submodule:

```groovy
// settings.gradle
includeBuild('../immersive-portals-minestom')
// build.gradle
repositories { mavenCentral() }
dependencies {
    implementation 'io.github.n4th4not:minestom:7.0.0-alpha.1'
}
```

The SDK re-exports its protocol and gravity libraries. Use the pinned Minestom version in [the example build](../minestom-example/build.gradle). For local Maven consumption, publish the gravity modules before `:portal-protocol:publishToMavenLocal :minestom:publishToMavenLocal`.

Create `PortalService` after `MinecraftServer.init()`. Worlds need distinct dimension keys and dimension types present in the client's configuration registries.

```java
PortalService portals = new PortalService();
PortalHandle handle = portals.create(lobby, gallery, p -> p
    .origin(new Vec3(0, 72, 0)).destination(new Vec3(20, 72, 0)).size(4, 4));
handle.update(p -> p.size(6, 4));
```

Origins are opening centers. `axisW × axisH` defines the front normal; crossing runs front to back. Create the reverse portal explicitly. Close handles when removed and the service at shutdown. See [ExampleServer](../minestom-example/src/main/java/io/github/n4th4not/immersiveportals/example/ExampleServer.java) for complete setup.

## Options and traversal

[`PortalOptions`](../minestom/src/main/java/io/github/n4th4not/immersiveportals/minestom/PortalOptions.java) encodes shapes and behavior into `extraNbt`. `PortalDefinition` geometry overrides equivalent native NBT fields. Native animation drivers can be rendered by the client; the SDK does not execute them, so update authoritative geometry for moving portals.

```java
if (handle.crosses(previousEye, currentEye)) {
    handle.teleport(entity);
}
```

Explicit transfers do not require contact with the opening. They transform eyes, look, velocity and scale while preserving external modifiers. Detach passengers before individual transfers; transfer failure restores managed scale/gravity state.

## Remote interactions and packets

```java
portals.onRemoteInteraction(event -> {
    // Apply game rules using event.instance(), blockPosition(), entity() and packet().
});
portals.sendWorldPacket(gallery, soundPacket);
```

The service validates portal path, reach, visibility and obstacles. The callback owns gameplay and container validity. Block state and sequence acknowledgement resolve prediction even when the callback does nothing.

`sendWorldPacket` sends supported world-local packets to remote views only; current-world viewers still need the application's normal Minestom sends. Custom particle types require matching client registration and encoding.

Use `authorization` and `visibility` to restrict the bridge. Limits and wire definitions live in [`PortalService`](../minestom/src/main/java/io/github/n4th4not/immersiveportals/minestom/PortalService.java) and [`BridgeCodec`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeCodec.java). Bridge v2 peers must target the same Minecraft protocol.

See [gravity integration](gravity-repository.md), [network behavior](network-audit-26.2.md) and [runtime checks](port-26.2.md).
