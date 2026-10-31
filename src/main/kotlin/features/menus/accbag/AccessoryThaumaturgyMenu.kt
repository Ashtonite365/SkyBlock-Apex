// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.accbag

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubSection
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.HubTile
import dev.ashtonite365.apex.features.menus.h6.HubView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.util.data.Config

data class AccessoryPower(
	val slot: Int,
	val name: String,

	val tier: String?,

	val stats: List<String>,

	val bonus: List<String>,

	val description: String?,
	val selected: Boolean,

	val requirement: String?,
	val lore: List<String>,
)

object AccessoryThaumaturgy {
	const val TAG = "[ApexAccBag]"
	const val TITLE_TEXT = "Accessory Bag Thaumaturgy"
	val TITLE = Regex("^Accessory Bag Thaumaturgy$")
	private val TIER = Regex("^(?:[A-Za-z ]+ )?Power$")

	private val SELECTED = Regex("^(?:Power is selected!?|Currently selected!?|Selected!?)$", RegexOption.IGNORE_CASE)
	private val SELECT_HINT = Regex("^Click to select\\b.*", RegexOption.IGNORE_CASE)
	private val YOU_HAVE = Regex("^You have:\\s*([\\d,]+)\\s+Accessory Power$", RegexOption.IGNORE_CASE)
	private val BLOCK_HEADS = listOf("Stats:", "Unique Power Bonus:")

	fun isSelectedLine(l: String) = SELECTED.matches(MenuText.clean(l).trim())

	fun power(s: SlotText): AccessoryPower? {
		val c = s.cleanLore.map { it.trim() }
		if (s.cleanName.isEmpty() || c.isEmpty()) return null
		val selected = c.any(::isSelectedLine)
		val selectable = c.any { SELECT_HINT.matches(it) }
		val tier = c.first().takeIf { TIER.matches(it) }
		val stats = HubText.block(s.lore, "Stats:")
		val bonus = HubText.block(s.lore, "Unique Power Bonus:")
		val requirement = HubText.requirement(s.lore)

		if (tier == null || !(selected || selectable || stats.isNotEmpty() || requirement != null)) return null
		val inBlocks = mutableSetOf<Int>()
		for (head in BLOCK_HEADS) {
			val i = c.indexOfFirst { it.equals(head, true) }
			if (i >= 0) { inBlocks += i; var j = i + 1; while (j < c.size && c[j].isNotEmpty()) inBlocks += j++ }
		}
		val rest = c.withIndex().filter { (i, l) ->
			i != 0 && i !in inBlocks && l.isNotEmpty() && !isSelectedLine(l) && !SELECT_HINT.matches(l) && !YOU_HAVE.matches(l) && l != requirement
		}.map { it.value }
		return AccessoryPower(s.slot, s.cleanName, tier, stats, bonus, rest.joinToString(" ").ifEmpty { null }, selected, requirement, c)
	}

	fun accessoryPower(slots: List<SlotText>): String? =
		slots.firstNotNullOfOrNull { s -> s.cleanLore.firstNotNullOfOrNull { YOU_HAVE.matchEntire(it.trim())?.groupValues?.get(1) } }
			?: slots.firstNotNullOfOrNull { s -> s.cleanLore.firstNotNullOfOrNull { Regex("^Total:\\s*([\\d,]+) Accessory Power$").matchEntire(it.trim())?.groupValues?.get(1) } }

	fun info(p: AccessoryPower): List<String> = buildList {
		if (p.selected) add("§a§l✔ SELECTED")
		p.tier?.let { add("§8$it") }
		p.requirement?.let { add("§c$it") }
		p.stats.forEach { add("§a$it") }
		if (p.bonus.isNotEmpty()) { add("§6Unique Power Bonus:"); p.bonus.forEach { add("§e$it") } }
		p.description?.let { add("§7$it") }
	}

	private fun clickable(s: SlotText) = s.cleanLore.any { it.trim().startsWith("Click", true) }

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): HubModel? {
		val t = MenuText.clean(title).trim()
		if (!TITLE.matches(t)) return null
		val chest = slots.filter { it.slot in 0 until chestSize && it.cleanName.isNotEmpty() }
		val powers = chest.mapNotNull(::power)
		if (powers.isEmpty()) return null
		val powerSlots = powers.map { it.slot }.toSet()
		val rest = chest.filter { it.slot !in powerSlots }

		val nav = HubText.navOf(rest)
		val navSlots = nav.map { it.second }.toSet()
		val infoTiles = rest.filter { it.slot !in navSlots }.map { s ->
			HubTile(s.slot, s.cleanName, HubText.infoLines(s.lore), tooltip = HubText.clean(s.lore), clickable = clickable(s))
		}

		val sections = powers.groupBy { it.tier ?: "Other" }.map { (tier, l) ->
			HubSection("$tier (${l.size})", l.map { p ->
				HubTile(p.slot, (if (p.selected) "§a" else if (p.requirement != null) "§7" else "§f") + p.name, info(p),
					delayed = true, tooltip = p.lore, highlight = p.selected, locked = p.requirement != null)
			})
		} + listOfNotNull(infoTiles.takeIf { it.isNotEmpty() }?.let { HubSection("Accessory Bag", it) })
		val sel = powers.firstOrNull { it.selected }
		val tuning = chest.firstOrNull { it.cleanName.equals("Stats Tuning", true) }?.let { HubText.kv(it.lore)["tuning points"] }
		val chips = listOfNotNull(
			accessoryPower(chest)?.let { "Accessory Power" to it },
			"Selected Power" to (sel?.name ?: "None shown"),
			tuning?.let { "Tuning Points" to it },
			"Powers" to powers.size.toString(),
		)
		return HubModel(t, chips, sections, nav)
	}
}

@Config
object AccessoryThaumaturgySettings : MenuSettings("accessory-thaumaturgy") {
	val reset by button("reset") { resetToDefaults() }
}

object AccessoryThaumaturgyMenu : CustomMenu {
	override val id = "accessory-thaumaturgy"
	override val displayName = "Accessory Bag Thaumaturgy"
	override val title = AccessoryThaumaturgy.TITLE
	override val settings get() = AccessoryThaumaturgySettings
	override val defaultSize = 360 to 330
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) AccessoryThaumaturgyView(screen) else null
}

class OneClickGate(private val windowMs: Long = ApexClickDelay.MAX_MS + 50L) {
	private var until = 0L
	fun tryAcquire(now: Long = System.currentTimeMillis()): Boolean { if (now < until) return false; until = now + windowMs; return true }
}

class AccessoryThaumaturgyView(screen: AbstractContainerScreen<*>) : HubView(screen, { AccessoryThaumaturgySettings }) {
	override val logTag get() = AccessoryThaumaturgy.TAG
	private var warnedParse = false
	override fun parse(title: String, slots: List<SlotText>): HubModel? = try {
		AccessoryThaumaturgy.parse(title, slots, screen.menu.slots.size - 36)
	} catch (e: Exception) {
		if (!warnedParse) { warnedParse = true; dev.ashtonite365.apex.Apex.logger.warn("${AccessoryThaumaturgy.TAG} could not read '$title': ${e.javaClass.simpleName}: ${e.message}; leaving Hypixel's menu showing") }
		null
	}
	override fun showsInventory(m: HubModel) = true
	override fun wrapsTileText(m: HubModel) = true
	override fun wrapsAllInfo(m: HubModel) = true
	override fun liveOnly(m: HubModel) = true
	private val gate = OneClickGate()
	override fun allowPress(m: HubModel, t: HubTile) = !t.delayed || gate.tryAcquire()
}
