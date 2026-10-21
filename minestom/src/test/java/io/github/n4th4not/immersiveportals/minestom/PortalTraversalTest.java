package io.github.n4th4not.immersiveportals.minestom;

import static org.junit.jupiter.api.Assertions.*;

import io.github.n4th4not.immersiveportals.protocol.*;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.*;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.world.DimensionType;

import org.junit.jupiter.api.*;

import java.util.UUID;

class PortalTraversalTest {
    @BeforeAll
    static void initialize() {
        MinecraftServer.init();
    }

    @AfterAll
    static void shutdown() {
        MinecraftServer.stopCleanly();
    }

    private InstanceContainer world() {
        var world =
                new InstanceContainer(
                        UUID.randomUUID(),
                        DimensionType.OVERWORLD,
                        Key.key("test:world_" + UUID.randomUUID().toString().replace("-", "")));
        MinecraftServer.getInstanceManager().registerInstance(world);
        return world;
    }

    @Test
    void gravityTraversalTransformsFramesWhilePreservingEyesVelocityAndStrength() {
        var source = world();
        var target = world();
        try (var service = new PortalService()) {
            var rotation = Quaternion.axisAngle(new Vec3(1, 0, 0), Math.PI / 2);
            var handle =
                    service.create(
                            source,
                            target,
                            p ->
                                    p.destination(new Vec3(20, 80, 0))
                                            .rotation(rotation)
                                            .scale(.5)
                                            .extraNbt(
                                                    PortalOptions.builder()
                                                            .changesGravity(true)
                                                            .build()
                                                            .encode()));
            for (var direction : GravityDirection.values()) {
                var entity = new LivingEntity(EntityType.ARMOR_STAND);
                entity.setInstance(source, new Pos(0, 75, 1, 35, -20)).join();
                service.gravity().set(entity, new GravityState(direction, 12));
                var expectedEye =
                        handle.definition().transformPoint(service.gravity().eyePosition(entity));
                var expectedLook = rotation.rotate(service.gravity().lookDirection(entity));
                entity.setVelocity(new Vec(2, 3, -4));
                handle.teleport(entity).join();
                assertSame(target, entity.getInstance());
                assertEquals(
                        direction.rotated(rotation), service.gravity().get(entity).direction());
                assertEquals(12, service.gravity().get(entity).strength());
                assertVector(expectedEye, service.gravity().eyePosition(entity));
                assertVector(expectedLook, service.gravity().lookDirection(entity));
                assertVector(
                        handle.definition().transformVector(new Vec3(2, 3, -4)),
                        new Vec3(
                                entity.getVelocity().x(),
                                entity.getVelocity().y(),
                                entity.getVelocity().z()));
                entity.remove();
            }
        }
    }

    @Test
    void gravityCanBePreservedAndResetWithoutChangingNoGravityOrAerodynamics() {
        var source = world();
        try (var service = new PortalService()) {
            var entity = new LivingEntity(EntityType.ARMOR_STAND);
            entity.setInstance(source, new Pos(1, 80, 1, 20, -10)).join();
            var original = entity.getAerodynamics();
            var originalBox = entity.getBoundingBox();
            entity.setNoGravity(true);
            service.gravity().set(entity, GravityDirection.WEST);
            var handle =
                    service.create(
                            source,
                            source,
                            p -> p.rotation(Quaternion.axisAngle(new Vec3(0, 1, 0), Math.PI / 2)));
            var expectedLook =
                    handle.definition().rotation().rotate(service.gravity().lookDirection(entity));
            handle.teleport(entity).join();
            assertEquals(GravityDirection.WEST, service.gravity().get(entity).direction());
            assertVector(expectedLook, service.gravity().lookDirection(entity));
            assertTrue(entity.hasNoGravity());
            service.gravity().reset(entity);
            assertEquals(GravityState.DEFAULT, service.gravity().get(entity));
            assertEquals(original, entity.getAerodynamics());
            assertEquals(originalBox, entity.getBoundingBox());
            assertTrue(entity.hasNoGravity());
        }
    }

