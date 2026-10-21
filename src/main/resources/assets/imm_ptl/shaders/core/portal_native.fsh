#version 330
uniform sampler2D PortalColor;
uniform sampler2D PortalDepth;
uniform sampler2D PortalMask;
layout(std140) uniform PortalTransform {
    mat4 PortalMvp;
    vec4 PortalViewport;
    mat4 SourceProjectionInverse;
    mat4 ParentProjection;
    vec4 PortalFlags;
    vec4 PortalClipPlane;
};
in float portalClipDistance;
out vec4 fragColor;
void main() {
    if (portalClipDistance < -0.0001) discard;
    vec2 uv = (gl_FragCoord.xy - PortalViewport.xy) / PortalViewport.zw;
    gl_FragDepth = gl_FragCoord.z;
    if ((int(PortalFlags.w + 0.5) & 1) != 0) {
        vec2 mask = texelFetch(PortalMask, ivec2(gl_FragCoord.xy), 0).rg;
        if (mask.r < 0.5) discard;
        gl_FragDepth = mask.g;
    }
    if (PortalFlags.x > 0.5) {
        float depth = texture(PortalDepth, uv).r;
        // Reverse Z: the cleared depth represents an empty remote world.
        if (depth <= 0.0) discard;
        float ndcDepth = PortalFlags.y > 0.5 ? depth : depth * 2.0 - 1.0;
        vec4 view = SourceProjectionInverse * vec4(uv * 2.0 - 1.0, ndcDepth, 1.0);
        if (PortalFlags.z > 0.5) view.x = -view.x;
        vec4 parent = ParentProjection * view;
        float parentDepth = parent.z / parent.w;
        gl_FragDepth = PortalFlags.y > 0.5 ? parentDepth : parentDepth * 0.5 + 0.5;
    }
    fragColor = texture(PortalColor, uv);
}
