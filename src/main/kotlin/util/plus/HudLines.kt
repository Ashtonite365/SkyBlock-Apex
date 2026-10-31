// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.plus

import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.util.render.drawHudLines
import net.minecraft.network.chat.Component

class PlusLines {
	val lines: MutableList<Component> = ArrayList()

	fun add(c: Component) { lines.add(c) }
	fun add(s: String) = add(Component.literal(s))
	fun addAll(c: Iterable<Component>) { for (line in c) lines.add(line) }
	fun addIf(cond: Boolean, s: () -> String) { if (cond) add(s()) }

	fun pair(label: String, value: Any?) = add("§7$label: §f$value")
}

fun buildPlusLines(block: PlusLines.() -> Unit): List<Component> {
	val builder = PlusLines()
	builder.block()
	return builder.lines
}

fun HudRenderEvent.drawPlusLines(position: HudMeta, lines: List<Component>) {
	if (lines.isEmpty()) return
	drawHudLines(position, lines)
}

fun HudRenderEvent.drawPlusLines(position: HudMeta, block: PlusLines.() -> Unit) = drawPlusLines(position, buildPlusLines(block))

fun List<String>.toPlusComponents(): List<Component> = map(Component::literal)
