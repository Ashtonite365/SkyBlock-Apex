package dev.ashtonite365.apex.util.render.gui.pip;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import kotlin.ranges.ClosedFloatingPointRange;
import kotlin.ranges.RangesKt;
import dev.ashtonite365.apex.util.collections.RangeUtil;
import dev.ashtonite365.apex.util.math.Projections;
import dev.ashtonite365.apex.util.render.CustomRenderTypes;
import dev.ashtonite365.apex.util.render.LerpUtilsKt;
import dev.ashtonite365.apex.util.render.state.gui.RenderCircleProgressState;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec2;

public final class RenderCircleProgressRenderer extends PictureInPictureRenderer<RenderCircleProgressState> {

	private static final float SECTION = (float) (Math.PI / 4);

	private static final float TOP = (float) (-Math.PI / 2);
	private static final float FULL_TURN = (float) (Math.PI * 2);

	private RenderCircleProgressRenderer(PictureInPictureRendererRegistry.Context context) {
		super();
	}

	@Override
	public Class<RenderCircleProgressState> getRenderStateClass() {
		return RenderCircleProgressState.class;
	}

	public static void registerRenderer() {
		PictureInPictureRendererRegistry.register(RenderCircleProgressRenderer::new);
	}

	@Override
	protected void renderToTexture(RenderCircleProgressState state, PoseStack stack, SubmitNodeCollector out) {
		List<Float> cuts = new ArrayList<>();
		for (Float cut : RangeUtil.nonNegligibleSubSectionsAlignedWith(state.angleRadians(), SECTION)) cuts.add(cut);
		if (!drawsSlices(cuts)) return;
		float scale = state.scale() == 0f ? 1f : state.scale();
		float halfW = (state.x1() - state.x0()) / (2f * scale);
		float halfH = (state.y1() - state.y0()) / (2f * scale);
		stack.translate(0f, -halfH, 0f);
		stack.scale(halfW, halfH, 1f);
		out.submitCustomGeometry(stack, state.type(), (pose, sink) -> {
			for (int i = 0; i + 1 < cuts.size(); i++) {
				Vec2 start = Projections.Two.INSTANCE.projectAngleOntoUnitBox(cuts.get(i));
				Vec2 end = Projections.Two.INSTANCE.projectAngleOntoUnitBox(cuts.get(i + 1));
				vertex(sink, pose, state, end);
				vertex(sink, pose, state, start);
				vertex(sink, pose, state, Vec2.ZERO);
			}
		});
	}

	private static void vertex(VertexConsumer sink, PoseStack.Pose pose, RenderCircleProgressState state, Vec2 point) {
		float u = LerpUtilsKt.interpolate(state.u1(), state.u2(), (point.x + 1f) / 2f);
		float v = LerpUtilsKt.interpolate(state.v1(), state.v2(), (point.y + 1f) / 2f);
		sink.addVertex(pose, point.x, point.y, 0f)
			.setUv(u, v)
			.setColor(state.colour())
			.setLineWidth(state.innerCutoutRadius());
	}

	public static boolean drawsSlices(List<Float> cuts) {
		return cuts.size() >= 2;
	}

	@Override
	protected String getTextureLabel() {
		return "Circle";
	}

	public static void extractCircularSlice(GuiGraphicsExtractor gui, RenderType renderType, float u1, float u2, float v1, float v2,
	                                        ClosedFloatingPointRange<Float> angleRadians, int colour, float innerCutoutRadius) {
		ScreenRectangle area = new ScreenRectangle(-1, -1, 2, 2).transformAxisAligned(gui.pose());
		float scale = area.width() / 2f;
		gui.guiRenderState.addPicturesInPictureState(new RenderCircleProgressState(
			renderType, area.left(), area.top(), area.right(), area.bottom(),
			u1, v1, u2, v2, angleRadians, colour, innerCutoutRadius, scale, area, null));
	}

	public static void extractCircle(GuiGraphicsExtractor gui, Identifier texture, float progress, float u1, float u2, float v1, float v2, int colour) {
		ClosedFloatingPointRange<Float> angles = RangesKt.rangeTo(TOP, TOP + progress * FULL_TURN);
		extractCircularSlice(gui, CustomRenderTypes.GUI_TEXTURED_NO_DEPTH_TRIANGLES_CIRCLE.apply(texture), u1, u2, v1, v2, angles, colour, 0f);
	}
}
