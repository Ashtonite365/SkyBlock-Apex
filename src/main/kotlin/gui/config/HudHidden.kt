// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.features.misc.Hud

object HudHidden {

	fun keyOf(hudId: Identifier): String = hudId.path.replace('/', '.')

	const val LABEL_PREFIX = "apex.config."

	fun keyOfLabel(labelKey: String?): String? =
		labelKey?.takeIf { it.startsWith(LABEL_PREFIX) && it.length > LABEL_PREFIX.length }?.removePrefix(LABEL_PREFIX)

	fun keyOf(hudId: Identifier, label: Component): String =
		keyOfLabel((label.contents as? TranslatableContents)?.key) ?: keyOf(hudId)

	private fun hidden(): Set<String> = Hud.TConfig.hiddenElements._actualValue ?: emptySet()

	fun isHidden(hudId: Identifier, label: Component): Boolean {
		val set = hidden()
		return set.isNotEmpty() && keyOf(hudId, label) in set
	}

	fun toggled(set: Set<String>, key: String): Set<String> = if (key in set) set - key else set + key

	fun toggle(hudId: Identifier, label: Component) {
		Hud.TConfig.hiddenElements.value = toggled(hidden(), keyOf(hudId, label))
	}
}
