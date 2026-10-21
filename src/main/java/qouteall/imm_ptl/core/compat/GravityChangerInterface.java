package qouteall.imm_ptl.core.compat;

import io.github.n4th4not.immersiveportals.protocol.GravityState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.DQuaternion;

public class GravityChangerInterface {
    public static Invoker invoker = new Invoker();

    public static void init() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("gravity_changer")) {
            invoker = new qouteall.imm_ptl.core.compat.gravity.OnGravityChangerPresent();
            qouteall.q_misc_util.Helper.log("Gravity Changer 26.2 portal integration enabled");
        }
    }

    public static class Invoker {
        public boolean isGravityChangerPresent() {
            return false;
        }

        public Vec3 getEyeOffset(Entity entity) {
            return new Vec3(0, entity.getEyeHeight(), 0);
        }

        public Direction getGravityDirection(Entity entity) {
            return Direction.DOWN;
        }

        public Direction getBaseGravityDirection(Entity entity) {
            return Direction.DOWN;
        }

        public GravityState getState(Entity entity) {
            return GravityState.DEFAULT;
        }

        public void applyState(Entity entity, GravityState state, boolean snapVisualRotation) {}

        public void setClientPlayerGravityDirection(Player player, Direction direction) {
            warnGravityChangerNotPresent();
        }

        public void setBaseGravityDirectionServer(Entity entity, Direction direction) {
            // nothing
        }

        @Nullable
        public DQuaternion getExtraCameraRotation(Direction gravityDirection) {
            return null;
        }

        public Vec3 getWorldVelocity(Entity entity) {
            return entity.getDeltaMovement();
        }

        public void setWorldVelocity(Entity entity, Vec3 newVelocity) {
            entity.setDeltaMovement(newVelocity);
        }

        public Vec3 transformPlayerToWorld(Direction gravity, Vec3 vec3d) {
            return vec3d;
        }

        public Vec3 transformWorldToPlayer(Direction gravity, Vec3 vec3d) {
            return vec3d;
        }

        public Direction transformDirPlayerToWorld(Direction gravity, Direction direction) {
            return direction;
        }

        public Direction transformDirWorldToPlayer(Direction gravity, Direction direction) {
            return direction;
        }
    }

    private static boolean warned = false;

    @Environment(EnvType.CLIENT)
    private static void warnGravityChangerNotPresent() {
        if (!warned) {
            warned = true;
            CHelper.printChat(
                    Component.translatable("imm_ptl.missing_gravity_changer")
                            .append(
                                    McHelper.getLinkText(
                                            "https://modrinth.com/mod/gravity-changer-unofficial-port")));
        }
    }
}
