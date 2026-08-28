#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 curtainData;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.006) {
        discard;
    }

    // R carries the CPU-authored phase, G carries brightness in a 0..1.55 range,
    // B controls the travelling highlight and A remains ordinary opacity.
    float phase = curtainData.r;
    float brightness = max(0.05, curtainData.g * 1.55);
    float flowStrength = curtainData.b;
    float travel = fract(texCoord0.y - phase);
    float core = exp(-pow((travel - 0.50) * 7.5, 2.0));
    float wake = exp(-pow((travel - 0.72) * 3.6, 2.0)) * 0.24;
    float luminance = dot(texel.rgb, vec3(0.2126, 0.7152, 0.0722));
    float glyphAffinity = smoothstep(0.34, 0.88, luminance);
    float flow = flowStrength * (core * (0.40 + glyphAffinity * 1.55) + wake);

    vec3 colour = texel.rgb * brightness * (1.0 + flow);
    float alpha = texel.a * curtainData.a * ColorModulator.a;
    fragColor = vec4(colour * ColorModulator.rgb, alpha);
}
