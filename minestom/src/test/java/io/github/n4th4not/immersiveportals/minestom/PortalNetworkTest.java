package io.github.n4th4not.immersiveportals.minestom;

import static org.junit.jupiter.api.Assertions.*;

import io.github.n4th4not.immersiveportals.protocol.*;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.*;
import net.minestom.server.network.*;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.server.*;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.network.player.*;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

class PortalNetworkTest {
    @BeforeAll static void initialize() { MinecraftServer.init(); }
    @AfterAll static void shutdown() { MinecraftServer.stopCleanly(); }

    private static final class Connection extends PlayerConnection {
        final List<BridgeMessage> messages = new ArrayList<>();
        java.util.function.Consumer<BridgeMessage> onMessage = ignored -> {};
        public SocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 0); }
        public void sendPacket(SendablePacket packet) {
            var nativePacket = SendablePacket.extractServerPacket(ConnectionState.PLAY, packet);
            if (nativePacket instanceof PluginMessagePacket p && p.channel().equals(BridgeCodec.CHANNEL)) {
                var message = BridgeCodec.decode(p.data());
                messages.add(message);
                onMessage.accept(message);
            }
        }
        List<ServerPacket> worldPackets() {
            var packets = new ArrayList<ServerPacket>();
            var reassembler = new PacketReassembler();
            for (var message : messages)
                if (message instanceof BridgeMessage.WorldFragment fragment)
                    reassembler.accept(fragment, 0).ifPresent(bytes -> {
                        var input = NetworkBuffer.wrap(bytes, 0, bytes.length, MinecraftServer.getRegistries());
                        packets.add(PacketVanilla.SERVER_PACKET_PARSER.play()
                                .create(input.read(NetworkBuffer.VAR_INT), input));
                    });
            return packets;
        }
    }

    private static Player player(Connection connection, Instance rootWorld) {
        var player = new Player(connection, new GameProfile(UUID.randomUUID(), "PacketAudit")) {
            @Override public Instance getInstance() { return rootWorld; }
        };
        connection.setPlayer(player);
        return player;
    }

    private static InstanceContainer world() {
        var world = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD,
                Key.key("test:w_" + UUID.randomUUID().toString().replace("-", "")));
        MinecraftServer.getInstanceManager().registerInstance(world);
        world.loadChunk(0, 0).join();
        return world;
    }

    private static void call(PortalService service, String method, Class<?>[] types, Object... args) {
        try {
            var target = PortalService.class.getDeclaredMethod(method, types);
            target.setAccessible(true);
            target.invoke(service, args);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void tick(PortalService service) { call(service, "tick", new Class<?>[0]); }
    private static void receive(PortalService service, Player player, BridgeMessage message) {
        call(service, "receive", new Class<?>[] {Player.class, byte[].class},
                player, BridgeCodec.encode(message));
    }
    private static void connect(PortalService service, Player player) {
        receive(service, player, new BridgeMessage.Hello(BridgeCodec.VERSION,
                BridgeCodec.MINECRAFT_PROTOCOL, BridgeCodec.FEATURE_GRAVITY));
        assertTrue(service.isConnected(player));
    }

    @Test
    void worldRegistrationAndEquivalentPortalUpdatesDoNotRepublishState() {
        var source = world(); var target = world(); var connection = new Connection();
        var player = player(connection, source);
        try (var service = new PortalService()) {
            var handle = service.create(source, target, p -> {});
            connect(service, player);
            tick(service); tick(service);
            assertEquals(1, connection.messages.stream().filter(BridgeMessage.Welcome.class::isInstance).count());
            connection.messages.clear();
            service.registerWorld(source); service.registerWorld(target);
            handle.update(p -> p);
            tick(service); tick(service);
            assertFalse(connection.messages.stream().anyMatch(p ->
                    p instanceof BridgeMessage.Welcome || p instanceof BridgeMessage.Upsert));
            handle.update(p -> p.size(4, 5));
            tick(service);
            assertEquals(1, connection.messages.stream().filter(BridgeMessage.Upsert.class::isInstance).count());
            connection.messages.clear();
            var third = world();
            service.registerWorld(third); service.registerWorld(third);
            tick(service);
            assertEquals(1, connection.messages.stream().filter(BridgeMessage.Welcome.class::isInstance).count());
        }
    }

    @Test
    void onlyLoadedChunksReceiveBlockDeltasAndRepeatedStateIsCoalescedWithinTheTick() {
        var source = world(); var target = world(); var connection = new Connection();
        try (var service = new PortalService()) {
            service.create(source, target, p -> {});
            connect(service, player(connection, source));
            tick(service); tick(service);
            connection.messages.clear();
            var update = new BlockChangePacket(new Vec(1, 5, 1), Block.STONE.stateId());
            service.sendWorldPacket(target, update); service.sendWorldPacket(target, update);
            service.sendWorldPacket(target, new BlockChangePacket(new Vec(10000, 5, 10000), Block.STONE.stateId()));
            tick(service);
            assertEquals(1, connection.worldPackets().stream().filter(BlockChangePacket.class::isInstance).count());
            connection.messages.clear();
            service.sendWorldPacket(target, update);
            service.sendWorldPacket(target, new BlockChangePacket(new Vec(1, 5, 1), Block.AIR.stateId()));
            service.sendWorldPacket(target, update);
            tick(service);
            assertEquals(3, connection.worldPackets().stream().filter(BlockChangePacket.class::isInstance).count());
            connection.messages.clear();
            var event = new EntityStatusPacket(42, (byte) 2);
            service.sendWorldPacket(target, event); service.sendWorldPacket(target, event);
            tick(service);
            assertEquals(2, connection.worldPackets().stream().filter(EntityStatusPacket.class::isInstance).count());
        }
    }

    @Test
    void aFullChunkRefreshSupersedesItsIncrementalBlockUpdates() {
        var source = world(); var target = world(); var connection = new Connection();
        try (var service = new PortalService()) {
            service.create(source, target, p -> {});
            connect(service, player(connection, source));
            tick(service); tick(service);
            connection.messages.clear();
            target.invalidateSection(0, 0, 0);
            target.setBlock(1, 5, 1, Block.STONE);
            tick(service); tick(service);
            var packets = connection.worldPackets();
            assertFalse(packets.stream().anyMatch(BlockChangePacket.class::isInstance));
            assertTrue(packets.stream().anyMatch(ChunkDataPacket.class::isInstance));
        }
    }

    @Test
    void aRemoteBlockEditAcknowledgesPredictionWithoutRepeatingTheAutomaticBlockUpdate() {
        var source = world(); var target = world(); var connection = new Connection();
        target.setBlock(0, 1, 1, Block.STONE);
        var player = player(connection, source);
        try (var service = new PortalService()) {
            var portal = service.create(source, target, p -> p
                    .origin(new Vec3(0, 1, 1)).destination(new Vec3(0, 1, 0))
                    .axes(new Vec3(-1, 0, 0), new Vec3(0, 1, 0)).size(4, 4));
            connect(service, player);
            tick(service); tick(service);
            var action = new ClientPlayerActionPacket(ClientPlayerActionPacket.Status.STARTED_DIGGING,
                    new Vec(0, 1, 1), BlockFace.NORTH, 23);
            var info = PacketVanilla.CLIENT_PACKET_PARSER.play().packetInfo(ClientPlayerActionPacket.class);
            byte[] bytes = NetworkBuffer.makeArray(buffer -> {
                buffer.write(NetworkBuffer.VAR_INT, info.id());
                buffer.write(ClientPlayerActionPacket.SERIALIZER, action);
            }, MinecraftServer.getRegistries());
            var request = new BridgeMessage.Interaction(List.of(portal.id()), bytes);
            assertTrue(RemoteInteractionEvent.validate(service, player, request).isPresent());
            service.onRemoteInteraction(event -> event.instance().setBlock(event.blockPosition().orElseThrow(), Block.AIR));
            connection.messages.clear();
            service.events().call(new PlayerPluginMessageEvent(player, BridgeCodec.CHANNEL,
                    BridgeCodec.encode(request)));
            tick(service);
            var packets = connection.worldPackets();
            assertEquals(1, packets.stream().filter(BlockChangePacket.class::isInstance).count());
            assertEquals(1, packets.stream().filter(AcknowledgeBlockChangePacket.class::isInstance).count());
            assertTrue(packets.indexOf(packets.stream().filter(BlockChangePacket.class::isInstance).findFirst().orElseThrow())
                    < packets.indexOf(packets.stream().filter(AcknowledgeBlockChangePacket.class::isInstance).findFirst().orElseThrow()));

            // Two denied predictions still each need an authoritative block before their ack.
            service.onRemoteInteraction(event -> {});
            connection.messages.clear();
            service.events().call(new PlayerPluginMessageEvent(player, BridgeCodec.CHANNEL,
                    BridgeCodec.encode(request)));
            service.events().call(new PlayerPluginMessageEvent(player, BridgeCodec.CHANNEL,
                    BridgeCodec.encode(request)));
            tick(service);
            packets = connection.worldPackets().stream().filter(p ->
                    p instanceof BlockChangePacket || p instanceof AcknowledgeBlockChangePacket).toList();
            assertEquals(4, packets.size());
            assertInstanceOf(BlockChangePacket.class, packets.get(0));
            assertInstanceOf(AcknowledgeBlockChangePacket.class, packets.get(1));
            assertInstanceOf(BlockChangePacket.class, packets.get(2));
            assertInstanceOf(AcknowledgeBlockChangePacket.class, packets.get(3));
        }
    }

    @Test
    void correctionsCarryGravityAndClearAloneClosesTheSession() {
        var source = world(); var connection = new Connection(); var player = player(connection, source);
        try (var service = new PortalService()) {
            service.registerWorld(source);
            connect(service, player);
            service.gravity().set(player, new GravityState(GravityDirection.NORTH, 12));
            connection.messages.clear();
            receive(service, player, new BridgeMessage.Teleport(UUID.randomUUID(), Vec3.ZERO, Vec3.ZERO));
            assertEquals(1, connection.messages.stream().filter(BridgeMessage.Correction.class::isInstance).count());
            receive(service, player, new BridgeMessage.Teleport(UUID.randomUUID(), Vec3.ZERO, Vec3.ZERO));
            assertEquals(2, connection.messages.stream().filter(BridgeMessage.Correction.class::isInstance).count());
            call(service, "syncGravity", new Class<?>[] {
                    ClassFor.session(), net.minestom.server.entity.Entity.class, boolean.class},
                    session(service, player), player, false);
            assertFalse(connection.messages.stream().anyMatch(BridgeMessage.Gravity.class::isInstance));
            // A change during publication must not be mistaken for the state carried by Correction.
            var changed = new GravityState(GravityDirection.WEST, 12);
            connection.onMessage = message -> {
                if (message instanceof BridgeMessage.Correction) service.gravity().set(player, changed);
            };
            receive(service, player, new BridgeMessage.Teleport(UUID.randomUUID(), Vec3.ZERO, Vec3.ZERO));
            connection.onMessage = ignored -> {};
            call(service, "syncGravity", new Class<?>[] {
                    ClassFor.session(), net.minestom.server.entity.Entity.class, boolean.class},
                    session(service, player), player, false);
            assertEquals(changed, assertInstanceOf(BridgeMessage.Gravity.class,
                    connection.messages.getLast()).state());
            service.authorization(p -> false);
            tick(service);
            assertEquals(1, connection.messages.stream().filter(BridgeMessage.Clear.class::isInstance).count());
            assertFalse(connection.messages.stream().anyMatch(BridgeMessage.CloseView.class::isInstance));
        }
    }

    private static Object session(PortalService service, Player player) {
        try {
            var field = PortalService.class.getDeclaredField("sessions");
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(service)).get(player);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static class ClassFor {
        static Class<?> session() {
            try { return Class.forName(PortalService.class.getName() + "$Session"); }
            catch (ClassNotFoundException e) { throw new AssertionError(e); }
        }
    }
}
