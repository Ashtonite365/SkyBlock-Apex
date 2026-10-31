package dev.ashtonite365.apex.util.hud

import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object HudStyle {
	enum class Theme(val label: String, val background: Int) : StringRepresentable {
		DARK("dark", 0x000000),
		LIGHT("light", 0xFFFFFF),
		NONE("none", 0x000000);

		override fun getSerializedName() = label
	}

	enum class Corners(val label: String) : StringRepresentable {
		SQUARE("square"),
		ROUNDED("rounded");

		override fun getSerializedName() = label
	}

	@Config
	object TConfig : ManagedConfig("hud-style", Category.GENERAL) {
		val theme by choice("theme") { Theme.DARK }
		val backgroundOpacity by integer("background-opacity", 0, 100) { 0 }
		val textColour by colour("text-colour") { ChromaColour.fromStaticRGB(255, 255, 255, 255) }
		val textShadow by toggle("text-shadow") { true }
		val corners by choice("corners") { Corners.ROUNDED }
		val padding by integer("padding", 0, 10) { 2 }
		val scale by integer("scale", 50, 200) { 100 }
		val panelStyle by choice("panel-style") { PanelStyle.STANDARD }
		val timerStyle by choice("timer-style") { PanelStyle.STANDARD }
		val barStyle by choice("bar-style") { BarStyle.STANDARD }
	}

	data class Override(
		val theme: Theme? = null,
		val backgroundOpacity: Int? = null,
		val textColour: Int? = null,
		val textShadow: Boolean? = null,
		val padding: Int? = null,
		val scale: Float? = null,
		val panelStyle: PanelStyle? = null,
	)

	val lineHeight get() = Client.font.lineHeight + 1

	fun backgroundColour(o: Override? = null): Int {
		val chosen = o?.theme ?: TConfig.theme
		if (chosen == Theme.NONE) return 0
		val percent = (o?.backgroundOpacity ?: TConfig.backgroundOpacity).coerceIn(0, 100)
		return (percent * 255 / 100 shl 24) or (chosen.background and 0xFFFFFF)
	}

	fun textColour(o: Override? = null): Int = o?.textColour ?: TConfig.textColour.getEffectiveColourRGB()

	fun drawBackground(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, o: Override? = null) {
		StylePack.drawPanel(ctx, o?.panelStyle ?: TConfig.panelStyle, x1, y1, x2, y2) { fillPlain(ctx, x1, y1, x2, y2, o) }
	}

	private fun fillPlain(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, o: Override?) {
		val argb = backgroundColour(o)
		if (argb ushr 24 == 0 || x2 <= x1 || y2 <= y1) return
		if (TConfig.corners == Corners.SQUARE || x2 - x1 < 3 || y2 - y1 < 3) {
			ctx.fill(x1, y1, x2, y2, argb)
			return
		}
		ctx.fill(x1, y1 + 1, x2, y2 - 1, argb)
		ctx.fill(x1 + 1, y1, x2 - 1, y1 + 1, argb)
		ctx.fill(x1 + 1, y2 - 1, x2 - 1, y2, argb)
	}

	fun drawLines(event: HudRenderEvent, position: HudMeta, lines: List<Component>, o: Override? = null) {
		if (lines.isEmpty() || dev.ashtonite365.apex.gui.config.HudHidden.isHidden(position.hudId, position.label)) return

		val labelKey = (position.label.contents as? net.minecraft.network.chat.contents.TranslatableContents)?.key
		if (dev.ashtonite365.apex.features.timers.TimerBoards.intercept(dev.ashtonite365.apex.gui.config.HudHidden.keyOfLabel(labelKey), lines)) return
		val g = event.context
		val inset = o?.padding ?: TConfig.padding
		val styleScale = o?.scale ?: (TConfig.scale / 100f)
		val textW = lines.maxOf { Client.font.width(it) }
		val boxW = textW + inset * 2
		val boxH = lines.size * lineHeight - 1 + inset * 2
		val pose = g.pose()
		pose.pushMatrix()

		val fit = position.applyTextTransformations(pose, boxW * styleScale, boxH * styleScale)
		pose.scale(styleScale, styleScale)

		val fillW = maxOf(boxW, (fit.localW / styleScale).toInt())
		val fillH = maxOf(boxH, (fit.localH / styleScale).toInt())
		drawBackground(g, 0, 0, fillW, fillH, o)
		val rgb = textColour(o)
		val shadow = o?.textShadow ?: TConfig.textShadow
		lines.forEachIndexed { row, line -> g.text(Client.font, line, inset, inset + row * lineHeight, rgb, shadow) }
		pose.popMatrix()
	}
}
