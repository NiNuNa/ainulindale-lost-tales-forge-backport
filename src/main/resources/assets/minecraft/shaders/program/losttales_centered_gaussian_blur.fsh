#version 120

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec2 BlurDir;
uniform float Radius;

void main() {
    vec4 center = texture2D(DiffuseSampler, texCoord);
    float radius = clamp(Radius, 0.0, 12.0);
    float sigma = max(0.5, radius * 0.5);
    vec4 blurred = center;
    float totalWeight = 1.0;

    // Every tap is paired with its exact opposite. Unlike an accumulated
    // one-sided blur this cannot introduce a directional screen offset.
    for (float offset = 1.0; offset <= 12.0; offset += 1.0) {
        float coverage = clamp(radius - offset + 1.0, 0.0, 1.0);
        if (coverage > 0.0) {
            float weight = exp(-(offset * offset)
                    / (2.0 * sigma * sigma)) * coverage;
            vec2 delta = oneTexel * offset * BlurDir;
            blurred += (texture2D(DiffuseSampler, texCoord + delta)
                    + texture2D(DiffuseSampler, texCoord - delta))
                    * weight;
            totalWeight += 2.0 * weight;
        }
    }

    gl_FragColor = vec4(blurred.rgb / totalWeight, center.a);
}
