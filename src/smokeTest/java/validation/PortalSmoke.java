package validation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;

public final class PortalSmoke implements ModInitializer {
    private final java.util.Map<java.util.UUID, Long> arrived = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Boolean> pending = new java.util.HashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {
                    var from = server.overworld();
                    var to = server.getLevel(Level.NETHER);
                    for (var world : new net.minecraft.server.level.ServerLevel[] {from, to}) {
                        var stale = new java.util.ArrayList<Portal>();
                        for (var entity : world.getAllEntities())
                            if (entity instanceof Portal portal) stale.add(portal);
                        stale.forEach(Portal::discard);
                        for (int x = -12; x <= 12; x++)
                            for (int z = -12; z <= 12; z++) {
                                world.setBlock(
                                        new BlockPos(x, 69, z),
                                        (world == from ? Blocks.STONE : Blocks.EMERALD_BLOCK)
                                                .defaultBlockState(),
                                        3);
                                for (int y = 70; y < 77; y++)
                                    world.setBlock(
                                            new BlockPos(x, y, z),
                                            Blocks.AIR.defaultBlockState(),
                                            3);
                            }
                    }
                    Portal portal = new Portal(Portal.ENTITY_TYPE, from);
                    portal.setOriginPos(new Vec3(0, 72, 0));
                    portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 4, 4);
                    portal.setDestinationDimension(Level.NETHER);
                    portal.setDestination(new Vec3(0, 72, 0));
                    from.addFreshEntity(portal);
                    var reverse =
                            PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
                    to.addFreshEntity(reverse);
                    var triangle = extra(from, to, new Vec3(-3.75, 72, 0), 3, 4);
                    var mesh = new qouteall.q_misc_util.my_util.Mesh2D();
                    mesh.addTriangle(-1.0, -1.0, 1.0, -1.0, 0.0, 1.0);
                    triangle.setPortalShape(
                            new qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape(mesh));
                    triangle.setScaling(.5);
                    triangle.setRotationTransformation(
                            qouteall.q_misc_util.my_util.DQuaternion.rotationByDegrees(
                                    new Vec3(0, 1, 0), 90));
                    from.addFreshEntity(triangle);
                    triangle.addThisSideAnimationDriver(
                            new qouteall.imm_ptl.core.portal.animation.NormalAnimation(
                                    java.util.List.of(
                                            new qouteall.imm_ptl.core.portal.animation
                                                    .NormalAnimation.Phase(
                                                    40,
                                                    new qouteall.imm_ptl.core.portal.animation
                                                            .DeltaUnilateralPortalState(
                                                            new Vec3(0, .4, 0), null, (Vec3) null),
                                                    qouteall.imm_ptl.core.portal.animation
                                                            .TimingFunction.sine),
                                            new qouteall.imm_ptl.core.portal.animation
                                                    .NormalAnimation.Phase(
                                                    40,
                                                    qouteall.imm_ptl.core.portal.animation
                                                            .DeltaUnilateralPortalState.identity,
                                                    qouteall.imm_ptl.core.portal.animation
                                                            .TimingFunction.sine)),
                                    from.getGameTime(),
                                    100000,
                                    false));
                    var box = extra(from, to, new Vec3(3.75, 72, 0), 2.5, 4);
                    box.setPortalShape(
                            qouteall.imm_ptl.core.portal.shape.BoxPortalShape.FACING_OUTWARDS);
                    box.setThickness(2);
                    from.addFreshEntity(box);
                    var mirror =
                            new qouteall.imm_ptl.core.portal.Mirror(
                                    qouteall.imm_ptl.core.portal.Mirror.ENTITY_TYPE, from);
                    mirror.setOriginPos(new Vec3(0, 75.5, 0));
                    mirror.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 3, 2);
                    mirror.setDestinationDimension(Level.OVERWORLD);
                    mirror.setDestination(mirror.getOriginPos());
                    from.addFreshEntity(mirror);
                    var fused = extra(from, to, new Vec3(-3.75, 75.5, 0), 3, 2);
                    fused.setFuseView(true);
                    from.addFreshEntity(fused);
                    // Regression coverage for the inclusive region bound and negative world
                    // heights.
                    from.getChunk(-1, -1);
                    from.setBlock(
                            new BlockPos(-1, -60, -1), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                    var access =
                            qouteall.imm_ptl.core.portal.nether_portal.FastBlockAccess.from(
                                    from, new net.minecraft.world.level.ChunkPos(-1, -1), 0);
                    if (!access.getBlockState(-1, -60, -1).is(Blocks.DIAMOND_BLOCK)
                            || access.lX() != 1
                            || access.lZ() != 1)
                        throw new AssertionError("FastBlockAccess boundary regression");
                    System.out.println("PORTAL_SMOKE_ADVANCED_SHAPES_READY");
                    to.setBlock(new BlockPos(0, 70, -5), Blocks.CHEST.defaultBlockState(), 3);
                    System.out.println("PORTAL_SMOKE_SERVER_READY");
                });
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    handler.player.setGameMode(GameType.CREATIVE);
                    boolean scaled = handler.player.getGameProfile().name().contains("Scaled");
                    handler.player
                            .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)
                            .removeModifier(
                                    net.minecraft.resources.Identifier.parse("iportal:scaling"));
                    handler.player.teleportTo(
                            server.overworld(),
                            scaled ? -3.75 : 0,
                            70,
                            5,
                            java.util.Set.of(),
                            180,
                            0,
                            false);
                    pending.put(handler.player.getUUID(), scaled);
                    System.out.println("PORTAL_SMOKE_PLAYER_JOINED");
                });
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    for (var entry : java.util.List.copyOf(pending.entrySet())) {
                        var player = server.getPlayerList().getPlayer(entry.getKey());
                        if (player == null) {
                            pending.remove(entry.getKey());
                            continue;
                        }
                        if (player.level().dimension() == Level.NETHER) {
                            long first =
                                    arrived.computeIfAbsent(
                                            player.getUUID(), id -> server.getTickCount() * 1L);
                            if (server.getTickCount() - first < 10) continue;
                            if (entry.getValue()
                                    && Math.abs(
                                                    player.getAttributeValue(
                                                                    net.minecraft.world.entity.ai
                                                                            .attributes.Attributes
                                                                            .SCALE)
                                                            - .5)
                                            > .001)
                                throw new AssertionError("Server scale was not transformed");
                            if (entry.getValue()
                                    && Math.abs(
                                                    net.minecraft.util.Mth.wrapDegrees(
                                                            player.getYRot() - 90))
                                            > 1)
                                throw new AssertionError(
                                        "Server view was not rotated: " + player.getYRot());
                            pending.remove(entry.getKey());
                            System.out.println(
                                    entry.getValue()
                                            ? "PORTAL_SMOKE_SERVER_SCALED_TELEPORT_OK"
                                            : "PORTAL_SMOKE_SERVER_TELEPORT_OK");
                        }
                    }
                });
    }

    private static Portal extra(
            net.minecraft.server.level.ServerLevel source,
            net.minecraft.server.level.ServerLevel target,
            Vec3 origin,
            double width,
            double height) {
        Portal portal = new Portal(Portal.ENTITY_TYPE, source);
        portal.setOriginPos(origin);
        portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), width, height);
        portal.setDestinationDimension(target.dimension());
        portal.setDestination(new Vec3(0, 72, 0));
        return portal;
    }
}
