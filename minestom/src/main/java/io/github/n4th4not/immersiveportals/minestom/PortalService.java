package io.github.n4th4not.immersiveportals.minestom;

import io.github.n4th4not.immersiveportals.protocol.*;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.*;
import net.minestom.server.event.*;
import net.minestom.server.event.instance.*;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.*;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import net.minestom.server.network.packet.server.*;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.timer.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Minestom 26.2 portal registry, player negotiation and bounded remote-world streaming. Create one
 * service after MinecraftServer.init(), register worlds, and close it on shutdown.
 */
public final class PortalService implements AutoCloseable {
    private record World(Instance instance, int seaLevel) {}

    private final Map<String, World> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, PortalDefinition> portals = new ConcurrentHashMap<>();
    private final Map<Player, Session> sessions =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final java.util.concurrent.atomic.AtomicInteger queuedMessages =
            new java.util.concurrent.atomic.AtomicInteger();
    private final ConcurrentLinkedQueue<Runnable> actions = new ConcurrentLinkedQueue<>();
    private final EventNode<Event> events = EventNode.all("immersive-portals");
    private final Task task;
    private final PortalGravity gravity = new PortalGravity(this);
    private volatile boolean closed;
    private long tick;
    private Predicate<Player> authorization = p -> true;
    private BiPredicate<Player, PortalDefinition> visibility = (p, d) -> true;
    private Consumer<RemoteInteractionEvent> interaction = event -> {};

    private final class Session {
        final Player player;
        final UUID id = UUID.randomUUID();
        final Map<UUID, PortalDefinition> known = new HashMap<>();
        final Map<String, View> views = new HashMap<>();
        final Map<GravityKey, SentGravity> gravitySent = new HashMap<>();
        int features;
        boolean travelling;
        long lastTeleport = -100;
        long messageTick;
        int messages;

        Session(Player player) {
            this.player = player;
        }
    }

    private record GravityKey(String world, int id, UUID uuid) {}

    private record SentGravity(GravityState state, long tick) {}

    private static final class View {
        final UUID id = UUID.randomUUID();
        final String world;
        final Set<Long> chunks = new HashSet<>();
        final Set<Integer> entities = new HashSet<>();
        int sequence;

        View(String world) {
            this.world = world;
        }
    }

    public PortalService() {
        events.addListener(
                net.minestom.server.event.entity.EntityTickEvent.class,
                event -> gravity.tick(event.getEntity()));
        events.addListener(
                PlayerSpawnEvent.class,
                event ->
                        actions.add(
                                () -> {
                                    Session session = sessions.get(event.getPlayer());
                                    if (session != null) session.gravitySent.clear();
                                }));
        events.addListener(
                PlayerPluginMessageEvent.class,
                event -> {
                    if (!event.getIdentifier().equals(BridgeCodec.CHANNEL)) return;
                    byte[] data = event.getMessage().clone();
                    Player player = event.getPlayer();
                    if (data.length > BridgeCodec.MAX_PAYLOAD) return;
                    if (queuedMessages.incrementAndGet() > 256) {
                        queuedMessages.decrementAndGet();
                        return;
                    }
                    actions.add(
                            () -> {
                                queuedMessages.decrementAndGet();
                                receive(player, data);
                            });
                });
        events.addListener(
                PlayerSpawnEvent.class,
                event -> {
                    if (event.isFirstSpawn())
                        event.getPlayer()
                                .sendPacket(
                                        new PluginMessagePacket(
                                                BridgeCodec.CHANNEL,
                                                BridgeCodec.encode(
                                                        new BridgeMessage.Hello(
                                                                BridgeCodec.VERSION,
                                                                BridgeCodec.MINECRAFT_PROTOCOL))));
                });
        events.addListener(
                PlayerDisconnectEvent.class,
                event -> actions.add(() -> disconnect(event.getPlayer())));
        events.addListener(
                InstanceBlockUpdateEvent.class,
                event ->
                        actions.add(
                                () ->
                                        sendWorldPacket(
                                                event.getInstance(),
                                                new BlockChangePacket(
                                                        event.getBlockPosition(),
                                                        event.getBlock().stateId()))));
        events.addListener(
                InstanceSectionInvalidateEvent.class,
                event ->
                        actions.add(
                                () ->
                                        refreshChunk(
                                                event.getInstance(),
                                                event.sectionX(),
                                                event.sectionZ())));
        MinecraftServer.getGlobalEventHandler().addChild(events);
        task =
                MinecraftServer.getSchedulerManager()
                        .buildTask(this::tick)
                        .repeat(TaskSchedule.tick(1))
                        .schedule();
    }

