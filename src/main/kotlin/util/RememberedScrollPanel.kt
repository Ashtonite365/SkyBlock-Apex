// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.component.ScrollPanelComponent

class RememberedScrollPanel(private val key: String, width: Int, height: Int, private val content: GuiComponent) :
	ScrollPanelComponent(width, height, content) {
	private var restored = false

	override fun render(context: GuiImmediateContext) {
		runCatching {
			val max = content.height - height
			if (!restored) { restored = true; field.setInt(this, ScrollMemory.clamp(ScrollMemory.get(key), max)) }
			else field.setInt(this, ScrollMemory.clamp(field.getInt(this), max))
		}
		super.render(context)
		runCatching { ScrollMemory.put(key, field.getInt(this)) }
	}

	companion object {
		private val field by lazy {
			ScrollPanelComponent::class.java.getDeclaredField("scrollOffset").apply { isAccessible = true }
		}
	}
}
