#version 330
out vec4 fragColor;
in float portalClipDistance;
void main() {
    if (portalClipDistance < -0.0001) discard;
    fragColor = vec4(1.0, gl_FragCoord.z, 0.0, 1.0);
}