    public void authorization(Predicate<Player> check) {
        authorization = Objects.requireNonNull(check);
    }

    public void visibility(BiPredicate<Player, PortalDefinition> check) {
        visibility = Objects.requireNonNull(check);
    }

    public void onRemoteInteraction(Consumer<RemoteInteractionEvent> listener) {
        interaction = Objects.requireNonNull(listener);
    }

    public void registerWorld(Instance instance) {
        registerWorld(instance, 63);
    }

    public void registerWorld(Instance instance, int seaLevel) {
        ensureOpen();
        var meta =
                new BridgeMessage.World(
                        instance.getDimensionName(),
                        instance.getDimensionType().key().asString(),
                        seaLevel);
        World old = worlds.putIfAbsent(meta.key(), new World(instance, seaLevel));
        if (old != null && old.instance() != instance)
            throw new IllegalArgumentException("World key already belongs to another instance");
        if (worlds.size() > 64) {
            worlds.remove(meta.key());
            throw new IllegalStateException("At most 64 worlds can be registered");
        }
        actions.add(() -> snapshotSessions().forEach(this::welcome));
    }

    public void unregisterWorld(Instance instance) {
        String key = instance.getDimensionName();
        worlds.computeIfPresent(key, (k, w) -> w.instance() == instance ? null : w);
        portals.values()
                .removeIf(p -> p.sourceWorld().equals(key) || p.destinationWorld().equals(key));
        actions.add(() -> snapshotSessions().forEach(this::welcome));
    }

    public PortalHandle create(PortalDefinition definition) {
        ensureOpen();
        validateWorlds(definition);
        if (portals.size() >= BridgeCodec.MAX_PORTALS)
            throw new IllegalStateException("Portal limit reached");
        if (portals.putIfAbsent(definition.id(), definition) != null)
            throw new IllegalArgumentException("Duplicate portal UUID");
        return new PortalHandle(this, definition.id());
    }

    public PortalHandle create(
            Instance source, Instance destination, Consumer<PortalDefinition.Builder> configure) {
        registerWorld(source);
        registerWorld(destination);
        var builder =
                PortalDefinition.builder(source.getDimensionName(), destination.getDimensionName());
        configure.accept(builder);
        return create(builder.build());
    }

    public Optional<PortalDefinition> find(UUID id) {
        return Optional.ofNullable(portals.get(id));
    }

    public Collection<PortalDefinition> portals() {
        return List.copyOf(portals.values());
    }

    void replace(UUID id, PortalDefinition definition) {
        ensureOpen();
        validateWorlds(definition);
        if (!id.equals(definition.id()) || portals.replace(id, definition) == null)
            throw new IllegalArgumentException("Portal no longer registered");
    }

    public boolean remove(UUID id) {
        return portals.remove(id) != null;
    }

    public boolean isConnected(Player player) {
        return sessions.containsKey(player);
    }

    public PortalGravity gravity() {
        return gravity;
    }

    public boolean supportsGravity(Player player) {
        Session session = sessions.get(player);
        return session != null && (session.features & BridgeCodec.FEATURE_GRAVITY) != 0;
    }

    public EventNode<Event> events() {
        return events;
    }

    CompletableFuture<Void> teleport(UUID id, Entity entity) {
        ensureOpen();
        PortalDefinition portal = find(id).orElseThrow(
            () -> new IllegalStateException("Portal is no longer registered"));
        World source = worlds.get(portal.sourceWorld()), target = worlds.get(portal.destinationWorld());
        if (source == null || target == null || entity.getInstance() != source.instance())
            throw new IllegalArgumentException(
                    "Entity must belong to the registered source instance");
        return PortalEntityTeleportation.teleport(
                entity, portal, target.instance(), gravity, gravity.eyePosition(entity));
    }

