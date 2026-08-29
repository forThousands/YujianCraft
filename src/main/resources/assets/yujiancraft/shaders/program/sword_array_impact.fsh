#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 DistortionCenter;
uniform vec2 RadialCenter;
uniform vec2 ChromaCenter;
uniform vec2 VignetteCenter;
uniform vec2 FlowCenter;
uniform vec2 SignalBottom;
uniform vec2 SignalTop;
uniform float DistortionStrength;
uniform float DistortionRadius;
uniform float DistortionWidth;
uniform float RadialBlurStrength;
uniform float ChromaticStrength;
uniform float BlurStrength;
uniform float Exposure;
uniform float GlobalExposure;
uniform float Contrast;
uniform float Saturation;
uniform float ThresholdAmount;
uniform float ThresholdLevel;
uniform float ThresholdSoftness;
uniform float InvertAmount;
uniform float WhiteoutAmount;
uniform float BlackoutAmount;
uniform float ThresholdIsolation;
uniform float SignalRadius;
uniform float SignalBeamWidth;
uniform float SignalFeather;
uniform float FlowFlashAmount;
uniform float FlowInvertIntensity;
uniform float FlowTransitionStart;
uniform float FlowTransitionRange;
uniform float FlowInvertAmount;
uniform float FlowStrength;
uniform float FlowScale;
uniform float FlowSpeed;
uniform float FlowSharpness;
uniform float FlowEnabled;
uniform vec3 FlowHighlightColor;
uniform vec3 FlowShadowColor;
uniform float GrainStrength;
uniform float GrainScale;
uniform float VignetteStrength;
uniform float VignetteRadius;
uniform float VignetteSoftness;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), local.x),
            mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0)), local.x), local.y);
}

vec2 mirrorUv(vec2 uv) {
    return 1.0 - abs(mod(uv, 2.0) - 1.0);
}

vec2 aspectPoint(vec2 uv) {
    return vec2((uv.x - 0.5) * InSize.x / max(InSize.y, 1.0) + 0.5, uv.y);
}

vec3 scene(vec2 uv) {
    return texture(DiffuseSampler, mirrorUv(uv)).rgb;
}

