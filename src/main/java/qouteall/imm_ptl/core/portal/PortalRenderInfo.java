package qouteall.imm_ptl.core.portal;

import qouteall.imm_ptl.core.render.context_management.RenderStates;

/** Per-portal visibility accounting; GPU targets are owned by NativeWorldRenderer scenes. */
public final class PortalRenderInfo implements AutoCloseable {
    private long lastRenderedFrame = -1;
    public static void init() {
        Portal.PORTAL_DISPOSE_SIGNAL.register(portal -> { if (portal.portalRenderInfo != null) portal.portalRenderInfo.close(); });
    }
    public static PortalRenderInfo getOptional(Portal portal) { return portal.portalRenderInfo; }
    public static PortalRenderInfo get(Portal portal) {
        if (portal.portalRenderInfo == null) portal.portalRenderInfo = new PortalRenderInfo();
        return portal.portalRenderInfo;
    }
    public void markRendered() { lastRenderedFrame = RenderStates.frameIndex; }
    public boolean wasRenderedLastFrame() { return lastRenderedFrame >= RenderStates.frameIndex - 1; }
    public void dispose() { close(); }
    @Override public void close() { lastRenderedFrame = -1; }
}
