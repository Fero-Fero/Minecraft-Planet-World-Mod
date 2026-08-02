#version 150

// Planet World curvature vertex helper — intended for Iris/Oculus shader packs
// or custom core-shader overrides. Drops Y by distance²/(2R).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraPos;
uniform float Circumference;
uniform float CurvatureIntensity;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    vec4 world = ModelViewMat * vec4(Position, 1.0);
    float dx = Position.x - CameraPos.x;
    float dz = Position.z - CameraPos.z;
    // Prefer shortest wrap delta when circumference is known
    float halfC = Circumference * 0.5;
    if (dx > halfC) dx -= Circumference;
    if (dx < -halfC) dx += Circumference;
    float dist2 = dx * dx + dz * dz;
    float radius = Circumference / (6.28318530718);
    float drop = (dist2 / (2.0 * max(radius, 1.0))) * CurvatureIntensity;
    world.y -= drop;
    gl_Position = ProjMat * world;
    texCoord0 = UV0;
    vertexColor = Color;
}
