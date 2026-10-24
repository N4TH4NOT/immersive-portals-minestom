# Build and runtime validation

[English](port-26.2.md) · [Français](port-26.2.fr.md)

```sh
git submodule update --init --recursive
./gradlew build validateAccessWidener -PportalSmoke compileSmokeTestJava
```

The October 24 baseline passes 56 JUnit tests across both repositories. Opt-in scenarios are excluded from distributed mod classes. Use a working graphical display and OpenGL/Vulkan driver; development worlds and captures live under `run/smoke-*`.

## Fabric

Run the server and client in separate terminals; configure the local server EULA first.

```sh
./gradlew -PportalSmoke runServer
./gradlew -PportalSmoke runClient --args='--graphicsBackend vulkan --username PortalValidation'
```

For integrated-server resource/datapack validation, replace the client command with `./gradlew -PportalSmoke -PportalSmokeSingleplayer runClient`. Use username `ScaledTest` for rotation/scale checks on the dedicated server.

## Minestom

```sh
./gradlew :minestom-example:run
./gradlew -PportalSmoke -PportalSmokeAddress=127.0.0.1:25566 \
  -PportalSmokeTarget=demo:gallery -PportalSmokeInteraction=true \
  runClient --args='--graphicsBackend vulkan --username PortalValidation'
```

Expect `PORTAL_SMOKE_COMPLETE`, `MINESTOM_REMOTE_CHEST_OK` and a server-confirmed world change. Add `-PportalSmokeInteractions=true` to both commands for multi-portal block/entity callbacks and rejected-action prediction checks.

## Gravity

```sh
./gradlew :minestom-example:run -PportalSmokeGravity
./gradlew -PportalSmoke -PportalSmokeGravity -PportalGravitySmoke=true \
  -PportalSmokeAddress=127.0.0.1:25566 -PportalSmokeTarget=demo:gallery \
  runClient --args='--graphicsBackend vulkan --username GravityTest'
```

Expect `PORTAL_GRAVITY_COMPLETE`: six directions, traversal, remote entities and interaction. Keep the server, remove the client's `-PportalSmokeGravity` and use username `GravityNone` to expect `PORTAL_GRAVITY_UNSUPPORTED_CLIENT_OK`.

## Rendering

Add `-PportalRenderSmoke=true` to the Fabric client command and use username `OcclusionTest`. The scenario checks wall occlusion/reveal, prediction policies, render modes and resource reload; expect `PORTAL_RENDER_COMPLETE`.

Use `opengl` with `-PportalSmokeSodium` or `-PportalSmokeIris` for those integrations. The Iris flag prepares the test shaderpack. [Rendering decisions](rendering-26.2.md) explain the expected behavior.

Recorded runtime checks use Xvfb/Mesa llvmpipe, including native Vulkan and OpenGL with Sodium/Iris. They cover the exercised scenes, not every GPU or shaderpack. See [migration findings](migration-audit-26.2.md) and [network findings](network-audit-26.2.md).
