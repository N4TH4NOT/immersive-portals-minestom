package io.github.n4th4not.immersiveportals.minestom;

import static org.junit.jupiter.api.Assertions.*;

import io.github.n4th4not.immersiveportals.protocol.*;

import org.junit.jupiter.api.Test;

class PortalOptionsTest {
    @Test
    void triangleApertureRejectsTheMissingCornersOfItsBoundingRectangle() {
        var options =
                PortalOptions.builder().teleportable(false).triangles(-1, -1, 1, -1, 0, 1).build();
        var portal =
                PortalDefinition.builder("demo:a", "demo:b")
                        .size(4, 4)
                        .extraNbt(options.encode())
                        .build();
        var decoded = PortalOptions.of(portal);
        assertFalse(decoded.teleportable());
        assertTrue(decoded.contains(portal, new Vec3(0, 0, 0)));
        assertFalse(decoded.contains(portal, new Vec3(1.8, 1.8, 0)));
    }

    @Test
    void rayIntersectionHandlesParallelAxesAndBehindTheCamera() {
        assertEquals(
                2,
                RemoteInteractionEvent.rayBox(
                        Vec3.ZERO,
                        new Vec3(0, 0, -1),
                        new Vec3(-1, -1, -3),
                        new Vec3(1, 1, -2),
                        5));
        assertTrue(
                Double.isNaN(
                        RemoteInteractionEvent.rayBox(
                                Vec3.ZERO,
                                new Vec3(0, 0, -1),
                                new Vec3(2, -1, -3),
                                new Vec3(3, 1, -2),
                                5)));
        assertTrue(
                Double.isNaN(
                        RemoteInteractionEvent.rayBox(
                                Vec3.ZERO,
                                new Vec3(0, 0, -1),
                                new Vec3(-1, -1, 2),
                                new Vec3(1, 1, 3),
                                5)));
    }

    @Test
    void invalidMeshCoordinatesDoNotReachTheNetwork() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PortalOptions.builder().triangles(0, 0, 1, 0, Double.POSITIVE_INFINITY, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortalOptions.builder().triangles(0, 0, .5, .5, 1, 1));
    }

    @Test
    void boxesUseTheFrontEntryOrBackExitInsteadOfTheCentralPlane() {
        var portal = PortalDefinition.builder("demo:a", "demo:b").size(2, 2).build();
        var outward = PortalOptions.builder().box(2, true).build();
        var inward = PortalOptions.builder().box(2, false).build();
        assertEquals(.25, outward.intersection(portal, new Vec3(0, 0, 2), new Vec3(0, 0, -2)));
        assertEquals(.75, inward.intersection(portal, new Vec3(0, 0, 2), new Vec3(0, 0, -2)));
        assertTrue(Double.isNaN(outward.intersection(portal, Vec3.ZERO, new Vec3(0, 0, -2))));
        assertEquals(.5, inward.intersection(portal, Vec3.ZERO, new Vec3(0, 0, -2)));
        assertTrue(
                Double.isNaN(outward.intersection(portal, new Vec3(2, 0, 2), new Vec3(2, 0, -2))));
    }
}
