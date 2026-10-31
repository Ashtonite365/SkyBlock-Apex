#version 330
// SkyBlock Apex: the vanilla position/uv/colour vertex stage plus one per-vertex float (the LineWidth element),
// handed to the fragment stage as the ring's inner radius. The uniform blocks are spelled out instead of
// moj_import-ed because this program is compiled at startup, before any resource pack is available.

layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };
layout(std140) uniform Projection { mat4 ProjMat; };

in vec3 Position; in vec2 UV0; in vec4 Color; in float LineWidth;
out vec2 texCoord0; out vec4 vertexColor; out float innerCutoutRadius;

void main() { // pass-throughs first, then the vanilla transform
	vertexColor = Color; texCoord0 = UV0; innerCutoutRadius = LineWidth;
	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0); // vanilla transform
}
