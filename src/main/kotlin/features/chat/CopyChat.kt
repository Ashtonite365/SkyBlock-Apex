package dev.ashtonite365.apex.features.chat

import org.joml.Matrix3x2f
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.TextAlignment
import net.minecraft.client.renderer.state.gui.GuiTextRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import dev.ashtonite365.apex.util.Client.font
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.rebuildComponent

object CopyChat {
	val identifier: String get() = "copy-chat"

	@Config
	object TConfig : ManagedConfig(category = Category.CHAT, name = identifier) {
		val copyChat by toggle("copy-chat", default = { false })
	}

	class HoveredTextLineCollector(
		val testX: Int,
		val testY: Int,
	) : ActiveTextCollector {
		private var current = ActiveTextCollector.Parameters(Matrix3x2f())

		var result: FormattedCharSequence? = null

		override fun defaultParameters(): ActiveTextCollector.Parameters = current

		override fun defaultParameters(parameters: ActiveTextCollector.Parameters) { current = parameters }

		private fun covers(line: FormattedCharSequence, left: Int, top: Int, params: ActiveTextCollector.Parameters): Boolean {
			val state = GuiTextRenderState(font, line, params.pose(), left, top, ARGB.white(params.opacity()), 0, true, true, params.scissor())
			return state.bounds()?.containsPoint(testX, testY) ?: false
		}

		override fun accept(align: TextAlignment, x: Int, y: Int, params: ActiveTextCollector.Parameters, line: FormattedCharSequence) {
			if (covers(line, align.calculateLeft(x, font, line), y, params)) result = line
		}

		override fun acceptScrolling(text: Component, centerX: Int, left: Int, right: Int, top: Int, bottom: Int, params: ActiveTextCollector.Parameters) =
			defaultScrollingHelper(text, centerX, left, right, top, bottom, font.width(text), font.lineHeight, params)
	}

	fun orderedTextToString(orderedText: FormattedCharSequence): String = orderedText.rebuildComponent().string
}