    private void validateWorlds(PortalDefinition d) {
        PortalOptions.of(d);
        if (!worlds.containsKey(d.sourceWorld()) || !worlds.containsKey(d.destinationWorld()))
            throw new IllegalArgumentException("Register both portal worlds first");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Portal service is closed");
    }

    private List<Session> snapshotSessions() {
        synchronized (sessions) {
            return List.copyOf(sessions.values());
        }
    }

    private void send(Player player, BridgeMessage message) {
        if (player.isOnline())
            player.sendPacket(
                    new PluginMessagePacket(BridgeCodec.CHANNEL, BridgeCodec.encode(message)));
    }

    private void welcome(Session s) {
        var list =
                worlds.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(
                                e ->
                                        new BridgeMessage.World(
                                                e.getKey(),
                                                e.getValue()
                                                        .instance()
                                                        .getDimensionType()
                                                        .key()
                                                        .asString(),
                                                e.getValue().seaLevel()))
                        .toList();
        send(s.player, new BridgeMessage.Welcome(s.id, list));
    }

    private void receive(Player player, byte[] data) {
        if (closed || !player.isOnline()) return;
        try {
            BridgeMessage message = BridgeCodec.decode(data);
            if (message instanceof BridgeMessage.Hello hello) {
                if (sessions.containsKey(player)) return;
                if (hello.protocol() != BridgeCodec.VERSION
                        || hello.minecraftProtocol() != BridgeCodec.MINECRAFT_PROTOCOL
                        || !authorization.test(player)) {
                    send(
                            player,
                            new BridgeMessage.Rejected(
                                    "Incompatible or unauthorized portal client"));
                    return;
                }
                Session s = new Session(player);
                s.features = hello.features();
                sessions.put(player, s);
                welcome(s);
                return;
            }
            Session s = sessions.get(player);
            if (s == null) return;
            if (s.messageTick != tick) {
                s.messageTick = tick;
                s.messages = 0;
            }
            if (++s.messages > 32) return;
            switch (message) {
                case BridgeMessage.Teleport request -> teleport(s, request);
                case BridgeMessage.Interaction request ->
                        RemoteInteractionEvent.validate(this, s.player, request)
                                .ifPresent(event -> {
                                    try { interaction.accept(event); }
                                    finally { acknowledgeInteraction(s, event); }
                                });
                default -> {}
            }
        } catch (RuntimeException ignored) {
            send(player, new BridgeMessage.Rejected("Invalid portal request"));
        }
    }

    private void acknowledgeInteraction(Session session, RemoteInteractionEvent event) {
        int sequence;
        if (event.packet() instanceof ClientPlayerActionPacket action) sequence = action.sequence();
        else if (event.packet() instanceof ClientPlayerBlockPlacementPacket placement) sequence = placement.sequence();
        else return;
        // Even a callback which does nothing must resolve client-side block prediction.
        // Re-send the authoritative block first, so a denied creative break/placement rolls back.
        var position = event.blockPosition().orElseThrow();
        ServerPacket update = new BlockChangePacket(position, event.instance().getBlock(position).stateId());
        ServerPacket ack = new AcknowledgeBlockChangePacket(sequence);
        if (event.instance() == session.player.getInstance()) session.player.sendPackets(update, ack);
        else {
            View view = session.views.get(event.instance().getDimensionName());
            if (view != null) {
                worldPacket(session, view, update);
                worldPacket(session, view, ack);
            }
        }
    }

    private synchronized void tick() {
        if (closed) return;
        tick++;
        gravity.cleanup();
        for (int n = 0; n < 4096; n++) {
            Runnable action = actions.poll();
            if (action == null) break;
            action.run();
        }
        for (Session s : snapshotSessions()) {
            if (!s.player.isOnline()) {
                disconnect(s.player);
                continue;
            }
            if (!authorization.test(s.player)) {
                disconnect(s.player);
                continue;
            }
            var allowed = new HashMap<UUID, PortalDefinition>();
            for (var p : portals.values()) if (visibility.test(s.player, p)) allowed.put(p.id(), p);
            for (var id : List.copyOf(s.known.keySet()))
                if (!allowed.containsKey(id)) {
                    send(s.player, new BridgeMessage.Remove(id));
                    s.known.remove(id);
                }
            for (var p : allowed.values())
                if (s.known.get(p.id()) != p) {
                    send(s.player, new BridgeMessage.Upsert(p));
                    s.known.put(p.id(), p);
                }
            if (tick % 2 == 0) {
                refreshViews(s);
                if (s.player.getInstance() != null) {
                    for (Entity entity : s.player.getInstance().getEntities())
                        if (entity == s.player || entity.getViewers().contains(s.player))
                            syncGravity(s, entity, false);
                }
                s.gravitySent.entrySet().removeIf(e -> tick - e.getValue().tick() > 200);
            }
        }
    }

