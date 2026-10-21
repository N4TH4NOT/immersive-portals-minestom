package qouteall.imm_ptl.core.compat.iris_compatibility;

import qouteall.imm_ptl.core.render.renderer.RendererUsingFrameBuffer;

/** Iris uses the same isolated native world contexts and GPU compositor. */
public final class ExperimentalIrisPortalRenderer extends RendererUsingFrameBuffer {
    public static final ExperimentalIrisPortalRenderer instance = new ExperimentalIrisPortalRenderer();
    public static final ExperimentalIrisPortalRenderer debugModeInstance = new ExperimentalIrisPortalRenderer();
    public static void init() {}
}
