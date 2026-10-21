#version 330
in vec3 Position;
layout(std140) uniform PortalTransform {
    mat4 PortalMvp;
    vec4 PortalViewport;
    mat4 SourceProjectionInverse;
    mat4 ParentProjection;
    vec4 PortalFlags;
    vec4 PortalClipPlane;
};
out float portalClipDistance;
void main() {
    portalClipDistance = (int(PortalFlags.w + 0.5) & 4) != 0 ? dot(vec4(Position, 1.0), PortalClipPlane) : 1.0;
    gl_Position = PortalMvp * vec4(Position, 1.0);
    if ((int(PortalFlags.w + 0.5) & 2) != 0 && gl_Position.w > 0.0)
        gl_Position.z = clamp(gl_Position.z, PortalFlags.y > 0.5 ? 0.0 : -gl_Position.w, gl_Position.w);
}
