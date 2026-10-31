package dev.ashtonite365.apex.util.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;

public class CustomVertexFormats {

	public static final VertexFormat POSITION_TEX_COLOR_RADIUS;

	static {
		VertexFormat.Builder format = VertexFormat.builder(0);
		format.addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT);
		format.addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT);
		format.addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM);
		format.addAttribute(DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME, GpuFormat.R32_FLOAT);
		POSITION_TEX_COLOR_RADIUS = format.build();
	}
}