    @Test
    void passiveEntitiesAccelerateAlongLocalDownAndCollideWithASideWall() {
        var source = world();
        try (var service = new PortalService()) {
            var entity = new LivingEntity(EntityType.ARMOR_STAND);
            entity.setInstance(source, new Pos(2, 80, 2)).join();
            source.setBlock(5, 79, 2, net.minestom.server.instance.block.Block.STONE);
            source.setBlock(5, 80, 2, net.minestom.server.instance.block.Block.STONE);
            source.setBlock(5, 81, 2, net.minestom.server.instance.block.Block.STONE);
            service.gravity().set(entity, GravityDirection.EAST);
            for (int i = 0; i < 50; i++) entity.tick(i * 50L);
            assertTrue(entity.getPosition().x() > 4.5, "Entity did not fall toward the side wall");
            assertTrue(entity.getPosition().x() <= 5.00001, "Entity passed through the wall");
            assertEquals(80, entity.getPosition().y(), 1e-6);
            assertTrue(service.gravity().isOnGround(entity));
            service.gravity().set(entity, new GravityState(GravityDirection.UP, 15));
            entity.setVelocity(Vec.ZERO);
            entity.tick(2550);
            assertTrue(entity.getVelocity().y() > 0);
            assertEquals(0, entity.getVelocity().x(), 1e-6);
        }
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-6);
        assertEquals(expected.y(), actual.y(), 1e-6);
        assertEquals(expected.z(), actual.z(), 1e-6);
    }

    @Test
    void aCancelledTransferRestoresGravityScaleAndVelocity() {
        var source = world();
        var target = world();
        try (var service = new PortalService()) {
            var entity = new LivingEntity(EntityType.ARMOR_STAND);
            entity.setInstance(source, new Pos(1, 80, 2)).join();
            var state = new GravityState(GravityDirection.WEST, 12);
            service.gravity().set(entity, state);
            entity.setVelocity(new Vec(1, 2, 3));
            service.events()
                    .addListener(
                            net.minestom.server.event.instance.AddEntityToInstanceEvent.class,
                            event -> {
                                if (event.getInstance() == target) event.setCancelled(true);
                            });
            var handle =
                    service.create(
                            source,
                            target,
                            p ->
                                    p.rotation(Quaternion.axisAngle(new Vec3(0, 1, 0), Math.PI / 2))
                                            .scale(.5)
                                            .extraNbt(
                                                    PortalOptions.builder()
                                                            .changesGravity(true)
                                                            .build()
                                                            .encode()));
            assertThrows(IllegalStateException.class, () -> handle.teleport(entity).join());
            assertSame(source, entity.getInstance());
            assertEquals(state, service.gravity().get(entity));
            assertEquals(1, entity.getAttributeValue(Attribute.SCALE), 1e-6);
            assertEquals(new Vec(1, 2, 3), entity.getVelocity());
        }
    }

    @Test
    void livingTraversalTransformsEyeFacingVelocityAndOnlyThePortalScaleModifier() {
        var source = world();
        var target = world();
        try (var service = new PortalService()) {
            var handle =
                    service.create(
                            source,
                            target,
                            p ->
                                    p.origin(new Vec3(0, 72, 0))
                                            .destination(new Vec3(20, 80, 0))
                                            .scale(.5)
                                            .rotation(
                                                    Quaternion.axisAngle(
                                                            new Vec3(0, 1, 0), Math.PI / 2)));
            var entity = new LivingEntity(EntityType.ARMOR_STAND);
            entity.getAttribute(Attribute.SCALE).setBaseValue(1.25);
            var external =
                    new AttributeModifier(
                            Key.key("test:external"), .25, AttributeOperation.ADD_VALUE);
            entity.getAttribute(Attribute.SCALE).addModifier(external);
            entity.setInstance(source, new Pos(0, 70, 1, 180, 0)).join();
            entity.setVelocity(new Vec(0, 0, -4));
            var expectedEye =
                    handle.definition()
                            .transformPoint(
                                    new Vec3(
                                            0,
                                            70
                                                    + entity.getEyeHeight()
                                                            * entity.getAttributeValue(
                                                                    Attribute.SCALE),
                                            1));

            handle.teleport(entity).join();

            assertSame(target, entity.getInstance());
            assertEquals(expectedEye.x(), entity.getPosition().x(), 1e-6);
            assertEquals(
                    expectedEye.y(),
                    entity.getPosition().y()
                            + entity.getEyeHeight() * entity.getAttributeValue(Attribute.SCALE),
                    1e-6);
            assertEquals(expectedEye.z(), entity.getPosition().z(), 1e-6);
            assertEquals(.75, entity.getAttributeValue(Attribute.SCALE), 1e-6);
            assertEquals(1.25, entity.getAttribute(Attribute.SCALE).getBaseValue(), 1e-6);
            assertTrue(entity.getAttribute(Attribute.SCALE).modifiers().contains(external));
            assertEquals(-2, entity.getVelocity().x(), 1e-6);
            assertEquals(0, entity.getVelocity().z(), 1e-6);
            assertEquals(90, entity.getPosition().yaw(), 1e-4);
        }
    }

    @Test
    void aNonLivingEntityCanBeTransferredAndTheSameWorldUsesTeleport() {
        var source = world();
        var target = world();
        try (var service = new PortalService()) {
            var handle = service.create(source, target, p -> p.destination(new Vec3(10, 0, 0)));
            var item = new Entity(EntityType.ITEM);
            item.setInstance(source, new Pos(1, 70, 2)).join();
            item.setVelocity(new Vec(1, 2, 3));
            handle.teleport(item).join();
            assertSame(target, item.getInstance());
            assertEquals(new Vec(11, 70, 2), item.getPosition().asVec());
            assertEquals(new Vec(1, 2, 3), item.getVelocity());
            var sameWorld = service.create(target, target, p -> p.destination(new Vec3(-10, 0, 0)));
            sameWorld.teleport(item).join();
            assertEquals(new Vec(1, 70, 2), item.getPosition().asVec());
            var gravityPortal =
                    service.create(
                            target,
                            target,
                            p ->
                                    p.origin(new Vec3(1, 70, 2))
                                            .destination(new Vec3(1, 70, 2))
                                            .rotation(
                                                    Quaternion.axisAngle(
                                                            new Vec3(1, 0, 0), Math.PI / 2))
                                            .extraNbt(
                                                    PortalOptions.builder()
                                                            .changesGravity(true)
                                                            .build()
                                                            .encode()));
            gravityPortal.teleport(item).join();
            assertEquals(GravityState.DEFAULT, service.gravity().get(item));
            assertEquals(90, item.getPosition().pitch(), 1e-5);
        }
    }

    @Test
    void crossingUsesTheActualShapeAndRejectsDisabledPortals() {
        var source = world();
        try (var service = new PortalService()) {
            var handle =
                    service.create(
                            source,
                            source,
                            p ->
                                    p.size(4, 4)
                                            .extraNbt(
                                                    PortalOptions.builder()
                                                            .triangles(-1, -1, 1, -1, 0, 1)
                                                            .build()
                                                            .encode()));
            assertTrue(handle.crosses(new Vec(0, 0, 1), new Vec(0, 0, -1)));
            assertFalse(handle.crosses(new Vec(1.8, 1.8, 1), new Vec(1.8, 1.8, -1)));
            assertFalse(handle.crosses(new Vec(0, 0, -1), new Vec(0, 0, 1)));
            handle.update(
                    p -> p.extraNbt(PortalOptions.builder().teleportable(false).build().encode()));
            assertFalse(handle.crosses(new Vec(0, 0, 1), new Vec(0, 0, -1)));
            var entity = new Entity(EntityType.ITEM);
            entity.setInstance(source, new Pos(0, 70, 0)).join();
            assertThrows(IllegalStateException.class, () -> handle.teleport(entity));
        }
    }

    @Test
    void wrongSourceAndRemovedPortalsCannotMoveAnEntity() {
        var source = world();
        var target = world();
        try (var service = new PortalService()) {
            var handle = service.create(source, target, p -> {});
            var entity = new Entity(EntityType.ITEM);
            entity.setInstance(target, new Pos(0, 70, 0)).join();
            assertThrows(IllegalArgumentException.class, () -> handle.teleport(entity));
            handle.close();
            assertThrows(IllegalStateException.class, () -> handle.teleport(entity));
        }
    }
}
