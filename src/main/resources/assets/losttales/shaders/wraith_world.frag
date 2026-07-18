#version 120

// Animated Wraith-world perception pass.
uniform sampler2D scene;
uniform sampler2D previousScene;
uniform float effectIntensity;
uniform float time;
uniform float aspectRatio;
uniform vec2 texelSize;

varying vec2 textureCoordinate;

float hash(vec2 point) {
    return fract(sin(dot(point, vec2(12.9898, 78.233))) * 43758.5453);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float lower = mix(hash(cell), hash(cell + vec2(1.0, 0.0)),
            local.x);
    float upper = mix(hash(cell + vec2(0.0, 1.0)),
            hash(cell + vec2(1.0, 1.0)), local.x);
    return mix(lower, upper, local.y);
}

float smokeNoise(vec2 point) {
    float result = valueNoise(point) * 0.57;
    result += valueNoise(point * 2.03 + vec2(7.1, 3.7)) * 0.29;
    result += valueNoise(point * 4.11 + vec2(-2.4, 9.2)) * 0.14;
    return result;
}

float luminanceOf(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

vec2 safeCoordinate(vec2 coordinate) {
    return clamp(coordinate, vec2(0.003), vec2(0.997));
}

vec3 spectralColor(float light) {
    float lifted = pow(smoothstep(0.005, 0.94, light), 0.88);
    vec3 color = vec3(lifted * 0.61, lifted * 0.78,
            lifted * 0.88);
    float bloom = smoothstep(0.50, 0.94, lifted);
    color += vec3(0.12, 0.16, 0.18) * bloom;
    color += vec3(0.018, 0.034, 0.039) * (1.0 - lifted);
    return color;
}

void main() {
    vec2 centered = textureCoordinate * 2.0 - 1.0;
    centered.x *= aspectRatio;
    float radius = length(centered);
    vec2 direction = radius > 0.0001 ? centered / radius : vec2(0.0);
    vec2 screenDirection = vec2(direction.x
            / max(0.001, aspectRatio), direction.y);

    float pulse = 0.5 + 0.5 * sin(time * 0.82);
    // Preserve a calm, readable focus window around the crosshair.  The
    // unstable realm progressively takes over toward the sides and corners.
    float peripheral = smoothstep(0.24, 1.28, radius);
    float cornerStrength = smoothstep(0.76, 1.82, radius);
    float screenStrength = clamp(peripheral * 0.72
            + cornerStrength * 0.48, 0.0, 1.0);
    vec2 noisePosition = centered * 1.62
            + vec2(time * 0.105, -time * 0.21);
    float smokeA = smokeNoise(noisePosition);
    float smokeB = smokeNoise(noisePosition * 1.29
            + vec2(4.6, -3.2) + vec2(-time * 0.075, time * 0.15));

    vec2 fogSpace = vec2(centered.x * 0.54, centered.y * 0.82);
    vec2 fogDomain = vec2(
            smokeNoise(fogSpace * 1.17
                    + vec2(time * 0.035, -time * 0.095)),
            smokeNoise(fogSpace * 1.11
                    + vec2(5.2 - time * 0.052, 1.7 + time * 0.061)))
            - vec2(0.5);
    float veilLarge = smokeNoise(fogSpace * 1.38
            + fogDomain * 1.15 + vec2(-time * 0.045, time * 0.074));
    float veilDetail = smokeNoise(fogSpace * 3.25
            - fogDomain * 0.54 + vec2(time * 0.082, -time * 0.12));
    float broadVeil = smoothstep(0.31, 0.75,
            veilLarge * 0.73 + veilDetail * 0.27);
    float veilBreakup = smoothstep(0.27, 0.72,
            smokeNoise(vec2(centered.x * 0.37 - time * 0.038,
                    centered.y * 2.15 + time * 0.067)));
    float veil = clamp(broadVeil * (0.54 + veilBreakup * 0.46),
            0.0, 1.0);
    // Low-frequency ridges turn the noise field into long smoke sheets rather
    // than a texture laid uniformly over the frame.
    float veilRidge = smoothstep(0.62, 0.93,
            1.0 - abs(veilLarge * 2.0 - 1.0));
    float filamentField = smokeNoise(vec2(
            centered.x * 0.38 + fogDomain.x * 0.92
                    - time * 0.041,
            centered.y * 2.58 + fogDomain.y * 1.18
                    + time * 0.078));
    float veilFilaments = smoothstep(0.49, 0.73, filamentField)
            * smoothstep(0.18, 0.82, veilBreakup);
    float wispyVeil = clamp(veilRidge * 0.68
            + veilFilaments * broadVeil * 0.57, 0.0, 1.0);

    vec2 tangent = vec2(-screenDirection.y, screenDirection.x);
    vec2 flow = vec2((smokeA - 0.5) * 1.45,
            0.52 + (smokeB - 0.5) * 0.82);
    flow += tangent * sin(radius * 8.3 - time * 0.76
            + smokeB * 5.2) * 0.39;
    float flowLength = length(flow);
    flow = flowLength > 0.0001 ? flow / flowLength : vec2(0.0, 1.0);

    float ripple = sin(radius * 20.0 - time * 1.72
            + smokeA * 6.4);
    float glassDistortion = (smokeB - 0.5) * 0.0095
            + ripple * 0.0032;
    vec2 warped = textureCoordinate
            + screenDirection * glassDistortion
            * screenStrength * effectIntensity
            + flow * texelSize * (0.5 + smokeA * 3.6)
            * screenStrength * effectIntensity;
    warped = safeCoordinate(warped);

    // Sample far enough apart that individual pixels in block textures are
    // not mistaken for silhouettes.  This edge signal only breaks up veils;
    // it is deliberately not used as a full-screen outline filter.
    vec2 edgePixel = texelSize * 4.25;
    vec3 centerColor = texture2D(scene, warped).rgb;
    vec3 leftColor = texture2D(scene,
            safeCoordinate(warped - vec2(edgePixel.x, 0.0))).rgb;
    vec3 rightColor = texture2D(scene,
            safeCoordinate(warped + vec2(edgePixel.x, 0.0))).rgb;
    vec3 lowerColor = texture2D(scene,
            safeCoordinate(warped - vec2(0.0, edgePixel.y))).rgb;
    vec3 upperColor = texture2D(scene,
            safeCoordinate(warped + vec2(0.0, edgePixel.y))).rgb;

    float horizontalEdge = abs(luminanceOf(rightColor)
            - luminanceOf(leftColor));
    float verticalEdge = abs(luminanceOf(upperColor)
            - luminanceOf(lowerColor));
    float colorEdge = length(rightColor - leftColor)
            + length(upperColor - lowerColor);
    float structuralDifference = horizontalEdge + verticalEdge
            + colorEdge * 0.045;
    float objectEdge = smoothstep(0.085, 0.34,
            structuralDifference) * screenStrength;

    vec3 neighborAverage = (leftColor + rightColor
            + lowerColor + upperColor) * 0.25;
    vec3 crispInterior = mix(centerColor, neighborAverage,
            screenStrength * 0.055);

    vec2 bloomPixel = texelSize * (10.0 + veil * 16.0
            + cornerStrength * 8.0);
    vec3 wideBlur = texture2D(scene,
            safeCoordinate(warped - vec2(bloomPixel.x, 0.0))).rgb;
    wideBlur += texture2D(scene,
            safeCoordinate(warped + vec2(bloomPixel.x, 0.0))).rgb;
    wideBlur += texture2D(scene,
            safeCoordinate(warped - vec2(0.0, bloomPixel.y))).rgb;
    wideBlur += texture2D(scene,
            safeCoordinate(warped + vec2(0.0, bloomPixel.y))).rgb;
    wideBlur *= 0.25;

    vec2 veilDrift = flow * texelSize * (12.0 + veil * 30.0)
            + tangent * texelSize * sin(time * 0.48
                    + smokeA * 6.0) * 11.0;
    veilDrift *= 0.25 + screenStrength * 0.75;
    vec3 dissolvedScene = texture2D(scene,
            safeCoordinate(warped - veilDrift)).rgb;
    float dissolvedDifference = smoothstep(0.016, 0.205,
            length(dissolvedScene - centerColor));

    vec2 smokeStep = flow * texelSize * (5.5 + smokeA * 13.0)
            * (0.35 + screenStrength * 0.65);
    vec3 flowingTrail = texture2D(scene,
            safeCoordinate(warped - smokeStep)).rgb * 0.43;
    flowingTrail += texture2D(scene,
            safeCoordinate(warped - smokeStep * 2.15)).rgb * 0.34;
    flowingTrail += texture2D(scene,
            safeCoordinate(warped + smokeStep * 0.72)).rgb * 0.23;

    vec2 radialStep = screenDirection
            * (0.0025 + screenStrength * 0.0115);
    vec3 radialTrail = texture2D(scene,
            safeCoordinate(warped - radialStep)).rgb * 0.57;
    radialTrail += texture2D(scene,
            safeCoordinate(warped - radialStep * 2.28)).rgb * 0.43;
    vec3 edgeTrail = mix(flowingTrail, radialTrail,
            peripheral * 0.54);

    float ribbonNoise = smokeNoise(vec2(
            centered.x * 0.43 + time * 0.13,
            centered.y * 5.2 - time * 0.095));
    float brokenRibbon = smoothstep(0.48, 0.78, ribbonNoise);
    vec2 ribbonStep = vec2(
            texelSize.x * (12.0 + smokeB * 24.0),
            texelSize.y * sin(time * 0.62
                    + centered.y * 10.0) * 3.2);
    vec3 ribbonTrail = texture2D(scene,
            safeCoordinate(warped - ribbonStep)).rgb * 0.59;
    ribbonTrail += texture2D(scene,
            safeCoordinate(warped - ribbonStep * 2.35)).rgb * 0.41;
    float ribbonDifference = smoothstep(0.022, 0.24,
            length(ribbonTrail - centerColor));
    float ribbonMask = brokenRibbon * ribbonDifference
            * (0.34 + objectEdge * 0.66) * screenStrength;
    edgeTrail = mix(edgeTrail, ribbonTrail,
            ribbonMask * (0.58 + peripheral * 0.24));

    vec2 historyOffset = flow * texelSize * (8.0 + smokeA * 18.0)
            * screenStrength
            + radialStep * (0.42 + screenStrength * 0.44);
    vec3 historyColor = texture2D(previousScene,
            safeCoordinate(warped - historyOffset)).rgb;
    vec2 secondEchoOffset = historyOffset * 2.15
            + tangent * texelSize * (5.0 + smokeB * 10.0)
            * screenStrength;
    vec3 secondEcho = texture2D(previousScene,
            safeCoordinate(warped - secondEchoOffset)).rgb;
    float historyDifference = smoothstep(0.018, 0.23,
            length(historyColor - centerColor));
    float temporalNoise = smoothstep(0.28, 0.70,
            smokeNoise(centered * vec2(0.78, 2.45)
                    + fogDomain * 0.7
                    + vec2(-time * 0.058, time * 0.09)));
    float temporalMask = temporalNoise
            * (objectEdge * 0.18 + historyDifference * 0.49
            + dissolvedDifference * 0.33)
            * (0.42 + veil * 0.58) * screenStrength;
    edgeTrail = mix(edgeTrail, historyColor,
            temporalMask * (0.43 + veil * 0.24));

    float animatedSmoke = smoothstep(0.12, 0.84,
            smokeA * 0.69 + smokeB * 0.31);
    float smokeMask = objectEdge * (0.22 + animatedSmoke * 0.58)
            * screenStrength;
    smokeMask = max(smokeMask, ribbonMask * 0.82);
    smokeMask = max(smokeMask, temporalMask * 0.72);
    smokeMask = max(smokeMask,
            dissolvedDifference * veil * screenStrength
            * (0.48 + temporalNoise * 0.38));
    float shapeVeil = clamp(smokeMask
            * (0.58 + veilBreakup * 0.42)
            + dissolvedDifference * veil * 0.34,
            0.0, 1.0);

    vec2 spectralOffset = (flow * texelSize * 2.4
            + radialStep * 0.24) * effectIntensity;
    vec3 color = crispInterior;
    color.r = mix(color.r, texture2D(scene,
            safeCoordinate(warped + spectralOffset)).r,
            objectEdge * effectIntensity * 0.27);
    color.b = mix(color.b, texture2D(scene,
            safeCoordinate(warped - spectralOffset)).b,
            objectEdge * effectIntensity * 0.32);

    float luminance = luminanceOf(color);
    vec3 spectral = spectralColor(luminance);
    spectral += vec3(0.013, 0.038, 0.054)
            * (0.43 + pulse * 0.57);
    float spectralAmount = (0.84 + screenStrength * 0.09)
            * effectIntensity;
    color = mix(color, spectral, spectralAmount);

    float trailLuminance = luminanceOf(edgeTrail);
    vec3 spectralTrail = spectralColor(trailLuminance);
    spectralTrail += vec3(0.055, 0.125, 0.15)
            * (0.45 + animatedSmoke * 0.55);
    color = mix(color, spectralTrail,
            smokeMask * effectIntensity * 0.46);

    // A broad temporal echo produces the soft double-image seen through the
    // veil.  It is intentionally independent of thin texture edges, and is
    // almost absent in the protected central focus window.
    vec3 ghostSource = historyColor * 0.52
            + secondEcho * 0.25 + dissolvedScene * 0.15
            + wideBlur * 0.08;
    vec3 ghostSpectral = spectralColor(luminanceOf(ghostSource));
    ghostSpectral += vec3(0.075, 0.13, 0.15)
            * (0.34 + veil * 0.66);
    float afterimageMask = clamp(screenStrength
            * (0.08 + temporalNoise * 0.46)
            * (0.24 + historyDifference * 0.59
            + dissolvedDifference * 0.31 + veil * 0.20)
            * (0.14 + wispyVeil * 0.86)
            + ribbonMask * 0.32, 0.0, 1.0);
    color = mix(color, ghostSpectral,
            afterimageMask * (0.31 + cornerStrength * 0.21)
            * effectIntensity);

    vec3 softSource = neighborAverage * 0.34
            + wideBlur * 0.18 + dissolvedScene * 0.30
            + historyColor * 0.18;
    vec3 softSpectral = spectralColor(luminanceOf(softSource));
    color = mix(color, softSpectral,
            (veil * screenStrength * 0.055 + shapeVeil * 0.35)
            * effectIntensity);

    float localHighlight = max(luminanceOf(centerColor),
            max(luminanceOf(wideBlur), trailLuminance));
    float highlightBloom = smoothstep(0.27, 0.83, localHighlight)
            * (0.52 + objectEdge * 0.48);
    color += vec3(0.28, 0.43, 0.49) * highlightBloom
            * (0.08 + veil * screenStrength * 0.19
            + cornerStrength * 0.11) * effectIntensity;

    float shadowLift = 1.0 - smoothstep(0.07, 0.58,
            luminanceOf(color));
    vec3 veilColor = vec3(0.27, 0.43, 0.49);
    float veilCoverage = veil * screenStrength * 0.18
            + shapeVeil * 0.68;
    color += veilColor * veilCoverage
            * (0.16 + shadowLift * 0.27)
            * effectIntensity;

    float peripheralFog = screenStrength
            * (broadVeil * 0.065 + wispyVeil * 0.25
            + veilRidge * veilBreakup * 0.12)
            + cornerStrength * (wispyVeil * 0.31
            + veil * 0.085);
    float fogOpacity = clamp(peripheralFog
            + shapeVeil * 0.40
            + dissolvedDifference * temporalNoise
            * screenStrength * 0.15,
            0.0, 0.70) * effectIntensity;
    vec3 foggedScene = mix(softSpectral,
            vec3(0.31, 0.46, 0.51), 0.38 + wispyVeil * 0.24);
    foggedScene += vec3(0.065, 0.10, 0.115) * highlightBloom;
    color = mix(color, foggedScene, fogOpacity);

    // Bright smoke cores separated by dark teal gaps recreate the large,
    // milky sheets in the visual reference without obscuring the full view.
    float smokeSheet = clamp(screenStrength
            * (wispyVeil * (0.23 + cornerStrength * 0.36)
            + veilRidge * veilBreakup * 0.16), 0.0, 0.72)
            * effectIntensity;
    vec3 luminousVeil = vec3(0.47, 0.63, 0.67)
            + vec3(0.10, 0.14, 0.15) * highlightBloom;
    color = mix(color, luminousVeil,
            smokeSheet * (0.22 + veilFilaments * 0.16));
    float darkGap = screenStrength * (1.0 - wispyVeil)
            * (0.035 + cornerStrength * 0.075)
            * effectIntensity;
    color *= 1.0 - darkGap;

    float edgeMist = clamp(smokeMask * 0.48
            + objectEdge * veil * 0.11
            + dissolvedDifference * veil * screenStrength * 0.31,
            0.0, 1.0);
    float edgeFlicker = 0.76 + 0.24 * sin(time * 1.72
            + smokeB * 8.0 + radius * 11.0);
    color += vec3(0.29, 0.46, 0.52) * edgeMist
            * (0.10 + animatedSmoke * 0.14) * edgeFlicker
            * effectIntensity;

    float dreamPatch = smoothstep(0.55, 0.82,
            smokeNoise(vec2(centered.x * 0.65 + time * 0.027,
                    centered.y * 1.72 - time * 0.054) + fogDomain));
    vec3 fadedDream = spectralColor(luminanceOf(color) * 0.86)
            + veilColor * (0.12 + veil * 0.14);
    color = mix(color, fadedDream,
            dreamPatch * screenStrength
            * (0.055 + cornerStrength * 0.075)
            * effectIntensity);

    float centralHaze = 1.0 - smoothstep(0.0, 0.72, radius);
    color += vec3(0.012, 0.032, 0.042) * centralHaze
            * (0.35 + pulse * 0.40) * effectIntensity;
    float vignette = smoothstep(0.55, 1.34, radius);
    color *= 1.0 - vignette * (0.20 + pulse * 0.03)
            * effectIntensity;

    float grain = hash(gl_FragCoord.xy + vec2(time * 23.0,
            -time * 13.0)) - 0.5;
    color += grain * (0.0035 + screenStrength * 0.0025)
            * effectIntensity;
    color = clamp(color, vec3(0.0), vec3(1.0));
    gl_FragColor = vec4(color, 1.0);
}
