package dev.ashtonite365.apex.util.render;

import java.util.Optional;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.jspecify.annotations.Nullable;
import dev.ashtonite365.apex.Apex;
import net.minecraft.client.renderer.RenderPipelines;

public final class CustomRenderPipelines {

	private static final DepthStencilState SEEN_ONLY = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1f, -2f);

	public static final RenderPipeline GUI_TEXTURED_NO_DEPTH_TRIANGLES =
		overlayTriangles("gui_textured_overlay_tris", DefaultVertexFormat.POSITION_TEX_COLOR);
	public static final RenderPipeline GUI_TEXTURED_NO_DEPTH_TRIANGLES_CIRCLE =
		overlayTriangles("gui_textured_overlay_tris", CustomVertexFormats.POSITION_TEX_COLOR_RADIUS);
	public static final RenderPipeline COLORED_OMNIPRESENT_QUADS = colouredQuads("colored_omnipresent_quads", null);
	public static final RenderPipeline CIRCLE_FILTER_TRANSLUCENT_GUI_TRIANGLES = circleFilter();
	public static final RenderPipeline OMNIPRESENT_LINES = lines("omnipresent_lines", null);
	public static final RenderPipeline COLORED_DEPTH_QUADS = colouredQuads("colored_depth_quads", SEEN_ONLY);
	public static final RenderPipeline DEPTH_LINES = lines("depth_lines", SEEN_ONLY);

	private CustomRenderPipelines() {
	}

	private static ColorTargetState translucentTarget() {
		return new ColorTargetState(BlendFunction.TRANSLUCENT);
	}

	private static RenderPipeline.Builder guiTriangles(String location, VertexFormat format) {
		return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Apex.identifier(location))
			.withVertexBinding(0, format)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withColorTargetState(translucentTarget());
	}

	private static RenderPipeline overlayTriangles(String location, VertexFormat format) {
		return guiTriangles(location, format)
			.withCull(false)
			.withDepthStencilState(Optional.empty())
			.build();
	}

	private static RenderPipeline.Builder depth(RenderPipeline.Builder builder, @Nullable DepthStencilState state) {
		return state == null ? builder.withDepthStencilState(Optional.empty()) : builder.withDepthStencilState(state);
	}

	private static RenderPipeline colouredQuads(String location, @Nullable DepthStencilState state) {
		String shader = "core/position_color";
		var builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET);
		builder.withLocation(Apex.identifier(location)).withCull(false);
		builder.withVertexShader(shader).withFragmentShader(shader);
		builder.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS);
		builder.withColorTargetState(translucentTarget());
		return depth(builder, state).build();
	}

	private static RenderPipeline lines(String location, @Nullable DepthStencilState state) {
		var builder = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Apex.identifier(location))
			.withColorTargetState(translucentTarget());
		return depth(builder, state).build();
	}

	private static RenderPipeline circleFilter() {
		var shader = Apex.identifier("circle_discard_color");
		return guiTriangles("gui_textured_overlay_tris_circle", CustomVertexFormats.POSITION_TEX_COLOR_RADIUS)
			.withVertexShader(shader)
			.withFragmentShader(shader)
			.withDepthStencilState(Optional.empty())
			.build();
	}
}
