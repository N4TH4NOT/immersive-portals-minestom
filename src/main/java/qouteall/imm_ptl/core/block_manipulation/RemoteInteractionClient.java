package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Minestom targeting follows the same bounded portal path and reach as the server validator. */
final class RemoteInteractionClient {
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static final int MAX_PORTALS = 3;

    static void updateTarget(float partialTick) {
        ClientLevel world = CLIENT.level;
        Vec3 from = CLIENT.player.getEyePosition(partialTick);
        Vec3 direction = CLIENT.player.getViewVector(partialTick).normalize();
        double blockReach = Math.min(6, CLIENT.player.blockInteractionRange());
        double entityReach = Math.min(6, CLIENT.player.entityInteractionRange());
        double reach = Math.max(blockReach, entityReach);
        double travelled = 0, scale = 1;
        var path = new ArrayList<UUID>();

        while (travelled < reach) {
            // The server rejects terrain segments longer than 4096 blocks, including scaled views.
            double segmentLength = Math.min(4096, (reach - travelled) * scale);
            Vec3 end = from.add(direction.scale(segmentLength));
            BlockHitResult block = BlockManipulationClient.clipBlocks(world, from, end);
            double blockDistance =
                    block.getType() == HitResult.Type.BLOCK
                            ? from.distanceTo(block.getLocation())
                            : Double.POSITIVE_INFINITY;
            EntityHitResult entity =
                    pickEntity(
                            world,
                            from,
                            direction,
                            Math.min(segmentLength, Math.max(0, entityReach - travelled) * scale),
                            blockDistance);
            double entityDistance =
                    entity == null
                            ? Double.POSITIVE_INFINITY
                            : from.distanceTo(entity.getLocation());
            var portalHit =
                    PortalUtils.raytracePortals(
                            world,
                            from,
                            end,
                            true,
                            portal ->
                                    !(portal instanceof Mirror)
                                            && portal.isInteractableBy(CLIENT.player));
            double portalDistance =
                    portalHit.isEmpty()
                            ? Double.POSITIVE_INFINITY
                            : from.distanceTo(portalHit.get().getSecond().hitPos());

            if (portalHit.isPresent()
                    && portalDistance < Math.min(blockDistance, entityDistance) + 1e-5) {
                // Hide geometry in the source world behind the first opening, even on a miss.
                if (path.isEmpty())
                    CLIENT.hitResult =
                            BlockHitResult.miss(
                                    portalHit.get().getSecond().hitPos(),
                                    Direction.getApproximateNearest(
                                            direction.x, direction.y, direction.z),
                                    BlockPos.containing(portalHit.get().getSecond().hitPos()));
                if (path.size() == MAX_PORTALS) return;
                Portal portal = portalHit.get().getFirst();
                ClientLevel destination = ClientWorldLoader.getOptionalWorld(portal.getDestDim());
                if (destination == null) return;
                travelled += portalDistance / scale;
                scale *= portal.getScale();
                direction = portal.transformLocalVecNonScale(direction).normalize();
                from =
                        portal.transformPoint(portalHit.get().getSecond().hitPos())
                                .add(direction.scale(1e-5));
                path.add(portal.getUUID());
                world = destination;
                continue;
            }

            if (path.isEmpty()) return;
            HitResult target =
                    entityDistance < blockDistance
                            ? entity
                            : blockDistance / scale + travelled <= blockReach + 1e-6 ? block : null;
            if (target != null && target.getType() != HitResult.Type.MISS) {
                BlockManipulationClient.remotePointedDim = world.dimension();
                BlockManipulationClient.remoteHitResult = target;
                BlockManipulationClient.remotePortalPath = List.copyOf(path);
            }
            return;
        }
    }

    private static EntityHitResult pickEntity(
            ClientLevel world, Vec3 from, Vec3 direction, double reach, double blockDistance) {
        if (reach <= 0) return null;
        Vec3 end = from.add(direction.scale(reach));
        EntityHitResult result = null;
        double nearest = Math.min(reach, blockDistance);
        for (Entity entity :
                world.getEntities(
                        CLIENT.player,
                        new AABB(from, end).inflate(1),
                        entity ->
                                !(entity instanceof Portal)
                                        && !entity.isSpectator()
                                        && entity.isPickable())) {
            AABB box = entity.getBoundingBox();
            var hit = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, end);
            if (hit.isEmpty()) continue;
            double distance = from.distanceTo(hit.get());
            if (distance <= nearest) {
                nearest = distance;
                result = new EntityHitResult(entity, hit.get());
            }
        }
        return result;
    }
}
