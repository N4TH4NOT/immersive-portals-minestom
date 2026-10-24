package io.github.n4th4not.immersiveportals.minestom;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.*;

import java.util.*;
import java.util.function.Consumer;

/** Client-known state for one remote view. Snapshot wire bytes, never mutable entity values. */
final class RemoteEntityTracker {
    private static final class Entry {
        final UUID uuid;
        final EntityType type;
        byte[] position, head;
        long positionTick;
        final Map<Integer, byte[]> metadata = new HashMap<>();
        final Map<Attribute, byte[]> attributes = new HashMap<>();
        final Map<EquipmentSlot, byte[]> equipment = new EnumMap<>(EquipmentSlot.class);

        Entry(SpawnEntityPacket spawn, long tick) {
            positionTick = tick;
            uuid = spawn.uuid();
            type = spawn.type();
            // Spawn already carries position, packed velocity and byte-precision rotations.
            var bytes = NetworkBuffer.makeArray(
                    SpawnEntityPacket.SERIALIZER, spawn, MinecraftServer.getRegistries());
            var decoded = NetworkBuffer.wrap(bytes, 0, bytes.length, MinecraftServer.getRegistries())
                    .read(SpawnEntityPacket.SERIALIZER);
            position = NativePackets.encode(new EntityPositionSyncPacket(spawn.entityId(),
                    decoded.position(), decoded.velocity(), decoded.position().yaw(),
                    decoded.position().pitch(), false));
            head = NativePackets.encode(new EntityHeadLookPacket(spawn.entityId(), decoded.headRot()));
            for (var slot : EquipmentSlot.values())
                equipment.put(slot, NativePackets.encode(
                        new EntityEquipmentPacket(spawn.entityId(), Map.of(slot, ItemStack.AIR))));
        }
    }

    private final Map<Integer, Entry> entities = new HashMap<>();

    /** Returns true when the client needs a fresh gravity state as well as an entity spawn. */
    boolean update(Entity entity, boolean onGround, long tick, Consumer<ServerPacket> send) {
        int id = entity.getEntityId();
        Entry entry = entities.get(id);
        boolean appeared = entry == null || !entry.uuid.equals(entity.getUuid())
                || entry.type != entity.getEntityType();
        if (appeared) {
            if (entry != null) send.accept(new DestroyEntitiesPacket(List.of(id)));
            int data = entity.getEntityMeta() instanceof
                    net.minestom.server.entity.metadata.ObjectDataProvider provider
                    ? provider.getObjectData() : 0;
            var spawn = new SpawnEntityPacket(id, entity.getUuid(), entity.getEntityType(),
                    entity.getPosition(), entity.getHeadRotation(), data, entity.getVelocity().div(20));
            entry = new Entry(spawn, tick);
            entities.put(id, entry);
            send.accept(spawn);
        }
        var position = new EntityPositionSyncPacket(id, entity.getPosition(),
                entity.getVelocity().div(20), entity.getPosition().yaw(),
                entity.getPosition().pitch(), onGround);
        byte[] positionBytes = NativePackets.encode(position);
        if (!Arrays.equals(entry.position, positionBytes)
                || tick - entry.positionTick >= Math.max(1, entity.getSynchronizationTicks())) {
            send.accept(position);
            entry.position = positionBytes;
            entry.positionTick = tick;
        }
        var head = new EntityHeadLookPacket(id, entity.getHeadRotation());
        byte[] headBytes = NativePackets.encode(head);
        if (!Arrays.equals(entry.head, headBytes)) {
            send.accept(head);
            entry.head = headBytes;
        }
        var metadata = new HashMap<Integer, Metadata.Entry<?>>();
        for (var value : entity.getMetadataPacket().entries().entrySet())
            if (changed(entry.metadata, value.getKey(),
                    new EntityMetaDataPacket(id, Map.of(value.getKey(), value.getValue()))))
                metadata.put(value.getKey(), value.getValue());
        if (!metadata.isEmpty()) send.accept(new EntityMetaDataPacket(id, metadata));
        if (entity instanceof LivingEntity living) {
            var attributes = new ArrayList<EntityAttributesPacket.Property>();
            for (var value : living.getAttributes()) {
                var property = new EntityAttributesPacket.Property(
                        value.getAttribute(), value.getBaseValue(), value.modifiers());
                if (changed(entry.attributes, value.getAttribute(),
                        new EntityAttributesPacket(id, List.of(property)))) attributes.add(property);
            }
            if (!attributes.isEmpty()) send.accept(new EntityAttributesPacket(id, attributes));
            var equipment = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
            for (var slot : EquipmentSlot.values()) {
                var item = living.getEquipment(slot);
                if (changed(entry.equipment, slot,
                        new EntityEquipmentPacket(id, Map.of(slot, item)))) equipment.put(slot, item);
            }
            if (!equipment.isEmpty()) send.accept(new EntityEquipmentPacket(id, equipment));
        }
        return appeared;
    }

    void retain(Set<Integer> desired, Consumer<ServerPacket> send) {
        var removed = new ArrayList<Integer>();
        entities.keySet().removeIf(id -> {
            if (desired.contains(id)) return false;
            removed.add(id);
            return true;
        });
        if (!removed.isEmpty()) send.accept(new DestroyEntitiesPacket(removed));
    }

    /** Account for state explicitly forwarded by an application before the next automatic scan. */
    void observe(ServerPacket packet, long tick) {
        switch (packet) {
            case SpawnEntityPacket p -> entities.put(p.entityId(), new Entry(p, tick));
            case DestroyEntitiesPacket p -> p.entityIds().forEach(entities::remove);
            case EntityPositionSyncPacket p -> {
                var entry = entities.get(p.entityId());
                if (entry != null) {
                    entry.position = NativePackets.encode(p);
                    entry.positionTick = tick;
                }
            }
            case EntityHeadLookPacket p -> {
                var entry = entities.get(p.entityId());
                if (entry != null) entry.head = NativePackets.encode(p);
            }
            case EntityMetaDataPacket p -> {
                var entry = entities.get(p.entityId());
                if (entry != null) p.entries().forEach((key, value) -> changed(entry.metadata, key,
                        new EntityMetaDataPacket(p.entityId(), Map.of(key, value))));
            }
            case EntityAttributesPacket p -> {
                var entry = entities.get(p.entityId());
                if (entry != null) p.properties().forEach(value -> changed(entry.attributes,
                        value.attribute(), new EntityAttributesPacket(p.entityId(), List.of(value))));
            }
            case EntityEquipmentPacket p -> {
                var entry = entities.get(p.entityId());
                if (entry != null) p.equipments().forEach((key, value) -> changed(entry.equipment,
                        key, new EntityEquipmentPacket(p.entityId(), Map.of(key, value))));
            }
            default -> {}
        }
    }

    private static <K> boolean changed(Map<K, byte[]> cache, K key, ServerPacket packet) {
        byte[] bytes = NativePackets.encode(packet);
        return !Arrays.equals(cache.put(key, bytes), bytes);
    }
}
