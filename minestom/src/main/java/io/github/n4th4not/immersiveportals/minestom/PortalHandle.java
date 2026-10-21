package io.github.n4th4not.immersiveportals.minestom;

import io.github.n4th4not.immersiveportals.protocol.PortalDefinition;

import java.util.UUID;
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

    @Override
    public void close() {
        service.remove(id);
    }
}