    private void disconnect(Player player) {
        Session s = sessions.remove(player);
        if (s != null) gravity.reset(player);
        if (s != null && player.isOnline()) {
            send(player, new BridgeMessage.Clear());
            for (View view : s.views.values()) send(player, new BridgeMessage.CloseView(view.id));
        }
    }

    static double eyeHeight(Player player) {
        return player.getEyeHeight() * player.getAttributeValue(Attribute.SCALE);
    }

    private static Vec3 vec(net.minestom.server.coordinate.Point p) {
        return new Vec3(p.x(), p.y(), p.z());
    }

    private void teleport(Session s, BridgeMessage.Teleport request) {
        if (!tryTeleport(s, request)) rejectTeleport(s);
    }

    private void rejectTeleport(Session s) {
        Player p = s.player;
        if (p.getInstance() == null) return;
        double factor =
                p.getAttribute(Attribute.SCALE).modifiers().stream()
                        .filter(m -> m.id().equals(Key.key("iportal:scaling")))
                        .mapToDouble(m -> m.amount() + 1)
                        .findFirst()
                        .orElse(1);
        send(
                p,
                new BridgeMessage.Correction(
                        p.getInstance().getDimensionName(),
                        vec(p.getPosition()),
                        vec(p.getVelocity()),
                        factor,
                        p.getPosition().yaw(),
                        p.getPosition().pitch(),
                        gravity.get(p)));
    }

    private boolean tryTeleport(Session s, BridgeMessage.Teleport request) {
        PortalDefinition p = s.known.get(request.portal());
        Player player = s.player;
        if (p == null
                || p.kind() != PortalDefinition.Kind.PORTAL
                || s.travelling
                || tick - s.lastTeleport < 2
                || player.getInstance() == null
                || !player.getInstance().getDimensionName().equals(p.sourceWorld())) return false;
        Vec3 eye = gravity.eyePosition(player);
        if (request.fromEye().subtract(eye).lengthSquared() > 16
                || request.toEye().subtract(request.fromEye()).lengthSquared() > 64) return false;
        double t = PortalOptions.of(p).intersection(p, request.fromEye(), request.toEye());
        if (!Double.isFinite(t) || !PortalOptions.of(p).teleportable()) return false;
        // The earliest crossed visible portal is authoritative.
        for (var other : s.known.values())
            if (other != p && other.sourceWorld().equals(p.sourceWorld())) {
                if (other.kind() != PortalDefinition.Kind.PORTAL
                        || !PortalOptions.of(other).teleportable()) continue;
                double ot =
                        PortalOptions.of(other)
                                .intersection(other, request.fromEye(), request.toEye());
                if (Double.isFinite(ot) && ot < t) return false;
            }
        if (!RemoteInteractionEvent.clearSegment(
                player.getInstance(),
                request.fromEye(),
                request.fromEye().add(request.toEye().subtract(request.fromEye()).multiply(t))))
            return false;
        World target = worlds.get(p.destinationWorld());
        if (target == null) return false;
        GravityState nextGravity =
                PortalOptions.of(p).changesGravity()
                        ? gravity.get(player).rotated(p.rotation())
                        : gravity.get(player);
        if (!supportsGravity(player) && !nextGravity.equals(GravityState.DEFAULT)) return false;
        s.travelling = true;
        s.lastTeleport = tick;
        CompletableFuture<Void> future;
        try {
            future =
                    PortalEntityTeleportation.teleport(
                            player, p, target.instance(), gravity, request.toEye());
        } catch (RuntimeException error) {
            s.travelling = false;
            return false;
        }
        future.whenComplete(
                (ignored, error) ->
                        actions.add(
                                () -> {
                                    s.travelling = false;
                                    if (error != null) {
                                        rejectTeleport(s);
                                        return;
                                    }
                                }));
        return true;
    }

