#version 330
#extension GL_ARB_separate_shader_objects : require

// SPDX-License-Identifier: GPL-3.0-or-later
// SkyBlock Apex: Apex Far Terrain vertex shader. Vertices are region-local (x, z in 0..128, y = world y);
// ModelOffset (or RegionOffset on the shared-buffer path) = region origin - camera (computed in double precision on
// the CPU), so no precision loss far away.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef REGION_INSTANCED
// shared-buffer path: the region's offset from the camera per instance (one draw call for all regions; the transform's
// ModelOffset is zero then). Added in the same order as before, so the result is bit for bit the old one.
layout(location = 4) in vec3 RegionOffset;
#endif

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

void main() {
#ifdef REGION_INSTANCED
    vec3 pos = (Position + RegionOffset) + ModelOffset;
#else
    vec3 pos = Position + ModelOffset;
#endif
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    // tiny depth bias away from the camera (reverse-Z: smaller depth is farther): on the one frame where the game's
    // own section and far terrain could both draw the same chunk, the game's terrain wins the depth test, no z-fight
    gl_Position.z *= 1.0 - 1.0 / 65536.0;

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
