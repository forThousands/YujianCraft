#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D FractureSampler;
uniform sampler2D InkSampler;

uniform vec2 InSize;
uniform vec2 BeamBottom;
uniform vec2 BeamTop;
uniform float BeamRadius;
uniform float Charge;
uniform float DarkAmount;
uniform float Expansion;
uniform float WhiteAmount;
uniform float InkAmount;
uniform float Recovery;
uniform float Distortion;
uniform float ChromaAmount;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

mat2 rotate2d(float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return mat2(cosine, -sine, sine, cosine);
}

float segmentDistance(vec2 point, vec2 start, vec2 end, out float along) {
    vec2 segment = end - start;
    float denominator = max(dot(segment, segment), 0.000001);
    along = clamp(dot(point - start, segment) / denominator, 0.0, 1.0);
    return length(point - (start + segment * along));
}

void main() {
    float aspect = InSize.x / max(InSize.y, 1.0);
    vec2 point = vec2(texCoord.x * aspect, texCoord.y);
    vec2 bottom = vec2(BeamBottom.x * aspect, BeamBottom.y);
    vec2 top = vec2(BeamTop.x * aspect, BeamTop.y);
    vec2 segment = top - bottom;
    vec2 tangent = segment / max(length(segment), 0.0001);
    vec2 normal = vec2(-tangent.y, tangent.x);
    vec2 uvNormal = vec2(normal.x / aspect, normal.y);
    vec2 impactCentre = mix(bottom, top, 0.16);

    vec2 noiseUv = fract(texCoord * vec2(1.63, 1.21) + vec2(Time * 0.019, -Time * 0.013));
    vec3 noise = texture(NoiseSampler, noiseUv).rgb;
    vec2 fractureUv = (point - impactCentre) * rotate2d(0.16 + Time * 0.004) * 0.88 + vec2(0.5);
    vec3 fracture = texture(FractureSampler, fractureUv).rgb;
    vec2 inkUv = fract(texCoord * vec2(1.07, 0.93) + vec2(Time * 0.003, -Time * 0.002));
    vec3 ink = texture(InkSampler, inkUv).rgb;

    vec2 radial = point - impactCentre;
    float radialDistance = length(radial);
    vec2 radialDirection = radial / max(radialDistance, 0.0001);
    float shockBand = exp(-pow((radialDistance - (0.10 + Expansion * 0.74)) * 11.0, 2.0));
    float broadTear = (ink.r - 0.5) * 0.018 + (ink.g - 0.34) * 0.012;
    vec2 warpedUv = texCoord + vec2(radialDirection.x / aspect, radialDirection.y)
            * (shockBand * Distortion * 0.068 + broadTear * Distortion);
    warpedUv += vec2(noise.b - 0.5, noise.g - 0.5) * Distortion * 0.008;
    warpedUv = clamp(warpedUv, vec2(0.001), vec2(0.999));

    float along;
    float beamDistance = segmentDistance(point, bottom, top, along);
    vec2 closestPoint = mix(bottom, top, along);
    float signedSide = dot(point - closestPoint, normal);
    float endEnergy = pow(max(0.0, sin(3.14159265 * along)), 0.24);
    float ragged = (noise.r - 0.5) * 0.34 + (noise.g - 0.5) * 0.18
            + (ink.b - 0.5) * 0.16;
    float localRadius = max(0.0015, BeamRadius * (0.88 + ragged));
    float beamBody = 1.0 - smoothstep(localRadius * 0.76, localRadius * 1.16, beamDistance);
    float beamCore = 1.0 - smoothstep(localRadius * 0.10, localRadius * 0.38, beamDistance);
    beamBody *= 0.60 + 0.40 * endEnergy;

    // Continuous exponential falloff produces a welding-like glare without concentric bands.
    float outside = max(0.0, beamDistance - localRadius * 0.72);
    float nearHalo = exp(-outside / max(0.006, localRadius * 0.72));
    float wideHalo = exp(-outside / max(0.022, localRadius * 2.55));
    float glare = clamp(beamBody + nearHalo * 0.54 + wideHalo * 0.20, 0.0, 1.0);
    float chargeGlow = clamp(glare * (0.16 + Charge * 0.84), 0.0, 1.0);

    // Large blocks and vertical tears punch readable voids through the pillar. Hairline cracks
    // are intentionally excluded here; they made the former full-screen frame look dirty.
    float blockField = ink.r * 0.66 + ink.b * 0.22 + noise.r * 0.12;
    float broadHole = smoothstep(0.55, 0.69, blockField);
    float columnHole = smoothstep(0.38, 0.70, ink.g + (noise.b - 0.5) * 0.14);
    float hole = clamp(max(broadHole * 0.72, columnHole * 0.94), 0.0, 1.0);
    float brokenBeam = beamBody * (1.0 - hole * 0.90);
    float brokenCore = beamCore * (1.0 - hole * 0.76);
    float pillarWhite = clamp(max(brokenCore, brokenBeam * (0.74 + noise.g * 0.26)), 0.0, 1.0);

    // RGB separation is confined to a discontinuous high-contrast rim, so it reads as optical
    // dispersion instead of making the entire game blurry.
    float rimDistance = abs(beamDistance - localRadius * 0.98);
    float rim = exp(-pow(rimDistance / max(0.003, localRadius * 0.15), 2.0));
    float rimBreak = smoothstep(0.43, 0.61,
            noise.b * 0.58 + ink.b * 0.34 + fracture.g * 0.08);
    vec3 rimColour = signedSide >= 0.0
            ? vec3(1.0, 0.10, 0.015)
            : vec3(0.02, 0.30, 1.0);
    vec3 chromaFringe = rimColour * rim * rimBreak * ChromaAmount;

    float chromaShift = ChromaAmount * (0.0014 + rim * 0.0028);
    vec3 scene;
    scene.r = texture(DiffuseSampler, clamp(warpedUv + uvNormal * chromaShift,
            vec2(0.001), vec2(0.999))).r;
    scene.g = texture(DiffuseSampler, warpedUv).g;
    scene.b = texture(DiffuseSampler, clamp(warpedUv - uvNormal * chromaShift,
            vec2(0.001), vec2(0.999))).b;

    vec3 chargedScene = mix(scene, vec3(1.0), chargeGlow * (1.0 - DarkAmount));

    // The impact image is genuinely black outside the broken pillar. The colour fringe is the
    // only chroma deliberately retained during this beat.
    vec3 impactImage = vec3(pillarWhite);
    impactImage += chromaFringe * (1.0 - pillarWhite);
    vec3 colour = mix(chargedScene, clamp(impactImage, 0.0, 1.0), DarkAmount);

    // White grows from the pillar through an irregular front. Only the last part becomes a true
    // full-white frame, rather than lifting the entire screen as soon as expansion begins.
    if (WhiteAmount > 0.001) {
        float expandedRadius = localRadius * (1.05 + WhiteAmount * 2.40);
        float expandedPillar = 1.0 - smoothstep(expandedRadius * 0.72,
                expandedRadius * 1.14, beamDistance);
        float whiteSeed = ink.b * 0.52 + ink.r * 0.28 + noise.r * 0.20;
        float irregularFront = smoothstep(0.82 - WhiteAmount * 0.88,
                0.91 - WhiteAmount * 0.88, whiteSeed);
        float partialWhite = clamp(max(expandedPillar, irregularFront * WhiteAmount)
                * WhiteAmount, 0.0, 1.0);
        float fullWhite = smoothstep(0.91, 0.995, WhiteAmount);
        colour = mix(colour, vec3(1.0), max(partialWhite, fullWhite));
    }

    // After the white frame, broad quantised fields form ink-wash black, grey and white masses.
    // Grey transitions remain visible at boundaries instead of reducing the frame to binary noise.
    if (InkAmount > 0.001) {
        float inkField = ink.b * 0.48 + ink.r * 0.34 + noise.r * 0.18;
        float inkTone = smoothstep(0.18, 0.31, inkField) * 0.24
                + smoothstep(0.37, 0.51, inkField) * 0.31
                + smoothstep(0.61, 0.75, inkField) * 0.45;
        inkTone = max(inkTone, pillarWhite * (0.70 + noise.g * 0.30));
        colour = mix(colour, vec3(clamp(inkTone, 0.0, 1.0)), InkAmount);
    }

    // Colour returns through broad ink islands. The pillar stays white-hot during the reveal but
    // fades before the post chain ends, preventing a one-frame pop back to normal gameplay.
    if (Recovery > 0.001) {
        float revealField = ink.b * 0.62 + noise.r * 0.38;
        float returnMask = smoothstep(0.92 - Recovery, 1.12 - Recovery, revealField);
        returnMask *= smoothstep(0.0, 0.08, Recovery);
        colour = mix(colour, scene, returnMask);
        float remainingEnergy = 1.0 - smoothstep(0.55, 1.0, Recovery);
        colour = mix(colour, vec3(1.0), clamp((brokenCore * 0.92 + nearHalo * 0.24)
                * remainingEnergy, 0.0, 1.0));
    }

    fragColor = vec4(clamp(colour, 0.0, 1.0), 1.0);
}
