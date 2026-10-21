package qouteall.imm_ptl.core.compat.iris_compatibility;

import qouteall.imm_ptl.core.render.renderer.RendererUsingFrameBuffer;

/** Iris uses the same isolated native world contexts and GPU compositor. */
public final class IrisPortalRenderer extends RendererUsingFrameBuffer {
    public static final IrisPortalRenderer instance = new IrisPortalRenderer();
    public static final IrisPortalRenderer debugModeInstance = new IrisPortalRenderer();
    public static void init() {}
}
