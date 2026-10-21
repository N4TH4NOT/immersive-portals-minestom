package io.github.n4th4not.immersiveportals.example;

import io.github.n4th4not.immersiveportals.minestom.PortalService;
import io.github.n4th4not.immersiveportals.protocol.*;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.*;
import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.*;
import net.minestom.server.network.packet.client.play.*;
import net.minestom.server.world.DimensionType;

import java.util.UUID;

/** Local offline example. Change the authentication mode before exposing your own server. */
public final class ExampleServer {
    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init(new Auth.Offline());
        boolean interactionValidation = Boolean.getBoolean("portal.example.interactions");
        int galleryOffset = interactionValidation ? 20 : 0;
        var manager = MinecraftServer.getInstanceManager();
        var lobby =
                new InstanceContainer(
                        UUID.randomUUID(), DimensionType.OVERWORLD, Key.key("demo:lobby"));
        var gallery =
                new InstanceContainer(
                        UUID.randomUUID(), DimensionType.OVERWORLD, Key.key("demo:gallery"));
        manager.registerInstance(lobby);
        manager.registerInstance(gallery);
        lobby.setGenerator(unit -> unit.modifier().fillHeight(0, 70, Block.STONE));
        gallery.setGenerator(unit -> unit.modifier().fillHeight(0, 70, Block.EMERALD_BLOCK));
        PortalService portals = new PortalService();
        portals.create(
                lobby,
                gallery,
                builder ->
                        builder.origin(new Vec3(0, 72, 0))
                                .destination(new Vec3(galleryOffset, 72, 0))
                                .size(4, 4));
        portals.create(
                gallery,
                lobby,
                builder ->
                        builder.origin(new Vec3(galleryOffset, 72, 0))
                                .destination(new Vec3(0, 72, 0))
                                .axes(new Vec3(-1, 0, 0), new Vec3(0, 1, 0))
                                .size(4, 4));
        gallery.loadChunk(galleryOffset >> 4, -1).join();
        gallery.setBlock(galleryOffset, 71, -2, Block.CHEST);
        LivingEntity marker = new LivingEntity(EntityType.ARMOR_STAND);
        marker.setInstance(gallery, new Pos(galleryOffset + 2, 70, -4));
        marker.setEquipment(
                EquipmentSlot.HELMET,
                net.minestom.server.item.ItemStack.of(
                        net.minestom.server.item.Material.DIAMOND_HELMET));
        if (interactionValidation) {
            var annex = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD, Key.key("demo:annex"));
            manager.registerInstance(annex);
            annex.setGenerator(unit -> unit.modifier().fillHeight(0, 70, Block.STONE));
            annex.loadChunk(0, -1).join();
            annex.setBlock(0, 71, -1, Block.STONE);
            portals.create(gallery, annex, p -> p.origin(new Vec3(galleryOffset, 72, -1))
                .destination(new Vec3(0, 72, 0)).size(2, 3));
            var target = new LivingEntity(EntityType.ARMOR_STAND);
            target.setInstance(gallery, new Pos(galleryOffset + 1.5, 70, -.4)).join();
        }
        MinecraftServer.getGlobalEventHandler()
                .addListener(
                        AsyncPlayerConfigurationEvent.class,
                        event -> {
                            event.setSpawningInstance(lobby);
                            event.getPlayer().setRespawnPoint(new Pos(.5, 70, interactionValidation ? 2 : 3, 180, 0));
                        });
        MinecraftServer.getGlobalEventHandler()
                .addListener(
                        PlayerSpawnEvent.class,
                        event -> {
                            event.getPlayer().setGameMode(GameMode.CREATIVE);
                            System.out.println(
                                    "MINESTOM_PLAYER_WORLD "
                                            + event.getInstance().getDimensionName());
                        });
        portals.onRemoteInteraction(
                event -> {
                    if (interactionValidation) {
                        String markerMessage = null;
                        if (event.packet() instanceof ClientPlayerBlockPlacementPacket)
                            markerMessage = "MINESTOM_REMOTE_BLOCK_USE " + event.portalPath().size();
                        if (event.packet() instanceof ClientPlayerActionPacket action
                            && action.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING)
                            markerMessage = "MINESTOM_REMOTE_BLOCK_ATTACK " + event.portalPath().size();
                        if (event.packet() instanceof ClientInteractEntityPacket)
                            markerMessage = "MINESTOM_REMOTE_ENTITY_USE_OK";
                        if (event.packet() instanceof ClientAttackPacket)
                            markerMessage = "MINESTOM_REMOTE_ENTITY_ATTACK_OK";
                        if (markerMessage != null) {
                            System.out.println(markerMessage);
                            event.player().sendMessage(Component.text(markerMessage));
                        }
                        // Intentionally no gameplay action: callbacks must arrive regardless.
                        return;
                    }
                    if (event.packet() instanceof ClientPlayerBlockPlacementPacket
                            && event.blockPosition().isPresent()
                            && event.instance()
                                    .getBlock(event.blockPosition().get())
                                    .compare(Block.CHEST)) {
                        Inventory inventory =
                                new Inventory(
                                        InventoryType.CHEST_3_ROW,
                                        Component.text("Chest through the portal"));
                        event.player().openInventory(inventory);
                        System.out.println("MINESTOM_REMOTE_CHEST_OK");
                    }
                    if (event.packet() instanceof ClientPlayerActionPacket action
                            && event.player().getGameMode() == GameMode.CREATIVE
                            && action.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING)
                        event.instance()
                                .breakBlock(
                                        event.player(), action.blockPosition(), action.blockFace());
                });
        Runtime.getRuntime().addShutdownHook(new Thread(portals::close));
        server.start("127.0.0.1", Integer.getInteger("portal.example.port", 25566));
        System.out.println("MINESTOM_PORTAL_EXAMPLE_READY");
    }
}
