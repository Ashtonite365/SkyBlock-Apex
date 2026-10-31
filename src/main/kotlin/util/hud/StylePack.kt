package dev.ashtonite365.apex.util.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.data.AtlasIds
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.StringRepresentable

interface StyleOption : StringRepresentable {

	val texture: String?
	val sprite: Identifier? get() = styleSprite(texture)
}

private fun styleSprite(path: String?): Identifier? = path?.let { Identifier.fromNamespaceAndPath("apex", "styles/$it") }

enum class BarStyle(
	private val id: String,
	override val texture: String?,
	val fillTexture: String?,

	val barHeight: Int,
	val fillInset: Int,

	val tintFill: Boolean = true,

	val codeDrawn: Boolean = false,
) : StyleOption {
	STANDARD("standard", null, null, 5, 0),
	CLASSIC("classic", null, null, 11, 1, codeDrawn = true),
	ROUNDED("rounded", "bar/rounded_background", "bar/rounded_fill", 6, 0),
	APEX("apex", "bar/apex_background", "bar/apex_fill", 7, 1),
	ACCENT("accent", null, null, 6, 0, codeDrawn = true);

	val fillSprite: Identifier? get() = styleSprite(fillTexture)

	override fun getSerializedName() = id
}

enum class BarStyleChoice(private val id: String, val style: BarStyle?) : StringRepresentable {
	GLOBAL("global", null),
	CLASSIC("classic", BarStyle.CLASSIC),
	STANDARD("standard", BarStyle.STANDARD),
	ROUNDED("rounded", BarStyle.ROUNDED),
	APEX("apex", BarStyle.APEX),
	ACCENT("accent", BarStyle.ACCENT);

	val resolved: BarStyle get() = style ?: HudStyle.TConfig.barStyle

	override fun getSerializedName() = id
}

enum class PanelStyle(private val id: String, override val texture: String?) : StyleOption {
	STANDARD("standard", null),
	FLAT_MINIMAL("flat_minimal", "panel/flat_minimal"),
	ROUNDED_GLOW("rounded_glow", "panel/rounded_glow"),
	CLASSIC_TOOLTIP("classic_tooltip", "panel/classic_tooltip");

	override fun getSerializedName() = id
}

object StylePack {
	private const val OPAQUE = 0xFF000000.toInt()

	fun isAvailable(sprite: Identifier): Boolean {
		val gui = Minecraft.getInstance().atlasManager.getAtlasOrThrow(AtlasIds.GUI)
		return gui.getSprite(sprite).contents().name() == sprite
	}

	private fun solid(colour: Int) = if (colour ushr 24 == 0) colour or OPAQUE else colour

	fun drawAccentRounded(ctx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, filled: Int, colour: Int) {

		fun strip(left: Int, right: Int, argb: Int) {
			if (right <= left) return
			if (right - left < 3 || height < 3) {
				ctx.fill(left, y, right, y + height, argb)
				return
			}
			ctx.fill(left, y + 1, right, y + height - 1, argb)
			ctx.fill(left + 1, y, right - 1, y + 1, argb)
			ctx.fill(left + 1, y + height - 1, right - 1, y + height, argb)
		}
		strip(x, x + width, 0xC0202020.toInt())
		strip(x, x + filled.coerceIn(0, width), solid(colour))
	}

	fun drawClassic(ctx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, progress: Float, colour: Int) {
		ctx.fill(x, y, x + width, y + height, OPAQUE)
		if (width <= 2 || height <= 2) return
		val left = x + 1
		val top = y + 1
		val right = x + width - 1
		val bottom = y + height - 1
		ctx.fill(left, top, right, bottom, 0xFF3A3A3A.toInt())
		val end = left + ((right - left) * progress.coerceIn(0f, 1f)).toInt()
		if (end > left) bevel(ctx, left, top, end, bottom, solid(colour), raised = true)
	}

	private fun bevel(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, colour: Int, raised: Boolean) {
		ctx.fill(x1, y1, x2, y2, colour)
		if (y2 - y1 < 3) return
		val bright = tone(colour, 1.3f)
		val dim = tone(colour, 0.7f)
		ctx.fill(x1, y1, x2, y1 + 1, if (raised) bright else dim)
		ctx.fill(x1, y2 - 1, x2, y2, if (raised) dim else bright)
	}

	private fun tone(argb: Int, factor: Float): Int {
		var out = argb and OPAQUE
		for (shift in intArrayOf(16, 8, 0)) {
			val c = ((argb shr shift and 0xFF) * factor).toInt().coerceIn(0, 255)
			out = out or (c shl shift)
		}
		return out
	}

	fun drawPanel(ctx: GuiGraphicsExtractor, style: PanelStyle, x1: Int, y1: Int, x2: Int, y2: Int, fallback: () -> Unit) {
		val id = style.sprite
		if (id == null || x2 <= x1 || y2 <= y1 || !isAvailable(id)) return fallback()
		ctx.blitSprite(RenderPipelines.GUI_TEXTURED, id, x1, y1, x2 - x1, y2 - y1)
	}

	fun drawBar(ctx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, progress: Float, colour: Int,
	            style: BarStyle = HudStyle.TConfig.barStyle) {
		val p = progress.coerceIn(0f, 1f)
		when (style) {
			BarStyle.CLASSIC -> return drawClassic(ctx, x, y, width, height, p, colour)
			BarStyle.ACCENT -> return drawAccentRounded(ctx, x, y, width, height, (width * p).toInt(), colour)
			else -> {}
		}
		val back = style.sprite
		val front = style.fillSprite
		if (back != null && front != null && isAvailable(back) && isAvailable(front)) {
			ctx.blitSprite(RenderPipelines.GUI_TEXTURED, back, x, y, width, height)
			val inset = style.fillInset
			val span = ((width - inset * 2) * p).toInt()
			if (span > 0 && height > inset * 2)
				ctx.blitSprite(RenderPipelines.GUI_TEXTURED, front, x + inset, y + inset, span, height - inset * 2,
				               if (style.tintFill) solid(colour) else -1)
			return
		}

		ctx.fill(x, y, x + width, y + height, 0x90000000.toInt())
		val span = (width * p).toInt()
		if (span > 0) ctx.fill(x, y, x + span, y + height, solid(colour))
	}
}
