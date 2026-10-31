// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.minion

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.hypixelIdText

object MinionPanes {

	fun isPane(itemId: String): Boolean {
		val id = itemId.lowercase()
		return id == "minecraft:glass_pane" || (id.startsWith("minecraft:") && id.endsWith("_stained_glass_pane"))
	}

	private val PANE_NAME = Regex("^(?:[A-Za-z]+(?: [A-Za-z]+)? )?(?:Stained )?Glass Pane$")
	private val RARITY = Regex("^(?:VERY SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|SUPREME|ULTIMATE)\\b")
	private fun strip(s: String) = s.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()

	fun isRealPane(repoItemId: String?, name: String, lore: List<String>): Boolean {
		if (!repoItemId.isNullOrBlank()) return true
		return PANE_NAME.matches(strip(name)) && lore.any { RARITY.containsMatchIn(strip(it)) }
	}

	fun hide(itemId: String, repoItemId: String?, name: String, lore: List<String>) =
		isPane(itemId) && !isRealPane(repoItemId, name, lore)

	fun hide(stack: ItemStack): Boolean {
		if (stack.isEmpty) return false
		val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
		if (!isPane(id)) return false
		val a = stack.view()

		return hide(id, a.hypixelIdText, a.displayNameAccordingToNbt.string, a.loreAccordingToNbt.map { it.string })
	}

	fun layout(slots: List<Int>, hidden: (Int) -> Boolean): List<Triple<Int, Int, Int>> {
		val visible = slots.filter { !hidden(it) }
		val cols = visible.groupingBy { it / 9 }.eachCount().values.maxOrNull() ?: return emptyList()
		return visible.mapIndexed { i, s -> Triple(s, i % cols, i / cols) }
	}

	fun rows(cells: List<Triple<Int, Int, Int>>) = (cells.maxOfOrNull { it.third } ?: -1) + 1
}
