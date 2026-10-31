/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: what the screen and text hooks ask the active engine, in one place.
 */
package dev.ashtonite365.apex.texengine

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText

object ScreenHooks {
	private val screens get() = ApexTextureEngines.active.screens

	@JvmStatic
	fun layout(): ActiveLayout? = screens.active

	@JvmStatic
	fun background(): LayoutBackground? = screens.active?.background

	@JvmStatic
	fun signLine(index: Int): LayoutMover? = screens.active?.signLine(index)

	@JvmStatic
	fun nameField(): LayoutMover? = screens.active?.nameField

	@JvmStatic
	fun containerTitle(): TitleMover = screens.let { it.active?.containerTitle ?: it.noLayoutTitle }

	@JvmStatic
	fun playerTitle(): TitleMover = screens.let { it.active?.playerTitle ?: it.noLayoutTitle }

	@JvmStatic
	fun repairCostTitle(): TitleMover = screens.let { it.active?.repairCostTitle ?: it.noLayoutTitle }

	@JvmStatic
	fun text(text: Component): Component = ApexTextureEngines.active.texts.rewrite(text)

	@JvmStatic
	fun text(text: FormattedText): FormattedText = if (text is Component) text(text) else text

	@JvmStatic
	fun lines(lines: List<Component>): List<Component> {
		val styling = ApexTextureEngines.active.texts
		var out: Array<Component>? = null
		for (i in lines.indices) {
			val line = lines[i]
			val changed = styling.rewrite(line)
			if (changed !== line) {
				val copy = out ?: lines.toTypedArray().also { out = it }
				copy[i] = changed
			}
		}
		return out?.asList() ?: lines
	}
}
