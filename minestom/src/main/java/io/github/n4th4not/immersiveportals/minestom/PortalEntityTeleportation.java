package io.github.n4th4not.immersiveportals.minestom;

import io.github.n4th4not.immersiveportals.protocol.PortalDefinition;
import io.github.n4th4not.immersiveportals.protocol.Vec3;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.*;
import net.minestom.server.instance.Instance;

import java.util.concurrent.CompletableFuture;

final class PortalEntityTeleportation {
    private static final Key SCALE_MODIFIER = Key.key("iportal:scaling");

    static CompletableFuture<Void> teleport(
            Entity entity,
            PortalDefinition portal,
            Instance target,
            PortalGravity gravity,
            Vec3 sourceEye) {
        PortalOptions options = PortalOptions.of(portal);
        if (entity.isRemoved()
                || portal.kind() != PortalDefinition.Kind.PORTAL
                || !options.teleportable())
            throw new IllegalStateException("Entity or portal cannot be teleported");
        if (entity.getVehicle() != null || entity.hasPassenger())
            throw new IllegalStateException(
                    "Detach vehicles and passengers before teleporting individual entities");
        LivingEntity living = entity instanceof LivingEntity value ? value : null;
        Vec3 destinationEye = portal.transformPoint(sourceEye);
        var oldGravity = gravity.get(entity);
        var nextGravity =
                living != null && options.changesGravity()
                        ? oldGravity.rotated(portal.rotation())
                        : oldGravity;
        Vec3 direction =
                nextGravity
                        .direction()
                        .toLocal(portal.rotation().rotate(gravity.lookDirection(entity)));
        Vec3 velocity = portal.transformVector(vector(entity.getVelocity()));
        AttributeInstance attribute = living == null ? null : living.getAttribute(Attribute.SCALE);
        var previous =
                attribute == null
                        ? java.util.Optional.<AttributeModifier>empty()
                        : attribute.modifiers().stream()
                                .filter(m -> m.id().equals(SCALE_MODIFIER))
                                .findFirst();
        if (attribute != null && options.changesScale()) {
            double factor = previous.map(m -> m.amount() + 1).orElse(1.0) * portal.scale();
            if (!Double.isFinite(factor) || factor < 1e-6 || factor > 1e6)
                throw new IllegalArgumentException(
                        "Resulting portal scale is outside the supported range");
            attribute.addModifier(
                    new AttributeModifier(
                            SCALE_MODIFIER, factor - 1, AttributeOperation.ADD_MULTIPLIED_TOTAL));
        }
        try {
            if (living != null && !nextGravity.equals(oldGravity)) gravity.set(living, nextGravity);
        } catch (RuntimeException error) {
            if (attribute != null && options.changesScale()) {
                attribute.removeModifier(SCALE_MODIFIER);
                previous.ifPresent(attribute::addModifier);
            }
            throw error;
        }
        double newScale = living == null ? 1 : living.getAttributeValue(Attribute.SCALE);
        Vec3 feet =
                destinationEye.subtract(
                        nextGravity
                                .direction()
                                .toWorld(new Vec3(0, entity.getEyeHeight() * newScale, 0)));
        Pos position =
                new Pos(
                        feet.x(),
                        feet.y(),
                        feet.z(),
                        (float) Math.toDegrees(Math.atan2(-direction.x(), direction.z())),
                        (float)
                                -Math.toDegrees(
                                        Math.asin(Math.max(-1, Math.min(1, direction.y())))));
        Runnable restoreState =
                () -> {
                    if (attribute != null && options.changesScale()) {
                        attribute.removeModifier(SCALE_MODIFIER);
                        previous.ifPresent(attribute::addModifier);
                    }
                    if (living != null && !nextGravity.equals(oldGravity))
                        gravity.restore(living, oldGravity);
                };
        try {
            CompletableFuture<Void> transfer =
                    entity.getInstance() == target
                            ? entity.teleport(position)
                            : entity.setInstance(target, position);
            if (transfer == null)
                throw new IllegalStateException("Instance transfer was cancelled");
            return transfer.thenRun(
                            () ->
                                    entity.setVelocity(
                                            new Vec(velocity.x(), velocity.y(), velocity.z())))
                    .whenComplete(
                            (ignored, error) -> {
                                if (error != null) restoreState.run();
                            });
        } catch (RuntimeException error) {
            restoreState.run();
            throw error;
        }
    }

    private static Vec3 vector(net.minestom.server.coordinate.Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }
}
