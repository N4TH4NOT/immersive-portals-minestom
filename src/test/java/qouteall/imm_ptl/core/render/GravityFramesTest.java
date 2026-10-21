package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import com.moigferdsrte.gravitychanger.util.RotationUtil;

import io.github.n4th4not.immersiveportals.protocol.GravityDirection;
import io.github.n4th4not.immersiveportals.protocol.Vec3;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class GravityFramesTest {
    @Test
    void minestomFramesMatchThePublishedModInEveryDirection() {
        for (Direction nativeDirection : Direction.values()) {
            var direction = GravityDirection.fromId(nativeDirection.get3DDataValue());
            var nativeInput = new net.minecraft.world.phys.Vec3(1.25, -2.5, 3.75);
            Vec3 input = new Vec3(nativeInput.x, nativeInput.y, nativeInput.z);
            var world = RotationUtil.vecPlayerToWorld(nativeInput, nativeDirection);
            var local = RotationUtil.vecWorldToPlayer(nativeInput, nativeDirection);
            assertEquals(new Vec3(world.x, world.y, world.z), direction.toWorld(input));
            assertEquals(new Vec3(local.x, local.y, local.z), direction.toLocal(input));
            var transformed =
                    RotationUtil.getEntityRotationQuaternion(nativeDirection)
                            .transform(1.25f, -2.5f, 3.75f, new org.joml.Vector3f());
            assertEquals(world.x, transformed.x, 1e-5);
            assertEquals(world.y, transformed.y, 1e-5);
            assertEquals(world.z, transformed.z, 1e-5);
        }
    }
}
