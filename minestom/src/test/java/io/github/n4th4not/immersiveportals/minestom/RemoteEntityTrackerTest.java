package io.github.n4th4not.immersiveportals.minestom;

import static org.junit.jupiter.api.Assertions.*;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.item.*;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.*;
import org.junit.jupiter.api.*;

import java.util.*;

class RemoteEntityTrackerTest {
    @BeforeAll static void initialize() { MinecraftServer.init(); }
    @AfterAll static void shutdown() { MinecraftServer.stopCleanly(); }

    @Test
    void spawnAlreadyCarriesMotionAndHeadButPeriodicAnchorsRemain() {
        var tracker = new RemoteEntityTracker();
        var entity = new LivingEntity(EntityType.ARMOR_STAND);
        var sent = new ArrayList<ServerPacket>();
        assertTrue(tracker.update(entity, false, 0, sent::add));
        assertEquals(1, packets(sent, SpawnEntityPacket.class).size());
        assertTrue(packets(sent, EntityPositionSyncPacket.class).isEmpty());
        assertTrue(packets(sent, EntityHeadLookPacket.class).isEmpty());
        assertTrue(packets(sent, EntityEquipmentPacket.class).isEmpty());
        sent.clear();
        for (long tick = 2; tick < 20; tick += 2)
            assertFalse(tracker.update(entity, false, tick, sent::add));
        assertTrue(sent.isEmpty());
        tracker.update(entity, false, 20, sent::add);
        assertEquals(1, sent.size());
        assertInstanceOf(EntityPositionSyncPacket.class, sent.getFirst());
    }

