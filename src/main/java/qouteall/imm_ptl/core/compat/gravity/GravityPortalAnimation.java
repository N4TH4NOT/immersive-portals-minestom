package qouteall.imm_ptl.core.compat.gravity;

import com.moigferdsrte.gravitychanger.client.GravityAnimationEntity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

/** Portal rotation already has its own interpolation; do not apply a second gravity animation. */
@Environment(EnvType.CLIENT)
public final class GravityPortalAnimation {
    private static boolean snapping;

    public static boolean isSnapping() {
        return snapping;
    }

    public static void snap(Entity entity, Direction direction) {
        boolean previous = snapping;
        snapping = true;
        try {
            ((GravityAnimationEntity) entity).gravitychanger$getVisualGravityRotation(direction);
        } finally {
            snapping = previous;
        }
    }
}
