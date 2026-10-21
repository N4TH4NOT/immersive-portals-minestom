package qouteall.imm_ptl.core.compat.gravity;

import com.moigferdsrte.gravitychanger.attributes.DirectionalAttribute;
import com.moigferdsrte.gravitychanger.init.ModAttributes;
import com.moigferdsrte.gravitychanger.util.GravityDirectionUtil;
import com.moigferdsrte.gravitychanger.util.RotationUtil;

import io.github.n4th4not.immersiveportals.protocol.GravityDirection;
import io.github.n4th4not.immersiveportals.protocol.GravityState;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.q_misc_util.my_util.DQuaternion;

/** Adapter for NightEpiphany's attribute-based 26.2 port, loaded only when it is installed. */
public final class OnGravityChangerPresent extends GravityChangerInterface.Invoker {
    @Override
    public boolean isGravityChangerPresent() {
        return true;
    }

    @Override
    public Direction getGravityDirection(Entity entity) {
        return GravityDirectionUtil.getGravityDirection(entity);
    }

    @Override
    public Direction getBaseGravityDirection(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return Direction.DOWN;
        var attribute = living.getAttribute(ModAttributes.GRAVITY_DIRECTION);
        return attribute == null
                ? Direction.DOWN
                : DirectionalAttribute.toDirection(attribute.getBaseValue());
    }

    @Override
    public Vec3 getEyeOffset(Entity entity) {
        return RotationUtil.vecPlayerToWorld(
                0.0, (double) entity.getEyeHeight(), 0.0, getGravityDirection(entity));
    }

    @Override
    public void setClientPlayerGravityDirection(Player player, Direction direction) {
        setBaseGravityDirectionServer(player, direction);
        GravityPortalAnimation.snap(player, direction);
    }

    @Override
    public void setBaseGravityDirectionServer(Entity entity, Direction direction) {
        if (entity instanceof LivingEntity living) {
            var attribute = living.getAttribute(ModAttributes.GRAVITY_DIRECTION);
            if (attribute != null) {
                // The mod's item helper recenters/lifts entities. A portal already provides its
                // transformed eye position, so preserve feet and update only state and bounds here.
                double value = DirectionalAttribute.valueOf(direction);
                boolean unchanged = attribute.getBaseValue() == value;
                attribute.setBaseValue(value);
                // Rejected portal predictions can require resending an unchanged server value.
                if (unchanged && entity instanceof net.minecraft.server.level.ServerPlayer player)
                    player.connection.send(
                            new net.minecraft.network.protocol.game
                                    .ClientboundUpdateAttributesPacket(
                                    player.getId(), java.util.List.of(attribute)));
                entity.setBoundingBox(
                        RotationUtil.makeBoxFromDimensions(
                                entity.getDimensions(entity.getPose()),
                                getGravityDirection(entity),
                                entity.position()));
            }
        }
    }

    @Override
    public GravityState getState(Entity entity) {
        return new GravityState(
                GravityDirection.fromId(getGravityDirection(entity).get3DDataValue()),
                GravityDirectionUtil.getGravityStrength(entity));
    }

    @Override
    public void applyState(Entity entity, GravityState state, boolean snapVisualRotation) {
        setBaseGravityDirectionServer(
                entity, Direction.from3DDataValue(state.direction().ordinal()));
        GravityDirectionUtil.setGravityStrength(entity, state.strength());
        if (snapVisualRotation && entity.level().isClientSide())
            GravityPortalAnimation.snap(entity, getGravityDirection(entity));
    }

    @Override
    public DQuaternion getExtraCameraRotation(Direction direction) {
        return direction == Direction.DOWN
                ? null
                : DQuaternion.fromMcQuaternion(RotationUtil.getWorldRotationQuaternion(direction));
    }

    // This port stores velocity in WORLD coordinates; inherited velocity access is intentional.
    @Override
    public Vec3 transformPlayerToWorld(Direction gravity, Vec3 vector) {
        return RotationUtil.vecPlayerToWorld(vector, gravity);
    }

    @Override
    public Vec3 transformWorldToPlayer(Direction gravity, Vec3 vector) {
        return RotationUtil.vecWorldToPlayer(vector, gravity);
    }

    @Override
    public Direction transformDirPlayerToWorld(Direction gravity, Direction direction) {
        Vec3 v = transformPlayerToWorld(gravity, Vec3.atLowerCornerOf(direction.getUnitVec3i()));
        return Direction.getApproximateNearest(v.x, v.y, v.z);
    }

    @Override
    public Direction transformDirWorldToPlayer(Direction gravity, Direction direction) {
        Vec3 v = transformWorldToPlayer(gravity, Vec3.atLowerCornerOf(direction.getUnitVec3i()));
        return Direction.getApproximateNearest(v.x, v.y, v.z);
    }
}
