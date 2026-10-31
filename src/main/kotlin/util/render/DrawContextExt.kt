package dev.ashtonite365.apex.util.render

import me.shedaniel.math.Color
import kotlin.math.abs
import kotlin.math.floor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.util.render.state.gui.RenderLineState
import org.joml.Vector2f

private const val LINE_MARGIN = 3

private const val POSE_EPSILON = 1e-6f

fun GuiGraphicsExtractor.hasIdentityPose(): Boolean {
	val m = pose()
	val noTranslation = abs(m.m20) < POSE_EPSILON && abs(m.m21) < POSE_EPSILON
	val noScaleOrShear = abs(m.m00 - 1f) < POSE_EPSILON && abs(m.m11 - 1f) < POSE_EPSILON &&
		abs(m.m01) < POSE_EPSILON && abs(m.m10) < POSE_EPSILON
	return noTranslation && noScaleOrShear
}

fun GuiGraphicsExtractor.guiSprite(id: Identifier, left: Int, top: Int, w: Int, h: Int) {
	val pipeline = RenderPipelines.GUI_TEXTURED
	blitSprite(pipeline, id, left, top, w, h)
}

fun GuiGraphicsExtractor.textureRegion(
	texture: Identifier, left: Int, top: Int, u: Float, v: Float, w: Int, h: Int, texW: Int, texH: Int,
) {
	blit(RenderPipelines.GUI_TEXTURED, texture, left, top, u, v, w, h, w, h, texW, texH)
}

fun GuiGraphicsExtractor.outlineBox(left: Int, top: Int, w: Int, h: Int, color: Int) {
	val edgesX = intArrayOf(left, left + w)
	val edgesY = intArrayOf(top, top + h)
	for (x in edgesX) verticalLine(x, top, top + h, color)
	for (y in edgesY) horizontalLine(left, left + w, y, color)
}

private fun GuiGraphicsExtractor.toScreen(x: Int, y: Int, isOffset: Boolean = false): Pair<Int, Int> {
	val p = Vector2f(x.toFloat(), y.toFloat())
	if (isOffset) pose().transformDirection(p) else pose().transformPosition(p)
	return floor(p.x).toInt() to floor(p.y).toInt()
}

fun GuiGraphicsExtractor.lineBetween(x1: Int, y1: Int, x2: Int, y2: Int, color: Color, lineWidth: Float = 1f) {
	val upward = y2 < y1
	val (fromX, fromY) = if (upward) x2 to y2 else x1 to y1
	val (toX, toY) = if (upward) x1 to y1 else x2 to y2
	val direction =
		if (toX > fromX) RenderLineState.LineDirection.TOP_LEFT_TO_BOTTOM_RIGHT
		else RenderLineState.LineDirection.BOTTOM_LEFT_TO_TOP_RIGHT

	val (ax, ay) = toScreen(minOf(fromX, toX), fromY)
	val span = toScreen(abs(toX - fromX), toY - fromY, isOffset = true)
	val (bx, by) = (ax + span.first) to (ay + span.second)
	val area = ScreenRectangle(ax - LINE_MARGIN, ay - LINE_MARGIN, bx - ax + 2 * LINE_MARGIN, by - ay + 2 * LINE_MARGIN)
	val state = RenderLineState(
		area.left(), area.top(), area.right(), area.bottom(), 1f, area,
		lineWidth, span.first, span.second, color.color, direction,
	)
	guiRenderState.addPicturesInPictureState(state)
}
