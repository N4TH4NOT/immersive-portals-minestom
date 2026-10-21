package qouteall.imm_ptl.core.network.bridge;

import io.github.n4th4not.immersiveportals.protocol.BridgeMessage;
import io.github.n4th4not.immersiveportals.protocol.GravityState;

import net.minecraft.world.entity.Entity;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, world-specific state, including packets arriving before their entity is spawned. */
final class BridgeGravity {
    private record Key(String world, int id, UUID uuid) {}

    private static final class Entry {
        final GravityState state;
        final long received = System.nanoTime();
        WeakReference<Entity> applied = new WeakReference<>(null);

        Entry(GravityState state) {
            this.state = state;
        }
    }

    private static final Map<Key, Entry> STATES = new LinkedHashMap<>();

    static void receive(BridgeMessage.Gravity message) {
        if (!GravityChangerInterface.invoker.isGravityChangerPresent()) return;
        var key = new Key(message.world(), message.entityId(), message.entity());
        STATES.remove(key);
        STATES.put(key, new Entry(message.state()));
        if (STATES.size() > 4096) STATES.remove(STATES.keySet().iterator().next());
        apply(key, STATES.get(key));
    }

    static void tick() {
        long now = System.nanoTime();
        STATES.entrySet().removeIf(e -> now - e.getValue().received > 60_000_000_000L);
        STATES.forEach(BridgeGravity::apply);
    }

    private static void apply(Key key, Entry entry) {
        for (var world : ClientWorldLoader.getClientWorlds()) {
            if (!world.dimension().identifier().toString().equals(key.world())) continue;
            var entity = world.getEntity(key.id());
            if (entity != null
                    && entity.getUUID().equals(key.uuid())
                    && entry.applied.get() != entity) {
                GravityChangerInterface.invoker.applyState(entity, entry.state, false);
                entry.applied = new WeakReference<>(entity);
            }
            return;
        }
    }

    static void clear() {
        for (Entry entry : STATES.values()) {
            Entity entity = entry.applied.get();
            if (entity != null)
                GravityChangerInterface.invoker.applyState(entity, GravityState.DEFAULT, true);
        }
        STATES.clear();
    }
}
