package io.github.n4th4not.immersiveportals.minestom;

import io.github.n4th4not.immersiveportals.protocol.PortalDefinition;
import io.github.n4th4not.immersiveportals.protocol.Vec3;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/** A registered portal. Closing it removes it from every connected client. */
public final class PortalHandle implements AutoCloseable {
    private final PortalService service;
    private final UUID id;

    PortalHandle(PortalService service, UUID id) {
        this.service = service;
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public PortalDefinition definition() {
        return service.find(id)
                .orElseThrow(() -> new IllegalStateException("Portal is no longer registered"));
    }

    public void update(UnaryOperator<PortalDefinition.Builder> update) {
        service.replace(id, update.apply(definition().toBuilder()).id(id).build());
    }

    /** Check a swept eye-position segment against the actual aperture, including custom shapes. */
    public boolean crosses(Point previousEyePosition, Point eyePosition) {
        PortalDefinition portal = definition();
        PortalOptions options = PortalOptions.of(portal);
        return portal.kind() == PortalDefinition.Kind.PORTAL
                && options.teleportable()
                && Double.isFinite(
                        options.intersection(
                                portal,
                                new Vec3(
                                        previousEyePosition.x(),
                                        previousEyePosition.y(),
                                        previousEyePosition.z()),
                                new Vec3(eyePosition.x(), eyePosition.y(), eyePosition.z())));
    }

    /**
     * Explicit server-authorized traversal of one entity from this portal's source instance.
     * Applies eye position, facing, velocity and living-entity scale. The application decides when
     * to call this; no autonomous movement or aperture check is imposed here. Detach vehicles and
     * passengers before moving their individual entities.
     */
    public CompletableFuture<Void> teleport(Entity entity) {
        return service.teleport(id, entity);
    }

    @Override
    public void close() {
        service.remove(id);
    }
}
