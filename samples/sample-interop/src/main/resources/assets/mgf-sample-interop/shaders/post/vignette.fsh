#version 330

#moj_import <minecraft:globals.glsl>

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 delta = texCoord - vec2(0.5);
    delta.x *= ScreenSize.x / max(ScreenSize.y, 1.0);
    float dist = length(delta) * 1.41421356;
    float vignette = smoothstep(0.55, 1.05, dist);
    fragColor = vec4(0.0, 0.0, 0.0, vignette * 0.65);
}
