#version 330
in vec3 Position;
layout(std140) uniform PortalTransform {
    mat4 PortalMvp;
    vec4 PortalViewport;
    mat4 SourceProjectionInverse;
    mat4 ParentProjection;
    vec4 PortalFlags;
};
void main() {
    gl_Position = PortalMvp * vec4(Position, 1.0);
}
