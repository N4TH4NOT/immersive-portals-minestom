package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface NativeCameraAccess {
    @Invoker("setupPerspective")
    void ip_setupPerspective(float near, float far, float fov, float width, float height);
}
