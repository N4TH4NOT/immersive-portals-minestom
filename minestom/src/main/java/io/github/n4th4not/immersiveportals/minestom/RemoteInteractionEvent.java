package io.github.n4th4not.immersiveportals.minestom;

import io.github.n4th4not.immersiveportals.protocol.*;

import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.*;
import net.minestom.server.utils.block.BlockIterator;

import java.util.*;

/** A server-validated interaction with the destination world and the original native packet. */
public record RemoteInteractionEvent(
        Player player,
        Instance instance,
        List<UUID> portalPath,
        ClientPacket.Play packet,
        Optional<Point> blockPosition,
        Optional<Entity> entity) {
    public RemoteInteractionEvent {
        portalPath = List.copyOf(portalPath);
    }

    private static Vec3 vector(Point p) {
        return new Vec3(p.x(), p.y(), p.z());
    }

    static Optional<RemoteInteractionEvent> validate(
            PortalService service, Player player, BridgeMessage.Interaction request) {
        if (player.getInstance() == null) return Optional.empty();
        NetworkBuffer input =
                NetworkBuffer.wrap(
                        request.packet(),
                        0,
                        request.packet().length,
                        MinecraftServer.getRegistries());
        int packetId = input.read(NetworkBuffer.VAR_INT);
        var registry = PacketVanilla.CLIENT_PACKET_PARSER.play();
        var info = registry.packetInfo(packetId);
        if (info == null
                || !(info.packetClass() == ClientPlayerActionPacket.class
                        || info.packetClass() == ClientPlayerBlockPlacementPacket.class
                        || info.packetClass() == ClientAttackPacket.class
                        || info.packetClass() == ClientInteractEntityPacket.class))
            return Optional.empty();
        ClientPacket.Play packet = registry.create(packetId, input);
        if (input.readableBytes() != 0) return Optional.empty();
        boolean isEntity =
                packet instanceof ClientAttackPacket
                        || packet instanceof ClientInteractEntityPacket;
        if (!(isEntity
                || packet instanceof ClientPlayerActionPacket
                || packet instanceof ClientPlayerBlockPlacementPacket)) return Optional.empty();
        if (packet instanceof ClientPlayerActionPacket action
                && action.status() != ClientPlayerActionPacket.Status.STARTED_DIGGING
                && action.status() != ClientPlayerActionPacket.Status.CANCELLED_DIGGING
                && action.status() != ClientPlayerActionPacket.Status.FINISHED_DIGGING)
            return Optional.empty();
        double remaining =
                Math.min(
                        6,
                        player.getAttributeValue(
                                isEntity
                                        ? Attribute.ENTITY_INTERACTION_RANGE
                                        : Attribute.BLOCK_INTERACTION_RANGE));
        Vec3
                eye =
                        vector(player.getPosition())
                                .add(new Vec3(0, PortalService.eyeHeight(player), 0)),
                direction = vector(player.getPosition().direction());
        double scale = 1;
        Instance world = player.getInstance();
        for (UUID id : request.path()) {
            PortalDefinition portal = service.find(id).orElse(null);
            if (portal == null
                    || !service.visible(player, portal)
                    || !portal.sourceWorld().equals(world.getDimensionName()))
                return Optional.empty();
            Vec3 end = eye.add(direction.multiply(remaining * scale));
            double t = PortalOptions.of(portal).intersection(portal, eye, end);
            if (!Double.isFinite(t) || !PortalOptions.of(portal).interactable())
                return Optional.empty();
            Vec3 hit = eye.add(end.subtract(eye).multiply(t));
            if (!PortalOptions.of(portal).contains(portal, hit)
                    || !clearSegment(world, eye, hit.subtract(direction.multiply(1e-5))))
                return Optional.empty();
            remaining -= Math.sqrt(hit.subtract(eye).lengthSquared()) / scale;
            direction = portal.rotation().rotate(direction).normalized();
            scale *= portal.scale();
            eye = portal.transformPoint(hit).add(direction.multiply(1e-5));
            world = service.instance(portal.destinationWorld());
            if (world == null) return Optional.empty();
        }
        Point block = null;
        Entity target = null;
        Vec3 hit;
        if (isEntity) {
            int id =
                    packet instanceof ClientAttackPacket attack
                            ? attack.targetId()
                            : ((ClientInteractEntityPacket) packet).targetId();
            for (Entity candidate : world.getEntities())
                if (candidate.getEntityId() == id) {
                    target = candidate;
                    break;
                }
            if (target == null || target == player || target.isRemoved()) return Optional.empty();
            Vec3 pos = vector(target.getPosition());
            var box = target.getBoundingBox();
            double t =
                    rayBox(
                            eye,
                            direction,
                            pos.add(vector(box.relativeStart())),
                            pos.add(vector(box.relativeEnd())),
                            remaining * scale);
            if (!Double.isFinite(t)) return Optional.empty();
            hit = eye.add(direction.multiply(Math.max(0, t - 1e-5)));
        } else {
            block =
                    packet instanceof ClientPlayerActionPacket action
                            ? action.blockPosition()
                            : ((ClientPlayerBlockPlacementPacket) packet).blockPosition();
            Vec3 low = vector(block);
            double t = rayBox(eye, direction, low, low.add(new Vec3(1, 1, 1)), remaining * scale);
            if (!Double.isFinite(t) || world.getChunkAt(block) == null) return Optional.empty();
            hit = eye.add(direction.multiply(Math.max(0, t - 1e-5)));
        }
        if (!clearSegment(world, eye, hit)) return Optional.empty();
        return Optional.of(
                new RemoteInteractionEvent(
                        player,
                        world,
                        request.path(),
                        packet,
                        Optional.ofNullable(block),
                        Optional.ofNullable(target)));
    }

    /** Exact ray intersection against each native collision box, including slabs and stairs. */
    static boolean clearSegment(Instance world, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double distance = Math.sqrt(delta.lengthSquared());
        if (distance < 1e-8) return true;
        if (distance > 4096) return false;
        Vec3 direction = delta.multiply(1 / distance);
        var iterator =
                new BlockIterator(
                        new Vec(start.x(), start.y(), start.z()),
                        new Vec(direction.x(), direction.y(), direction.z()),
                        0,
                        distance);
        int count = 0;
        while (iterator.hasNext() && count++ < 16384) {
            Point pos = iterator.next();
            if (world.getChunkAt(pos) == null) return false;
            var shape = world.getBlock(pos).collisionShape();
            if (shape instanceof ShapeImpl boxes)
                for (var box : boxes.boundingBoxes()) {
                    Vec3 base = vector(pos);
                    double hit =
                            rayBox(
                                    start,
                                    direction,
                                    base.add(vector(box.relativeStart())),
                                    base.add(vector(box.relativeEnd())),
                                    distance - 1e-5);
                    if (Double.isFinite(hit)) return false;
                }
        }
        return count < 16384;
    }

    static double rayBox(Vec3 origin, Vec3 direction, Vec3 low, Vec3 high, double max) {
        double enter = 0, exit = max;
        double[] p = {origin.x(), origin.y(), origin.z()},
                d = {direction.x(), direction.y(), direction.z()},
                a = {low.x(), low.y(), low.z()},
                b = {high.x(), high.y(), high.z()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-12) {
                if (p[i] < a[i] || p[i] > b[i]) return Double.NaN;
                continue;
            }
            double t1 = (a[i] - p[i]) / d[i], t2 = (b[i] - p[i]) / d[i];
            enter = Math.max(enter, Math.min(t1, t2));
            exit = Math.min(exit, Math.max(t1, t2));
            if (enter > exit) return Double.NaN;
        }
        return enter;
    }
}
