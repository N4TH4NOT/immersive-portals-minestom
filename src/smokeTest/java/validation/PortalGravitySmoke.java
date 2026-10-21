package validation;

import io.github.n4th4not.immersiveportals.protocol.*;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.network.bridge.BridgeClient;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

/** Exercises the published mod, negotiated Minestom state and real gravity-changing traversal. */
final class PortalGravitySmoke {
    private static int ticks;
    private static int lastFrame = -1;
    private static final Direction[] DIRECTIONS = {
        Direction.UP,
        Direction.NORTH,
        Direction.SOUTH,
        Direction.WEST,
        Direction.EAST,
        Direction.DOWN
    };

    static void tick(Minecraft client, int realTicks) {
        require(realTicks < 2400, "Gravity scenario timed out");
        if (RenderStates.frameIndex == lastFrame) return;
        lastFrame = RenderStates.frameIndex;
        ticks++;
        var api = GravityChangerInterface.invoker;
        if (!api.isGravityChangerPresent()) {
            if (ticks < 100) return;
            require(BridgeClient.isActive(), "Missing-mod scenario requires the Minestom bridge");
            if (ticks == 140) client.options.keyUp.setDown(true);
            if (ticks == 180) client.options.keyUp.setDown(false);
            if (ticks == 240) {
                require(
                        client.level.dimension().identifier().toString().equals("demo:lobby"),
                        "Unsupported client crossed a gravity-changing portal");
                require(
                        api.getGravityDirection(client.player) == Direction.DOWN,
                        "Unsupported client acquired non-default gravity");
                System.out.println("PORTAL_GRAVITY_UNSUPPORTED_CLIENT_OK");
                client.stop();
            }
            return;
        }
        if (ticks >= 60 && ticks < 300 && (ticks - 60) % 40 == 0) {
            Direction direction = DIRECTIONS[(ticks - 60) / 40];
            client.player.connection.sendCommand("gravity_smoke " + direction.getName());
        }
        if (ticks >= 99 && ticks <= 299 && (ticks - 99) % 40 == 0) {
            Direction expected = DIRECTIONS[(ticks - 99) / 40];
            require(
                    api.getGravityDirection(client.player) == expected,
                    "Gravity state was not synchronized: " + expected);
            require(
                    Math.abs(api.getState(client.player).strength() - 12) < 1e-6,
                    "Gravity strength was lost");
            Vec3 expectedEye = client.player.position().add(api.getEyeOffset(client.player));
            require(
                    expectedEye.distanceTo(client.player.getEyePosition()) < 1e-5,
                    "Eyes use the wrong gravity frame");
            var up =
                    client.gameRenderer
                            .mainCamera()
                            .rotation()
                            .transform(new org.joml.Vector3f(0, 1, 0));
            var expectedUp = api.transformPlayerToWorld(expected, new Vec3(0, 1, 0));
            require(
                    up.x * expectedUp.x + up.y * expectedUp.y + up.z * expectedUp.z > .999,
                    "Camera did not settle into the gravity frame: " + expected + " " + up);
            capture(client, "gravity-" + expected.getName());
            System.out.println("PORTAL_GRAVITY_DIRECTION_OK " + expected);
        }
        if (ticks == 310) {
            if (BridgeClient.isActive()) {
                boolean found = false;
                for (var world : qouteall.imm_ptl.core.ClientWorldLoader.getClientWorlds())
                    if (world.dimension()
                            .identifier()
                            .toString()
                            .equals(System.getProperty("portalSmoke.target")))
                        for (var entity : world.entitiesForRendering())
                            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                                    && api.getGravityDirection(entity) == Direction.NORTH)
                                found = true;
                require(found, "Remote entity did not receive gravity before traversal");
            }
            client.player.connection.sendCommand("gravity_smoke ready");
        }
        if (ticks == 360) client.options.keyUp.setDown(true);
        String target = System.getProperty("portalSmoke.target", "minecraft:the_nether");
        boolean arrived = client.level.dimension().identifier().toString().equals(target);
        if (arrived) client.options.keyUp.setDown(false);
        if (ticks == 440) {
            require(arrived, "Gravity-changing portal was not crossed");
            require(
                    api.getGravityDirection(client.player) == Direction.NORTH,
                    "Portal did not rotate gravity: "
                            + api.getState(client.player)
                            + " base="
                            + api.getBaseGravityDirection(client.player));
            require(
                    Math.abs(api.getState(client.player).strength() - 12) < 1e-6,
                    "Portal changed gravity strength");
            require(
                    client.player.getBoundingBox().getZsize()
                            > client.player.getBoundingBox().getYsize(),
                    "Body bounds were not rotated");
            capture(client, "gravity-traversal");
            if (BridgeClient.isActive()) {
                boolean remoteGravity = false;
                for (var entity : client.level.entitiesForRendering())
                    if (entity != client.player
                            && entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                            && api.getGravityDirection(entity) == Direction.NORTH)
                        remoteGravity = true;
                require(remoteGravity, "Remote entity gravity was not streamed");
                api.setClientPlayerGravityDirection(client.player, Direction.EAST);
                var eye = client.player.getEyePosition();
                var point =
                        new io.github.n4th4not.immersiveportals.protocol.Vec3(eye.x, eye.y, eye.z);
                BridgeClient.send(
                        new BridgeMessage.Teleport(java.util.UUID.randomUUID(), point, point));
            } else {
                api.setClientPlayerGravityDirection(client.player, Direction.EAST);
                client.player.connection.sendCommand("gravity_smoke confirm");
            }
            System.out.println("PORTAL_GRAVITY_TRAVERSAL_OK");
        }
        if (ticks == 480) {
            require(
                    api.getGravityDirection(client.player) == Direction.NORTH,
                    "Refused traversal did not restore server gravity");
            capture(client, "gravity-after-correction");
            if (BridgeClient.isActive()) {
                client.player.setYRot(0);
                client.player.setXRot(0);
            }
        }
        if (ticks == 500 && BridgeClient.isActive()) {
            var hit =
                    qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient
                            .remoteHitResult;
            require(
                    hit instanceof net.minecraft.world.phys.BlockHitResult block
                            && block.getBlockPos()
                                    .equals(new net.minecraft.core.BlockPos(0, 71, 2)),
                    "Gravity-framed remote chest was not targeted: "
                            + hit
                            + " eye="
                            + client.player.getEyePosition()
                            + " look="
                            + client.player.getViewVector(1));
            client.options.keyUse.setDown(true);
        }
        if (ticks == 502) client.options.keyUse.setDown(false);
        if (ticks == 525) {
            if (BridgeClient.isActive()) {
                require(
                        client.gui.screen()
                                instanceof
                                net.minecraft.client.gui.screens.inventory.AbstractContainerScreen,
                        "Remote interaction with side gravity was not accepted by Minestom");
                client.player.closeContainer();
                System.out.println("PORTAL_GRAVITY_REMOTE_INTERACTION_OK");
            }
            System.out.println("PORTAL_GRAVITY_COMPLETE " + api.getState(client.player));
            client.stop();
        }
    }

    private static void capture(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(
                client.gameDirectory,
                name + ".png",
                client.gameRenderer.mainRenderTarget(),
                1,
                text -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
