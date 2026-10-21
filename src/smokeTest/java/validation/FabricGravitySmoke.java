package validation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.github.n4th4not.immersiveportals.protocol.*;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.Locale;
import java.util.Set;

/** Shared client scenario against a real Fabric server with the published gravity mod. */
final class FabricGravitySmoke {
    static void init() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> {
                    var direction =
                            Commands.argument("direction", StringArgumentType.word())
                                    .executes(FabricGravitySmoke::setGravity);
                    dispatcher.register(
                            Commands.literal("gravity_smoke")
                                    .requires(
                                            source ->
                                                    source.getPlayer() != null
                                                            && source.getPlayer()
                                                                    .getGameProfile()
                                                                    .name()
                                                                    .equals("GravityTest"))
                                    .then(direction));
                });
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> join(handler.player, server));
    }

    private static int setGravity(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var api = GravityChangerInterface.invoker;
        String name = StringArgumentType.getString(context, "direction");
        if (name.equals("confirm")) {
            api.applyState(player, api.getState(player), false);
            System.out.println("FABRIC_GRAVITY_CONFIRM_OK");
            return 1;
        }
        boolean ready = name.equals("ready");
        GravityDirection direction =
                ready
                        ? GravityDirection.DOWN
                        : GravityDirection.valueOf(name.toUpperCase(Locale.ROOT));
        api.applyState(player, new GravityState(direction, 12), false);
        player.getAbilities().flying = !ready;
        player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(
                context.getSource().getServer().overworld(),
                0,
                ready ? 70 : 80,
                ready ? 3 : 5,
                Set.of(),
                180,
                0,
                false);
        System.out.println("FABRIC_GRAVITY_SET " + name);
        return 1;
    }

    private static void join(ServerPlayer player, MinecraftServer server) {
        if (!player.getGameProfile().name().equals("GravityTest")) return;
        GravityChangerInterface.invoker.applyState(
                player, new GravityState(GravityDirection.DOWN, 12), false);
        for (var entity : server.overworld().getAllEntities()) {
            if (entity instanceof Portal portal
                    && portal.getOriginPos().distanceTo(new Vec3(0, 72, 0)) < .1) {
                portal.setRotationTransformation(
                        DQuaternion.rotationByDegrees(new Vec3(1, 0, 0), 90));
                portal.setTeleportChangesGravity(true);
                portal.reloadAndSyncToClient();
            }
        }
        var target = server.getLevel(Level.NETHER);
        for (int x = -12; x <= 12; x++)
            for (int y = 60; y <= 90; y++)
                target.setBlock(
                        new BlockPos(x, y, -3), Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
    }
}