    private void refreshViews(Session s) {
        var centers = new LinkedHashMap<String, List<Vec3>>();
        String root =
                s.player.getInstance() == null ? "" : s.player.getInstance().getDimensionName();
        var origins = new ArrayList<Map.Entry<String, Vec3>>();
        origins.add(Map.entry(root, vec(s.player.getPosition())));
        for (int depth = 0; depth < 3; depth++) {
            var next = new ArrayList<Map.Entry<String, Vec3>>();
            for (var origin : origins)
                for (var p : s.known.values())
                    if (p.sourceWorld().equals(origin.getKey())
                            && p.origin().subtract(origin.getValue()).lengthSquared() < 128 * 128) {
                        Vec3 center = p.transformPoint(origin.getValue());
                        if (!p.destinationWorld().equals(root) && centers.size() < 8)
                            centers.computeIfAbsent(p.destinationWorld(), k -> new ArrayList<>())
                                    .add(center);
                        next.add(Map.entry(p.destinationWorld(), center));
                        if (next.size() >= 32) break;
                    }
            origins = next;
        }
        for (String key : List.copyOf(s.views.keySet()))
            if (!centers.containsKey(key)) {
                View v = s.views.remove(key);
                send(s.player, new BridgeMessage.CloseView(v.id));
            }
        for (var entry : centers.entrySet()) {
            World world = worlds.get(entry.getKey());
            if (world == null) continue;
            View view =
                    s.views.computeIfAbsent(
                            entry.getKey(),
                            k -> {
                                View v = new View(k);
                                send(
                                        s.player,
                                        new BridgeMessage.OpenView(
                                                v.id, k, entry.getValue().getFirst(), 3));
                                return v;
                            });
            var desired = new LinkedHashSet<Long>();
            for (Vec3 center : entry.getValue()) {
                int cx = Math.floorDiv((int) Math.floor(center.x()), 16),
                        cz = Math.floorDiv((int) Math.floor(center.z()), 16);
                for (int radius = 0; radius <= 3; radius++)
                    for (int x = cx - radius; x <= cx + radius; x++)
                        for (int z = cz - radius; z <= cz + radius; z++)
                            if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) == radius
                                    && desired.size() < 49) desired.add(pack(x, z));
            }
            for (long pos : List.copyOf(view.chunks))
                if (!desired.contains(pos)) {
                    worldPacket(s, view, new UnloadChunkPacket((int) pos, (int) (pos >> 32)));
                    view.chunks.remove(pos);
                }
            for (long pos : desired)
                if (!view.chunks.contains(pos)) {
                    Chunk chunk = world.instance().getChunk((int) pos, (int) (pos >> 32));
                    if (chunk == null) {
                        world.instance().loadChunk((int) pos, (int) (pos >> 32));
                        continue;
                    }
                    ServerPacket packet =
                            SendablePacket.extractServerPacket(
                                    ConnectionState.PLAY, chunk.getFullDataPacket());
                    if (packet != null) {
                        worldPacket(s, view, packet);
                        view.chunks.add(pos);
                    }
                }
            streamEntities(s, view, world.instance());
        }
    }

    private static long pack(int x, int z) {
        return (x & 0xffffffffL) | ((long) z << 32);
    }

    private void streamEntities(Session s, View view, Instance instance) {
        var desired = new HashSet<Integer>();
        for (Entity entity : instance.getEntities()) {
            if (entity == s.player
                    || desired.size() >= 256
                    || !view.chunks.contains(
                            pack(entity.getPosition().chunkX(), entity.getPosition().chunkZ())))
                continue;
            desired.add(entity.getEntityId());
            if (!view.entities.contains(entity.getEntityId())) {
                int data =
                        entity.getEntityMeta()
                                        instanceof
                                        net.minestom.server.entity.metadata.ObjectDataProvider
                                                provider
                                ? provider.getObjectData()
                                : 0;
                worldPacket(
                        s,
                        view,
                        new SpawnEntityPacket(
                                entity.getEntityId(),
                                entity.getUuid(),
                                entity.getEntityType(),
                                entity.getPosition(),
                                entity.getPosition().yaw(),
                                data,
                                entity.getVelocity().div(20)));
            }
            worldPacket(
                    s,
                    view,
                    new EntityPositionSyncPacket(
                            entity.getEntityId(),
                            entity.getPosition(),
                            entity.getVelocity().div(20),
                            entity.getPosition().yaw(),
                            entity.getPosition().pitch(),
                            gravity.get(entity).direction() == GravityDirection.DOWN ? entity.isOnGround() : gravity.isOnGround(entity)));
            worldPacket(
                    s,
                    view,
                    new EntityHeadLookPacket(entity.getEntityId(), entity.getHeadRotation()));
            worldPacket(s, view, entity.getMetadataPacket());
            if (entity instanceof LivingEntity living) {
                worldPacket(
                        s,
                        view,
                        new EntityAttributesPacket(
                                entity.getEntityId(),
                                living.getAttributes().stream()
                                        .map(
                                                a ->
                                                        new EntityAttributesPacket.Property(
                                                                a.getAttribute(),
                                                                a.getBaseValue(),
                                                                a.modifiers()))
                                        .toList()));
                var equipment =
                        new EnumMap<EquipmentSlot, net.minestom.server.item.ItemStack>(
                                EquipmentSlot.class);
                for (var slot : EquipmentSlot.values())
                    equipment.put(slot, living.getEquipment(slot));
                worldPacket(s, view, new EntityEquipmentPacket(entity.getEntityId(), equipment));
                syncGravity(s, living, !view.entities.contains(entity.getEntityId()));
            }
        }
        for (int id : view.entities)
            if (!desired.contains(id)) worldPacket(s, view, new DestroyEntitiesPacket(List.of(id)));
        view.entities.clear();
        view.entities.addAll(desired);
    }

    private void syncGravity(Session session, Entity entity, boolean force) {
        if (!(entity instanceof LivingEntity)
                || !supportsGravity(session.player)
                || entity.getInstance() == null) return;
        String world = entity.getInstance().getDimensionName();
        var key = new GravityKey(world, entity.getEntityId(), entity.getUuid());
        var state = gravity.get(entity);
        var old = session.gravitySent.get(key);
        if (force || old == null || !old.state().equals(state) || tick - old.tick() >= 100) {
            if (session.gravitySent.size() >= 4096 && !session.gravitySent.containsKey(key)) return;
            send(
                    session.player,
                    new BridgeMessage.Gravity(
                            world, entity.getEntityId(), entity.getUuid(), state));
            session.gravitySent.put(key, new SentGravity(state, tick));
        }
    }

    private void worldPacket(Session s, View view, ServerPacket packet) {
        byte[] bytes = NativePackets.encode(packet);
        if (bytes.length > 2_097_152)
            throw new IllegalArgumentException("World packet exceeds 2 MiB");
        int count = (bytes.length + 29_999) / 30_000;
        int sequence = view.sequence++;
        for (int i = 0; i < count; i++)
            send(
                    s.player,
                    new BridgeMessage.WorldFragment(
                            view.id,
                            sequence,
                            i,
                            count,
                            bytes.length,
                            Arrays.copyOfRange(
                                    bytes, i * 30_000, Math.min(bytes.length, (i + 1) * 30_000))));
    }

    /**
     * Forward a world-local packet, such as sound, particles, block entities or custom entity
     * updates.
     */
    public void sendWorldPacket(Instance instance, ServerPacket packet) {
        ensureOpen();
        actions.add(() -> forwardWorldPacket(instance, packet));
    }

    private void forwardWorldPacket(Instance instance, ServerPacket packet) {
        for (Session s : snapshotSessions()) {
            View view = s.views.get(instance.getDimensionName());
            if (view != null) worldPacket(s, view, packet);
        }
    }

    private void refreshChunk(Instance instance, int x, int z) {
        for (Session s : snapshotSessions()) {
            View view = s.views.get(instance.getDimensionName());
            if (view != null) view.chunks.remove(pack(x, z));
        }
    }

    Instance instance(String key) {
        World w = worlds.get(key);
        return w == null ? null : w.instance();
    }

    boolean visible(Player player, PortalDefinition p) {
        return visibility.test(player, p);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        task.cancel();
        gravity.close();
        MinecraftServer.getGlobalEventHandler().removeChild(events);
        for (Session s : snapshotSessions()) disconnect(s.player);
        portals.clear();
        worlds.clear();
        actions.clear();
    }
}
