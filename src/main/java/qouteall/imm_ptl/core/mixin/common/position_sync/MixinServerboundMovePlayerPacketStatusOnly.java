package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;
import qouteall.q_misc_util.dimension.DimId;

@Mixin(ServerboundMovePlayerPacket.StatusOnly.class)
public class MixinServerboundMovePlayerPacketStatusOnly {
    @Inject(method = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$StatusOnly;read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$StatusOnly;", at = @At("RETURN"), cancellable = true)
    private static void onRead(
        FriendlyByteBuf buf, CallbackInfoReturnable<ServerboundMovePlayerPacket.StatusOnly> cir
    ) {
        ResourceKey<Level> playerDim = DimId.readWorldId(buf, false);
        ((IEPlayerMoveC2SPacket) cir.getReturnValue()).ip_setPlayerDimension(playerDim);
    }
}