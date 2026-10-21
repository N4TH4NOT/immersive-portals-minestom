package io.github.n4th4not.immersiveportals.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class GravityProtocolTest {
    @Test
    void allFramesPreserveHandednessAndInvertExactly() {
        for (var direction : GravityDirection.values()) {
            Vec3 vector = new Vec3(1.25, -2.5, 3.75);
            assertEquals(vector, direction.toLocal(direction.toWorld(vector)));
            assertEquals(vector.lengthSquared(), direction.toWorld(vector).lengthSquared(), 1e-10);
            assertVector(direction.vector(), direction.toWorld(new Vec3(0, -1, 0)));
            assertVector(
                    direction.toWorld(new Vec3(0, 0, 1)),
                    direction
                            .toWorld(new Vec3(1, 0, 0))
                            .cross(direction.toWorld(new Vec3(0, 1, 0))));
        }
        assertEquals(
                GravityDirection.NORTH,
                GravityDirection.DOWN.rotated(
                        Quaternion.axisAngle(new Vec3(1, 0, 0), Math.PI / 2)));
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-10);
        assertEquals(expected.y(), actual.y(), 1e-10);
        assertEquals(expected.z(), actual.z(), 1e-10);
    }

    @Test
    void stateAndAtomicCorrectionsRoundTrip() {
        for (var direction : GravityDirection.values()) {
            var state = new GravityState(direction, 12);
            var message = new BridgeMessage.Gravity("demo:world", 23, UUID.randomUUID(), state);
            assertEquals(message, BridgeCodec.decode(BridgeCodec.encode(message)));
            var correction =
                    new BridgeMessage.Correction(
                            "demo:world", Vec3.ZERO, new Vec3(1, 2, 3), .5, 23, -30, state);
            assertEquals(correction, BridgeCodec.decode(BridgeCodec.encode(correction)));
        }
        var hello =
                new BridgeMessage.Hello(
                        BridgeCodec.VERSION,
                        BridgeCodec.MINECRAFT_PROTOCOL,
                        BridgeCodec.FEATURE_GRAVITY);
        assertEquals(hello, BridgeCodec.decode(BridgeCodec.encode(hello)));
    }

    @Test
    void invalidDirectionsAndStrengthsAreRejectedOnTheWire() {
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 4.9, 15.1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GravityState(GravityDirection.DOWN, invalid));
        byte[] encoded =
                BridgeCodec.encode(
                        new BridgeMessage.Gravity(
                                "demo:a", 1, UUID.randomUUID(), GravityState.DEFAULT));
        encoded[encoded.length - 9] = 6;
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> GravityDirection.fromId(-1));
    }
}
