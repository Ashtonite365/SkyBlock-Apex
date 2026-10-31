#version 150
// SkyBlock Apex: disc / ring fill. A fragment survives when its distance from the quad centre is within
// [innerCutoutRadius, 0.5] and the vertex colour is not fully transparent.

in vec4 vertexColor; in vec2 texCoord0; in float innerCutoutRadius;
out vec4 fragColor; // the only colour output

void main() { // ring test, then the flat colour
	float fromCentre = distance(texCoord0, vec2(0.5));
	bool offRing = fromCentre > 0.5 || fromCentre < innerCutoutRadius;
	if (vertexColor.a == 0.0 || offRing) discard;
	fragColor = vertexColor;
}
