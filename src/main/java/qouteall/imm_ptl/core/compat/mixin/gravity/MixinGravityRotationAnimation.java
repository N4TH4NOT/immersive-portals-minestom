package qouteall.imm_ptl.core.compat.mixin.gravity;

import com.moigferdsrte.gravitychanger.client.GravityRotationAnimation;

import net.minecraft.core.Direction;

import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import qouteall.imm_ptl.core.compat.gravity.GravityPortalAnimation;

@Mixin(value = GravityRotationAnimation.class, remap = false)
public abstract class MixinGravityRotationAnimation {
    @Shadow
    public abstract void forceSet(Direction direction, long nowNanos);

    @Inject(
            method = "getRotation(Lnet/minecraft/core/Direction;J)Lorg/joml/Quaternionf;",
            at = @At("HEAD"))
    private void ip_snap(Direction direction, long now, CallbackInfoReturnable<Quaternionf> cir) {
        if (GravityPortalAnimation.isSnapping()) forceSet(direction, now);
    }
}
