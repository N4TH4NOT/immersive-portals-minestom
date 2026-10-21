package io.github.n4th4not.immersiveportals.example;

import io.github.n4th4not.immersiveportals.minestom.*;
import io.github.n4th4not.immersiveportals.protocol.*;

import net.kyori.adventure.key.Key;
import net.minestom.server.*;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.world.DimensionType;

import java.util.UUID;

/** Opt-in gravity example and runtime verification scene. */
final class GravityExample {
    static void run(MinecraftServer server) {
        var lobby =
                new InstanceContainer(
                        UUID.randomUUID(), DimensionType.OVERWORLD, Key.key("demo:lobby"));
        var gallery =
                new InstanceContainer(
                        UUID.randomUUID(), DimensionType.OVERWORLD, Key.key("demo:gallery"));
        MinecraftServer.getInstanceManager().registerInstance(lobby);
        MinecraftServer.getInstanceManager().registerInstance(gallery);
        lobby.setGenerator(unit -> unit.modifier().fillHeight(0, 70, Block.STONE));
        lobby.loadChunk(0, 0).join();
        for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++) gallery.loadChunk(x, z).join();
        for (int x = -12; x <= 12; x++)
            for (int y = 60; y <= 90; y++) gallery.setBlock(x, y, -3, Block.EMERALD_BLOCK);
        var service = new PortalService();
        var options = PortalOptions.builder().changesGravity(true).build().encode();
        var rotation = Quaternion.axisAngle(new Vec3(1, 0, 0), Math.PI / 2);
        service.create(
                lobby,
                gallery,
                p ->
                        p.origin(new Vec3(0, 72, 0))
                                .destination(new Vec3(0, 72, 0))
                                .size(4, 4)
                                .rotation(rotation)
                                .extraNbt(options));
        service.create(
                gallery,
                lobby,
                p ->
                        p.origin(new Vec3(0, 72, 0))
                                .destination(new Vec3(0, 72, 0))
                                .axes(new Vec3(-1, 0, 0), new Vec3(0, 0, 1))
                                .size(4, 4)
                                .rotation(Quaternion.axisAngle(new Vec3(1, 0, 0), -Math.PI / 2))
                                .extraNbt(options));
        var marker = new LivingEntity(EntityType.ARMOR_STAND);
        marker.setInstance(gallery, new Pos(2, 75, -2)).join();
        service.gravity().set(marker, new GravityState(GravityDirection.NORTH, 12));
        service.onRemoteInteraction(
                event -> {
                    if (event.packet()
                                    instanceof
                                    net.minestom.server.network.packet.client.play
                                            .ClientPlayerBlockPlacementPacket
                            && event.blockPosition().isPresent()
                            && event.instance()
                                    .getBlock(event.blockPosition().get())
                                    .compare(Block.CHEST)) {
                        if (service.gravity().get(event.player()).direction()
                                != GravityDirection.NORTH)
                            throw new AssertionError(
                                    "Remote interaction did not use directional gravity");
                        event.player()
                                .openInventory(
                                        new net.minestom.server.inventory.Inventory(
                                                net.minestom.server.inventory.InventoryType
                                                        .CHEST_3_ROW,
                                                net.kyori.adventure.text.Component.text(
                                                        "Gravity portal chest")));
                        System.out.println("MINESTOM_GRAVITY_REMOTE_INTERACTION_OK");
                    }
                });
        marker.setEquipment(
                EquipmentSlot.HELMET,
                net.minestom.server.item.ItemStack.of(
                        net.minestom.server.item.Material.DIAMOND_HELMET));
        var command = new Command("gravity_smoke");
        var direction = ArgumentType.Word("direction");
        command.setCondition(
                (sender, text) ->
                        sender instanceof Player p && p.getUsername().startsWith("Gravity"));
        command.addSyntax(
                (sender, context) -> {
                    var player = (Player) sender;
                    String value = context.get(direction);
                    if (!service.supportsGravity(player)) return;
                    if (value.equals("ready")) {
                        service.gravity().set(player, new GravityState(GravityDirection.DOWN, 12));
                        player.setFlying(false);
                        player.setVelocity(Vec.ZERO);
                        player.teleport(new Pos(.5, 70, 3, 180, 0));
                    } else {
                        var gravity =
                                GravityDirection.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                        service.gravity().set(player, new GravityState(gravity, 12));
                        player.setFlying(true);
                        player.setVelocity(Vec.ZERO);
                        player.teleport(new Pos(.5, 80, 5, 180, 0));
                    }
                    System.out.println("MINESTOM_GRAVITY_SET " + value);
                },
                direction);
        MinecraftServer.getCommandManager().register(command);
        MinecraftServer.getGlobalEventHandler()
                .addListener(
                        AsyncPlayerConfigurationEvent.class,
                        e -> {
                            e.setSpawningInstance(lobby);
                            e.getPlayer().setRespawnPoint(new Pos(.5, 70, 3, 180, 0));
                        });
        MinecraftServer.getGlobalEventHandler()
                .addListener(
                        PlayerSpawnEvent.class,
                        e -> {
                            e.getPlayer().setGameMode(GameMode.CREATIVE);
                            e.getPlayer().setAllowFlying(true);
                            if (e.getInstance() == lobby) lobby.setBlock(0, 71, 2, Block.AIR);
                            System.out.println(
                                    "MINESTOM_GRAVITY_PLAYER_WORLD "
                                            + e.getInstance().getDimensionName());
                        });
        var verified = java.util.concurrent.ConcurrentHashMap.<UUID>newKeySet();
        MinecraftServer.getGlobalEventHandler()
                .addListener(
                        PlayerDisconnectEvent.class, e -> verified.remove(e.getPlayer().getUuid()));
        MinecraftServer.getSchedulerManager()
                .buildTask(
                        () -> {
                            for (var player :
                                    MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                                if (player.getInstance() != gallery
                                        || !verified.add(player.getUuid())) continue;
                                if (service.gravity().get(player).direction()
                                        != GravityDirection.NORTH)
                                    throw new AssertionError(
                                            "Server traversal did not rotate gravity");
                                lobby.setBlock(0, 71, 2, Block.CHEST);
                                System.out.println(
                                        "MINESTOM_GRAVITY_TELEPORT_OK "
                                                + service.gravity().get(player));
                            }
                        })
                .repeat(TaskSchedule.tick(1))
                .schedule();
        Runtime.getRuntime().addShutdownHook(new Thread(service::close));
        server.start("127.0.0.1", Integer.getInteger("portal.example.port", 25566));
        System.out.println("MINESTOM_GRAVITY_EXAMPLE_READY");
    }
}
