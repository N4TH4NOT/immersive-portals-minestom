#version 330
in vec3 Position;
layout(std140) uniform MaskTransform {
    mat4 MaskMvp;
    vec4 MaskFlags;
    vec4 MaskClipPlane;
};
out float portalClipDistance;
void main() {
    portalClipDistance = MaskFlags.z > 0.5 ? dot(vec4(Position, 1.0), MaskClipPlane) : 1.0;
    gl_Position = MaskMvp * vec4(Position, 1.0);
    if (MaskFlags.x > 0.5 && gl_Position.w > 0.0)
        gl_Position.z = clamp(gl_Position.z, MaskFlags.y > 0.5 ? 0.0 : -gl_Position.w, gl_Position.w);
}
