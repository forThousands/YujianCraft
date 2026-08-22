#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    // Mix the sampled material texture toward white here. The old CPU path changed vertex RGB,
    // which was already white for item quads and therefore could never whiten the actual texture.
    color.rgb = mix(color.rgb, vec3(1.0), 0.90);

    fragColor = color * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}
