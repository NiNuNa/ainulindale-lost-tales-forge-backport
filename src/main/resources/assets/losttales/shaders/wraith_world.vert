#version 120

// Full-screen Wraith-world post-process vertex stage.

varying vec2 textureCoordinate;

void main() {
    gl_Position = ftransform();
    textureCoordinate = gl_MultiTexCoord0.xy;
}
