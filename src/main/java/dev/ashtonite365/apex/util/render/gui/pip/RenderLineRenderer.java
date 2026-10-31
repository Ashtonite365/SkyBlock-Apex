package dev.ashtonite365.apex.util.render.gui.pip;

import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ashtonite365.apex.mixins.accessor.AccessorGameRenderer;
import dev.ashtonite365.apex.util.render.state.gui.RenderLineState;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.phys.Vec3;

public final class RenderLineRenderer extends PictureInPictureRenderer<RenderLineState> {

	public static final float DEFAULT_LINE_WIDTH = 1.0f;

	private static final float MIN_LENGTH = 1.0E-6f;

	private RenderLineRenderer(PictureInPictureRendererRegistry.Context context) {
		super();
	}

	@Override
	public Class<RenderLineState> getRenderStateClass() {
		return RenderLineState.class;
	}

	public static void registerRenderer() {
		PictureInPictureRendererRegistry.register(RenderLineRenderer::new);
	}

	@Override
	public void prepare(RenderLineState state, GuiRenderState gui, FeatureRenderDispatcher features, int scale) {
		GameRenderer renderer = Minecraft.getInstance().gameRenderer;
		GameRenderState current = renderer.gameRenderState();
		int[] size = lineScreenSize(state.bounds().width(), state.bounds().height(), scale);
		updateScreenSize(renderer, current, size[0], size[1]);
		try {
			super.prepare(state, gui, features, scale);
		} finally {
			updateScreenSize(renderer, current, current.windowRenderState.width, current.windowRenderState.height);
		}
	}

	public static int[] lineScreenSize(int boxWidth, int boxHeight, int guiScale) {
		return new int[] {boxWidth, boxHeight};
	}

	private static void updateScreenSize(GameRenderer renderer, GameRenderState current, int width, int height) {
		var options = current.optionsRenderState;
		var level = current.levelRenderState;
		Vec3 camera = level.cameraRenderState != null && level.cameraRenderState.pos != null ? level.cameraRenderState.pos : Vec3.ZERO;
		((AccessorGameRenderer) renderer).getGlobalSettingsUniform_Apex().update(
			width, height, options.glintStrength, level.gameTime, level.worldPartialTicks,
			options.menuBackgroundBlurriness, camera, options.textureFiltering == TextureFilteringMethod.RGSS);
	}

	@Override
	protected void renderToTexture(RenderLineState state, PoseStack stack, SubmitNodeCollector out) {
		float halfW = state.width() / 2f;
		float halfH = state.height() / 2f;
		boolean downward = state.direction() == RenderLineState.LineDirection.TOP_LEFT_TO_BOTTOM_RIGHT;
		float startY = downward ? -halfH : halfH;
		int colour = state.colour();
		float width = state.lineWidth();
		out.submitCustomGeometry(stack, RenderTypes.LINES,
			(pose, sink) -> emitLine(sink, pose, -halfW, startY, halfW, -startY, colour, width));
	}

	public static void emitLine(VertexConsumer sink, PoseStack.Pose pose, float fromX, float fromY, float toX, float toY, int colour, float lineWidth) {
		Vector3f n = lineNormal(fromX, fromY, toX, toY);
		float width = sanitizeLineWidth(lineWidth);
		sink.addVertex(pose, fromX, fromY, 0f).setColor(colour).setNormal(pose, n.x, n.y, n.z).setLineWidth(width);
		sink.addVertex(pose, toX, toY, 0f).setColor(colour).setNormal(pose, n.x, n.y, n.z).setLineWidth(width);
	}

	public static Vector3f lineNormal(float fromX, float fromY, float toX, float toY) {
		float dx = toX - fromX, dy = toY - fromY;
		Vector3f direction = new Vector3f(dx, dy, 0f);
		float length = direction.length();
		if (Float.isNaN(length) || length < MIN_LENGTH) return direction.set(1f, 0f, 0f);
		return direction.normalize();
	}

	public static float sanitizeLineWidth(float requested) {
		return Float.isFinite(requested) && requested > 0f ? requested : DEFAULT_LINE_WIDTH;
	}

	@Override
	protected float getTranslateY(int textureHeight, int guiScale) {
		return textureHeight / 2f;
	}

	@Override
	protected String getTextureLabel() {
		return "Line Renderer";
	}
}
