package qouteall.imm_ptl.core.compat.iris_compatibility;

import qouteall.imm_ptl.core.render.renderer.RendererUsingFrameBuffer;

/** Iris uses the same isolated native world contexts and GPU compositor. */
public final class IrisCompatibilityPortalRenderer extends RendererUsingFrameBuffer {
    public static final IrisCompatibilityPortalRenderer instance = new IrisCompatibilityPortalRenderer();
    public static final IrisCompatibilityPortalRenderer debugModeInstance = new IrisCompatibilityPortalRenderer();
    public static void init() {}
}
