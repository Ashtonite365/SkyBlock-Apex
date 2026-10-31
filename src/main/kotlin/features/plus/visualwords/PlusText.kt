// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.visualwords

import java.util.Optional
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

object PlusText {

	fun mapSegments(line: Component, edit: (String, Style) -> Pair<String, Style>): Component {
		val rebuilt: MutableComponent = Component.empty()
		var touched = false
		val visitor = FormattedText.StyledContentConsumer<Unit> { style, run ->
			val (text, newStyle) = edit(run, style)
			touched = touched || text != run || newStyle != style
			rebuilt.append(Component.literal(text).withStyle(newStyle))
			Optional.empty()
		}
		line.visit(visitor, Style.EMPTY)
		return if (touched) rebuilt else line
	}

	fun replaceFirst(line: Component, old: String, new: String): Component {
		var replaced = false
		return mapSegments(line) { run, style ->
			if (replaced || !run.contains(old)) run to style
			else { replaced = true; run.replaceFirst(old, new) to style }
		}
	}
}
