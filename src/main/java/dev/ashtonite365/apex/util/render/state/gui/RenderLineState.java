package dev.ashtonite365.apex.util.render.state.gui;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

public record RenderLineState(int x0, int y0, int x1, int y1, float scale, ScreenRectangle bounds,
                              float lineWidth, int width, int height, int colour, LineDirection direction)
	implements PictureInPictureRenderState {

	public enum LineDirection { TOP_LEFT_TO_BOTTOM_RIGHT, BOTTOM_LEFT_TO_TOP_RIGHT }

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return null;
	}
}
