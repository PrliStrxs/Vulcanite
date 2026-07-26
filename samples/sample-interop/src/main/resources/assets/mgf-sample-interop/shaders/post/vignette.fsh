#version 330

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 delta = texCoord - vec2(0.5);
    float dist = length(delta) * 1.41421356;
    float vignette = smoothstep(0.55, 1.05, dist);
    fragColor = vec4(0.0, 0.0, 0.0, vignette * 0.65);
}
