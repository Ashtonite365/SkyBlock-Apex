package dev.ashtonite365.apex.util.render;

import java.util.function.Function;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public class CustomRenderTypes {
	private static RenderType untextured(String name, RenderPipeline pipeline) {
		return RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup());
	}

	private static Function<Identifier, RenderType> perTexture(String name, RenderPipeline pipeline) {
		return Util.memoize(texture -> RenderType.create(name,
			RenderSetup.builder(pipeline).withTexture("Sampler0", texture).createRenderSetup()));
	}

	public static final Function<Identifier, RenderType> GUI_TEXTURED_NO_DEPTH_TRIANGLES =
		perTexture("apex_gui_textured_overlay_tris", CustomRenderPipelines.GUI_TEXTURED_NO_DEPTH_TRIANGLES);
	public static final RenderType COLORED_QUADS = untextured("apex_quads", CustomRenderPipelines.COLORED_OMNIPRESENT_QUADS);

	public static final RenderType LINES_NO_DEPTH = RenderType.create("apex_lines_no_depth",
		RenderSetup.builder(CustomRenderPipelines.OMNIPRESENT_LINES)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.createRenderSetup());
	public static final Function<Identifier, RenderType> GUI_TEXTURED_NO_DEPTH_TRIANGLES_CIRCLE =
		perTexture("apex_gui_textured_overlay_tris_circle", CustomRenderPipelines.GUI_TEXTURED_NO_DEPTH_TRIANGLES_CIRCLE);
	public static final RenderType TRANSLUCENT_CIRCLE_GUI =
		untextured("apex_translucent_circle_gui", CustomRenderPipelines.CIRCLE_FILTER_TRANSLUCENT_GUI_TRIANGLES);
}
