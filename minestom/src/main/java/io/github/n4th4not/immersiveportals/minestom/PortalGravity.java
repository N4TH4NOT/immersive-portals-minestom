package io.github.n4th4not.immersiveportals.minestom;

import io.github.n4th4not.immersiveportals.protocol.*;

import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.property.ServerProperties;

import java.util.*;

/**
 * Server-owned gravity, geometry and passive directional physics. Mutate on the entity's thread.
 */
public final class PortalGravity implements AutoCloseable {
    private static final Aerodynamics COLLISIONS_ONLY = new Aerodynamics(0, 1, 1);

    private static final class Entry {
        volatile GravityState state;
        final Aerodynamics aerodynamics;
        final BoundingBox box;

        Entry(LivingEntity entity, GravityState state) {
            this.state = state;
            aerodynamics = entity.getAerodynamics();
            box = entity.getBoundingBox();
        }
    }

    private final PortalService service;
    private final Map<LivingEntity, Entry> entries =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private volatile boolean closed;

    PortalGravity(PortalService service) {
        this.service = service;
    }

    public GravityState get(Entity entity) {
        Entry entry = entries.get(entity);
        return entry == null ? GravityState.DEFAULT : entry.state;
    }

    public void set(LivingEntity entity, GravityDirection direction) {
        set(entity, new GravityState(direction, get(entity).strength()));
    }

    public void set(LivingEntity entity, GravityState state) {
        set(entity, state, true);
    }

    void restore(LivingEntity entity, GravityState state) {
        set(entity, state, false);
    }

    private void set(LivingEntity entity, GravityState state, boolean validatePlayer) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(state);
        if (closed || entity.isRemoved())
            throw new IllegalStateException("Gravity service or entity is closed");
        if (validatePlayer
                && entity instanceof Player player
                && !state.equals(GravityState.DEFAULT)
                && !service.supportsGravity(player))
            throw new IllegalStateException(
                    "Player has not negotiated the Gravity Changer feature");
        if (state.equals(GravityState.DEFAULT)) {
            reset(entity);
            return;
        }
        Entry entry = entries.computeIfAbsent(entity, ignored -> new Entry(entity, state));
        entry.state = state;
        // Keep Minestom's swept collisions, suppress its world-Y acceleration/drag.
        // NoGravity metadata is left to the application and remains visible to the client.
        entity.setAerodynamics(COLLISIONS_ONLY);
        refreshBounds(entity, entry);
    }

    public void reset(LivingEntity entity) {
        Entry entry = entries.remove(entity);
        if (entry == null) return;
        entity.setAerodynamics(entry.aerodynamics);
        entity.setBoundingBox(entry.box);
    }

    public Vec3 eyeOffset(Entity entity) {
        double scale =
                entity instanceof LivingEntity living
                        ? living.getAttributeValue(Attribute.SCALE)
                        : 1;
        return get(entity).direction().toWorld(new Vec3(0, entity.getEyeHeight() * scale, 0));
    }

    public Vec3 eyePosition(Entity entity) {
        return vector(entity.getPosition()).add(eyeOffset(entity));
    }

    public Vec3 lookDirection(Entity entity) {
        return get(entity).direction().toWorld(vector(entity.getPosition().direction()));
    }

    public BoundingBox boundingBox(Entity entity) {
        Entry entry = entries.get(entity);
        if (entry == null) return entity.getBoundingBox();
        var poseBox = BoundingBox.fromPose(entity.getPose());
        return rotated(
                poseBox == null ? entry.box : poseBox,
                get(entity).direction(),
                ((LivingEntity) entity).getAttributeValue(Attribute.SCALE));
    }

    /** A support probe along local down, including horizontal walls and ceilings. */
    public boolean isOnGround(Entity entity) {
        if (entity.getInstance() == null || !entity.hasPhysics()) return false;
        Vec3 delta = get(entity).direction().vector().multiply(1e-4);
        var result =
                CollisionUtils.handlePhysics(
                        entity.getInstance(),
                        entity.getChunk(),
                        boundingBox(entity),
                        entity.getPosition(),
                        vec(delta),
                        null,
                        false);
        return result.newPosition().distanceSquared(entity.getPosition())
                < delta.lengthSquared() * .99;
    }

    private static BoundingBox rotated(BoundingBox box, GravityDirection direction, double scale) {
        Vec3 a = direction.toWorld(vector(box.relativeStart()).multiply(scale));
        Vec3 b = direction.toWorld(vector(box.relativeEnd()).multiply(scale));
        return new BoundingBox(
                new Vec(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new Vec(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }

    private static void refreshBounds(LivingEntity entity, Entry entry) {
        var poseBox = BoundingBox.fromPose(entity.getPose());
        entity.setBoundingBox(
                rotated(
                        poseBox == null ? entry.box : poseBox,
                        entry.state.direction(),
                        entity.getAttributeValue(Attribute.SCALE)));
    }

    void tick(Entity entity) {
        Entry entry = entries.get(entity);
        if (entry == null
                || !(entity instanceof LivingEntity living)
                || entity.getInstance() == null
                || entity.isRemoved()) return;
        refreshBounds(living, entry);
        // As with ordinary Minestom, player movement is simulated by the client. Applying a
        // second acceleration through velocity packets would double its gravity.
        if (entity instanceof Player || entity.getVehicle() != null) return;
        double rate = ServerProperties.SERVER_TICKS_PER_SECOND.get();
        Vec3 local =
                entry.state.direction().toLocal(vector(entity.getVelocity()).multiply(1 / rate));
        double drag = entry.aerodynamics.horizontalAirResistance();
        if (isOnGround(entity)) {
            Vec3 support =
                    vector(entity.getPosition())
                            .add(entry.state.direction().vector().multiply(.5000001));
            drag *=
                    entity.getInstance()
                            .getBlock(
                                    (int) Math.floor(support.x()),
                                    (int) Math.floor(support.y()),
                                    (int) Math.floor(support.z()))
                            .friction();
        }
        double gravity =
                entity.hasNoGravity()
                        ? 0
                        : entry.aerodynamics.gravity()
                                * entry.state.strength()
                                / GravityState.DEFAULT_STRENGTH;
        Vec3 next =
                new Vec3(
                        local.x() * drag,
                        (local.y() - gravity) * entry.aerodynamics.verticalAirResistance(),
                        local.z() * drag);
        entity.setVelocity(vec(entry.state.direction().toWorld(next).multiply(rate)));
    }

    void cleanup() {
        synchronized (entries) {
            entries.keySet().removeIf(Entity::isRemoved);
        }
    }

    @Override
    public void close() {
        closed = true;
        List<LivingEntity> snapshot;
        synchronized (entries) {
            snapshot = List.copyOf(entries.keySet());
        }
        for (LivingEntity entity : snapshot) reset(entity);
    }

    private static Vec3 vector(Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }

    private static Vec vec(Vec3 point) {
        return new Vec(point.x(), point.y(), point.z());
    }
}
