#version 330
#extension GL_ARB_separate_shader_objects : require

// SPDX-License-Identifier: GPL-3.0-or-later
// SkyBlock Apex: Apex Far Terrain fragment shader. Unlike vanilla terrain it ignores the render-distance fog (that
// would hide everything past the vanilla render distance); it keeps the environmental fog (water, lava, blindness,
// weather) and fades into the fog colour over the last part of the far-terrain distance.
// TextureMat[3].x / [3].y = fade start / end distance in blocks, [3].z = scale for the environmental fog distances
// (1 under water / lava / blindness; larger in open air so the default 1024-block atmospheric fog does not cut far
// terrain short). TextureMat is otherwise unused here. Light: Sampler2 is the game's live lightmap (time of day,
// weather, night vision, gamma), sampled per vertex exactly like the game's terrain.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    float fadeStart = TextureMat[3].x;
    float fadeEnd = TextureMat[3].y;
    float envScale = max(TextureMat[3].z, 1.0);
    float fog = max(linear_fog_value(sphericalVertexDistance, FogEnvironmentalStart * envScale, FogEnvironmentalEnd * envScale),
                    linear_fog_value(cylindricalVertexDistance, fadeStart, fadeEnd));
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fog * FogColor.a), color.a);
}