    @Test
    void onlyChangedMetadataAttributesAndEquipmentSlotsAreSentAndReset() {
        var tracker = new RemoteEntityTracker();
        var entity = new LivingEntity(EntityType.ARMOR_STAND);
        tracker.update(entity, false, 0, ignored -> {});
        entity.setGlowing(true);
        entity.getAttribute(Attribute.SCALE).setBaseValue(.5);
        entity.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.DIAMOND_HELMET));
        var sent = new ArrayList<ServerPacket>();
        tracker.update(entity, false, 2, sent::add);
        assertEquals(3, sent.size());
        assertEquals(1, packets(sent, EntityMetaDataPacket.class).getFirst().entries().size());
        assertEquals(Set.of(Attribute.SCALE, Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS),
                packets(sent, EntityAttributesPacket.class).getFirst().properties().stream()
                        .map(EntityAttributesPacket.Property::attribute).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(EquipmentSlot.HELMET),
                packets(sent, EntityEquipmentPacket.class).getFirst().equipments().keySet());
        sent.clear();
        tracker.update(entity, false, 4, sent::add);
        assertTrue(sent.isEmpty());
        entity.setGlowing(false);
        entity.getAttribute(Attribute.SCALE).setBaseValue(1);
        entity.setEquipment(EquipmentSlot.HELMET, ItemStack.AIR);
        tracker.update(entity, false, 6, sent::add);
        assertEquals(3, sent.size());
        assertEquals(ItemStack.AIR, packets(sent, EntityEquipmentPacket.class).getFirst()
                .equipments().get(EquipmentSlot.HELMET));
    }

    @Test
    void forwardedPartialStateIsNotSentAgainByTheAutomaticScan() {
        var tracker = new RemoteEntityTracker();
        var entity = new LivingEntity(EntityType.ARMOR_STAND);
        tracker.update(entity, false, 0, ignored -> {});
        var helmet = ItemStack.of(Material.DIAMOND_HELMET);
        entity.setEquipment(EquipmentSlot.HELMET, helmet);
        entity.setGlowing(true);
        entity.getAttribute(Attribute.SCALE).setBaseValue(.5);
        tracker.observe(new EntityEquipmentPacket(entity.getEntityId(),
                Map.of(EquipmentSlot.HELMET, helmet)), 1);
        tracker.observe(entity.getMetadataPacket(), 1);
        var scale = entity.getAttribute(Attribute.SCALE);
        tracker.observe(new EntityAttributesPacket(entity.getEntityId(), List.of(
                new EntityAttributesPacket.Property(Attribute.SCALE, scale.getBaseValue(),
                        scale.modifiers()))), 1);
        var sent = new ArrayList<ServerPacket>();
        tracker.update(entity, false, 2, sent::add);
        // Equipping the helmet also changes armor/toughness, which were not in the forwarded delta.
        assertEquals(1, sent.size());
        assertEquals(Set.of(Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS),
                packets(sent, EntityAttributesPacket.class).getFirst().properties().stream()
                        .map(EntityAttributesPacket.Property::attribute).collect(java.util.stream.Collectors.toSet()));
        sent.clear();
        tracker.update(entity, false, 4, sent::add);
        assertTrue(sent.isEmpty());
    }

    @Test
    void groundPrecisionAndVelocityChangesStillHaveTheirOwnInformation() {
        var tracker = new RemoteEntityTracker();
        var entity = new LivingEntity(EntityType.ARMOR_STAND);
        entity.refreshPosition(new Pos(0, 0, 0, 35, -20), false, false);
        var sent = new ArrayList<ServerPacket>();
        tracker.update(entity, true, 0, sent::add);
        var spawn = packets(sent, SpawnEntityPacket.class).getFirst();
        assertEquals(entity.getHeadRotation(), spawn.headRot());
        assertTrue(packets(sent, EntityHeadLookPacket.class).isEmpty());
        var initial = packets(sent, EntityPositionSyncPacket.class).getFirst();
        assertTrue(initial.onGround());
        assertEquals(35, initial.yaw());
        sent.clear();
        entity.setVelocity(new Vec(1, 2, 3));
        tracker.update(entity, true, 2, sent::add);
        assertEquals(1, sent.size());
        assertEquals(new Vec(.05, .1, .15),
                packets(sent, EntityPositionSyncPacket.class).getFirst().delta());
    }

    @Test
    void leavingAndReenteringAViewRequiresACompleteNewAppearance() {
        var tracker = new RemoteEntityTracker();
        var entity = new LivingEntity(EntityType.ARMOR_STAND);
        entity.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.DIAMOND_HELMET));
        tracker.update(entity, false, 0, ignored -> {});
        var sent = new ArrayList<ServerPacket>();
        tracker.retain(Set.of(), sent::add);
        assertInstanceOf(DestroyEntitiesPacket.class, sent.getFirst());
        sent.clear();
        assertTrue(tracker.update(entity, false, 2, sent::add));
        assertInstanceOf(SpawnEntityPacket.class, sent.getFirst());
        assertEquals(1, packets(sent, EntityEquipmentPacket.class).size());
    }

    @Test
    void recycledNumericIdsAreDestroyedBeforeTheirNewUuidIsSpawned() {
        var tracker = new RemoteEntityTracker();
        var entity = new LivingEntity(EntityType.ARMOR_STAND);
        tracker.observe(new SpawnEntityPacket(entity.getEntityId(), UUID.randomUUID(),
                entity.getEntityType(), Pos.ZERO, 0, 0, Vec.ZERO), 0);
        var sent = new ArrayList<ServerPacket>();
        assertTrue(tracker.update(entity, false, 2, sent::add));
        assertInstanceOf(DestroyEntitiesPacket.class, sent.get(0));
        assertEquals(entity.getUuid(), assertInstanceOf(SpawnEntityPacket.class, sent.get(1)).uuid());
    }

    private static <T> List<T> packets(List<ServerPacket> packets, Class<T> type) {
        return packets.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void anIdleEntitySendsOnlyItsConfiguredAbsolutePositionRefreshes() {
        var tracker = new RemoteEntityTracker();
        var entity = new LivingEntity(EntityType.ARMOR_STAND);
        entity.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.DIAMOND_HELMET));
        tracker.update(entity, false, 0, ignored -> {});
        var sent = new ArrayList<ServerPacket>();
        for (long tick = 2; tick < 200; tick += 2) tracker.update(entity, false, tick, sent::add);
        assertEquals(9, sent.size());
        assertTrue(sent.stream().allMatch(EntityPositionSyncPacket.class::isInstance));
        // Previous scan: position, head, metadata, attributes and equipment (five native packets).
        System.out.println("NETWORK_IDLE_ENTITY scans=99 oldPackets=495 newPackets=" + sent.size()
                + " newBytes=" + sent.stream().mapToInt(p -> NativePackets.encode(p).length).sum());
    }
}
