// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.math.roundToInt
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object CombatStatsVitality {
	const val SLOT = 14
	const val ITEM_NAME = "Combat Stats"

	@Serializable data class Data(var vitality: Double? = null)

	@Config
	object DConfig : DataHolder<Data>(serializer(), "stat-bars-vitality", ::Data)

	private val FORMAT = dev.ashtonite365.apex.util.SharedRegex.SECTION
	private val LINE = Regex("^[^\\p{L}\\p{N}]*Vitality\\s+(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?\\s*$")

	fun parseLine(raw: String): Double? {
		val m = LINE.matchEntire(raw.replace(FORMAT, "")) ?: return null
		return (m.groupValues[1].replace(",", "") + m.groupValues[2]).toDoubleOrNull()
	}

	fun parseLore(lore: List<String>): Double? = lore.firstNotNullOfOrNull(::parseLine)

	private val PROFILE_TITLES = listOf("SkyBlock Menu", "Your SkyBlock Profile")
	private val PROFILE_ITEMS = listOf("Your SkyBlock Profile", "Stats")

	private val PROFILE_LINE = Regex("(?:Total +)?Vitality *:? *[^\\d\\s]? *(\\d[\\d,]*)(?: */ *(\\d[\\d,]*))?", RegexOption.IGNORE_CASE)

	fun parseProfileMax(lines: List<String>): Double? = lines.firstNotNullOfOrNull { raw ->
		val m = PROFILE_LINE.find(raw.replace(FORMAT, "")) ?: return@firstNotNullOfOrNull null
		(m.groupValues[2].ifEmpty { m.groupValues[1] }).replace(",", "").toDoubleOrNull()
	}

	fun isCombatStats(slot: Int, name: String) = slot == SLOT && name.replace(FORMAT, "").trim() == ITEM_NAME

	internal var store: () -> Data? = { runCatching { DConfig.data }.getOrNull() }
	internal var save: () -> Unit = { runCatching { DConfig.markDirty() } }

	fun cached(): Double? = store()?.vitality

	fun record(v: Double): Boolean {
		val d = store() ?: return false
		if (d.vitality == v) return false
		d.vitality = v; save(); return true
	}

	fun maxFallback(): Int? = cached()?.roundToInt()

	fun changeLog(oldCur: Int?, oldMax: Int?, cur: Int, max: Int?): String? =
		if (oldCur == cur && oldMax == max) null
		else "[apex stat-bars] vitality changed ${oldCur ?: "?"}->$cur ($cur/${max ?: "?"})"

	@Subscribe
	fun onChest(event: ChestInventoryUpdateEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		val title = screen.title.bareText.trim()
		if (PROFILE_TITLES.any { title.equals(it, ignoreCase = true) }) {
			for (slot in screen.menu.slots) {
				if (slot.container === Client.player?.inventory || slot.item.isEmpty) continue
				val name = slot.item.hoverName.bareText.trim()
				if (PROFILE_ITEMS.none { name.endsWith(it, ignoreCase = true) }) continue
				parseProfileMax(slot.item.view().loreAccordingToNbt.map { it.bareText })?.let { record(it); return }
			}
		}
		val stack = screen.menu.slots.getOrNull(SLOT)?.takeIf { it.container !== Client.player?.inventory }?.item ?: return
		if (stack.isEmpty || !isCombatStats(SLOT, stack.hoverName.bareText)) return
		parseLore(stack.view().loreAccordingToNbt.map { it.bareText })?.let(::record)
	}
}