void main() {
    vec2 uv = texCoord;

    // A world-anchored radial wave alters UVs before every later sample.
    vec2 aspectUv = aspectPoint(uv);
    vec2 aspectCentre = aspectPoint(DistortionCenter);
    vec2 delta = aspectUv - aspectCentre;
    float distanceToCentre = length(delta);
    float band = exp(-pow((distanceToCentre - DistortionRadius)
            / max(0.008, DistortionWidth), 2.0));
    vec2 direction = delta / max(distanceToCentre, 0.0001);
    uv += vec2(direction.x * InSize.y / max(InSize.x, 1.0), direction.y)
            * band * DistortionStrength * 0.055;

    // Eight samples form a bounded radial drag. The module is off when strength is zero.
    vec2 radialStep = (RadialCenter - uv) * RadialBlurStrength;
    vec3 colour = vec3(0.0);
    for (int index = 0; index < 8; index++) {
        float position = float(index) / 7.0;
        colour += scene(uv + radialStep * position);
    }
    colour /= 8.0;

    vec2 chromaDirection = uv - ChromaCenter;
    chromaDirection /= max(length(chromaDirection), 0.001);
    vec2 chromaOffset = chromaDirection * ChromaticStrength;
    vec3 separated = vec3(scene(uv + chromaOffset).r, scene(uv).g,
            scene(uv - chromaOffset).b);
    colour = mix(colour, separated, clamp(ChromaticStrength * 100.0, 0.0, 1.0));

    // One-pass mirrored blur is intentionally bounded for predictable finisher cost.
    vec2 pixelRadius = vec2(max(BlurStrength, 0.0)) / max(InSize, vec2(1.0));
    if (BlurStrength > 0.001) {
        vec3 blurred = scene(uv) * 0.28;
        blurred += (scene(uv + vec2(pixelRadius.x, 0.0))
                + scene(uv - vec2(pixelRadius.x, 0.0))) * 0.12;
        blurred += (scene(uv + vec2(0.0, pixelRadius.y))
                + scene(uv - vec2(0.0, pixelRadius.y))) * 0.12;
        blurred += (scene(uv + pixelRadius) + scene(uv - pixelRadius)) * 0.06;
        blurred += (scene(uv + vec2(pixelRadius.x, -pixelRadius.y))
                + scene(uv + vec2(-pixelRadius.x, pixelRadius.y))) * 0.06;
        colour = mix(colour, blurred, clamp(BlurStrength / 4.0, 0.0, 1.0));
    }

    colour *= exp2(Exposure);
    colour = (colour - 0.5) * Contrast + 0.5;
    float luminance = dot(colour, vec3(0.2126, 0.7152, 0.0722));
    colour = mix(vec3(luminance), colour, Saturation);
    colour = mix(colour, vec3(1.0) - colour, InvertAmount);

    // Threshold is a reusable black/white module, not a pre-authored mask sequence.
    luminance = dot(colour, vec3(0.2126, 0.7152, 0.0722));
    float monochrome = smoothstep(ThresholdLevel - ThresholdSoftness,
            ThresholdLevel + ThresholdSoftness, luminance);
    vec2 signalPoint = aspectPoint(uv);
    vec2 signalBottom = aspectPoint(SignalBottom);
    vec2 signalTop = aspectPoint(SignalTop);
    vec2 signalAxis = signalTop - signalBottom;
    float along = clamp(dot(signalPoint - signalBottom, signalAxis)
            / max(dot(signalAxis, signalAxis), 0.0001), 0.0, 1.0);
    float signalDistance = length(signalPoint - mix(signalBottom, signalTop, along));
    float beamSignal = 1.0 - smoothstep(SignalBeamWidth,
            SignalBeamWidth + max(0.002, SignalFeather), signalDistance);
    float arrayDistance = length(signalPoint - signalTop);
    float arraySignal = 1.0 - smoothstep(SignalRadius,
            SignalRadius + max(0.002, SignalFeather), arrayDistance);
    float signalMask = max(beamSignal, arraySignal);
    monochrome *= mix(1.0, signalMask, clamp(ThresholdIsolation, 0.0, 1.0));
    colour = mix(colour, vec3(monochrome), ThresholdAmount);
    colour = mix(colour, vec3(1.0), WhiteoutAmount);

    // A procedural, centre-out luminance transition inspired by dual-colour flash compositors.
    // It is resolution independent and avoids a fixed mask that would only fit one camera shot.
    vec2 flowPoint = aspectPoint(uv) - aspectPoint(FlowCenter);
    float flowRadius = length(flowPoint);
    float flowAngle = atan(flowPoint.y, flowPoint.x);
    float flowNoise = noise(vec2(flowAngle * 1.7 + Time * 0.11,
            flowRadius * FlowScale - Time * FlowSpeed));
    float radialMotion = flowRadius * 5.0 - Time * FlowSpeed;
    float angularStreak = sin(flowAngle * FlowScale + flowNoise * 3.5 + radialMotion) * 0.5 + 0.5;
    float flowWave = mix(0.5, smoothstep(0.42, 0.78, angularStreak), FlowEnabled);
    float boundary = (flowWave - 0.5) * FlowStrength
            + (flowNoise - 0.5) * FlowStrength * 0.8;
    float flowLuminance = dot(colour, vec3(0.2126, 0.7152, 0.0722));
    float mapped = smoothstep(FlowTransitionStart + boundary - FlowSharpness * 0.5,
            FlowTransitionStart + FlowTransitionRange + boundary + FlowSharpness * 0.5,
            flowLuminance);
    mapped = mix(mapped, 1.0 - mapped, clamp(FlowInvertAmount, 0.0, 1.0));
    vec3 flowColour = mix(FlowShadowColor, FlowHighlightColor, mapped);
    float flowingAmount = clamp(FlowFlashAmount * FlowInvertIntensity
            * (0.72 + 0.28 * flowWave), 0.0, 1.0);
    colour = mix(colour, flowColour, flowingAmount);

    float grain = (hash(gl_FragCoord.xy / max(0.25, GrainScale)
            + floor(Time * 40.0)) - 0.5) * GrainStrength;
    colour += grain;

    vec2 vignettePoint = aspectPoint(uv) - aspectPoint(VignetteCenter);
    float vignetteDistance = length(vignettePoint);
    float vignette = smoothstep(VignetteRadius,
            VignetteRadius + max(0.001, VignetteSoftness), vignetteDistance);
    colour *= 1.0 - vignette * VignetteStrength;
    // Developer exposure is a final display adjustment. It deliberately runs after threshold
    // classification so lowering brightness cannot make the sword-array signal misclassify.
    colour *= exp2(GlobalExposure);
    // Warp transitions use a true neutral black frame. Keeping it as the final compositing
    // operation prevents shader packs, sky exposure and bloom from lifting it back to grey.
    colour = mix(colour, vec3(0.0), clamp(BlackoutAmount, 0.0, 1.0));

    fragColor = vec4(clamp(colour, 0.0, 1.0), 1.0);
}
