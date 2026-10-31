/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex accessory bag sidebar: magical power, stat totals, reforge counts, duplicates and missing
 * accessories, remembered per profile from the pages of the bag you open.
 * Display only: a click on the sidebar only changes its tab and is consumed (never reaches the server).
 * Docked beside the bag window (AccessorySidebarLayout); long text wraps, and the wheel scrolls tall content.
 */
package dev.ashtonite365.apex.features.accessories

import com.google.gson.JsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import me.shedaniel.math.Rectangle
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.events.HandledScreenPushREIEvent
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.events.HandledScreenClickEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.repo.RepoItemTypeCache
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.accessors.getProperRectangle
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.reforgeKey
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.stripSectionCodes
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.ItemType
import dev.ashtonite365.apex.util.skyblock.Rarity
import dev.ashtonite365.apex.util.bareText

@OptIn(NeedsComponents::class)
object AccessoryBagHelper {
	val identifier: String
		get() = "accessory-bag-helper"

	@Serializable
	data class Accessory(
		val id: String,
		val rarity: Rarity? = null,
		val reforge: String? = null,
		val stats: Map<String, Double> = mapOf(),
	)

	@Serializable
	data class Data(

		val slots: MutableMap<String, Accessory> = mutableMapOf(),
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), identifier, ::Data)

	@Config
	object TConfig : ManagedConfig(category = Category.INVENTORY, name = identifier) {
		val enabled by toggle("enabled") { true }
		val missingLines by integer("missing-lines", 5, 30) { 12 }
	}

	enum class Tab(val label: String) { SUMMARY("MP"), STATS("Stats"), REFORGES("Reforges"), DUPLICATES("Dupes"), MISSING("Missing") }

	fun interface Upgrades {
		fun of(id: String): List<String>?
	}

	private const val WIDTH = 210
	private const val PAD = 5
	private const val ROW_H = 10
	private const val TAB_ROW_H = 12
	private const val BAG_TITLE = "Accessory Bag"

	fun magicalPower(rarity: Rarity?): Int = when (rarity) {
		Rarity.MYTHIC -> 22
		Rarity.LEGENDARY -> 16
		Rarity.EPIC -> 12
		Rarity.RARE -> 8
		Rarity.UNCOMMON, Rarity.VERY_SPECIAL -> 5
		Rarity.COMMON, Rarity.SPECIAL -> 3
		else -> 0
	}

	private fun Upgrades.supersededBy(id: String, owned: Set<String>): Boolean = of(id)?.any { it in owned } == true

	fun pageOf(title: String): String? {
		if (!title.startsWith(BAG_TITLE)) return null
		val rest = title.substring(BAG_TITLE.length)
		if (rest.isEmpty()) return "1"
		if (!rest.startsWith(" (") || !rest.endsWith(")")) return null
		val inner = rest.substring(2, rest.length - 1)
		val slash = inner.indexOf('/')
		if (slash <= 0 || slash == inner.length - 1) return null
		val (a, b) = inner.substring(0, slash) to inner.substring(slash + 1)
		return if (isDigits(a) && isDigits(b)) a else null
	}

	private fun isDigits(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' }

	private val STAT_LINE = Regex("([A-Za-z ]+): \\+([\\d,.]+)%?.*")

	fun statsFrom(lore: List<String>): Map<String, Double> {
		val out = LinkedHashMap<String, Double>()
		for (line in lore) {
			val m = STAT_LINE.matchEntire(line) ?: continue
			val v = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
			out[m.groupValues[1].trim()] = v
		}
		return out
	}

	fun record(data: Data, key: String, entry: Accessory?): Boolean {
		if (entry == null) return data.slots.remove(key) != null
		if (data.slots[key] == entry) return false
		data.slots[key] = entry
		return true
	}

	private fun owned(data: Data): Set<String> = data.slots.values.mapTo(HashSet()) { it.id }

	fun counted(data: Data, upgrades: Upgrades): List<Accessory> {
		val have = owned(data)
		val firsts = LinkedHashMap<String, Accessory>()
		for (a in data.slots.values) firsts.putIfAbsent(a.id, a)
		return firsts.values.filterNot { upgrades.supersededBy(it.id, have) }
	}

	fun missing(data: Data, all: Collection<ItemId>, ignored: Set<String>, upgrades: Upgrades, price: (ItemId) -> Double?): List<Pair<ItemId, Double?>> {
		val have = owned(data)
		val out = ArrayList<Pair<ItemId, Double?>>()
		for (id in all) {
			if (id.skyblockId in have || id.skyblockId in ignored || upgrades.supersededBy(id.skyblockId, have)) continue
			val p = price(id) ?: continue
			out += id to p
		}
		out.sortBy { it.second }
		return out
	}

	fun rows(
		tab: Tab, data: Data, upgrades: Upgrades, name: (String) -> String,
		missingList: () -> List<Pair<ItemId, Double?>>, missingLimit: Int,
	): List<Pair<String, String>> = when (tab) {
		Tab.SUMMARY -> {
			val c = counted(data, upgrades)
			val head = listOf(
				"§eMagical Power" to "§6" + c.sumOf { magicalPower(it.rarity) },
				"§eAccessories counted" to "§f" + c.size,
				"§eSlots saved" to "§f" + data.slots.size,
			)
			head + Rarity.entries.mapNotNull { r -> c.count { it.rarity == r }.takeIf { it > 0 }?.let { "§7 $r" to "§f$it" } }
		}
		Tab.STATS -> {
			val sum = java.util.TreeMap<String, Double>()
			for (a in counted(data, upgrades)) for ((k, v) in a.stats) sum[k] = (sum[k] ?: 0.0) + v
			sum.map { (k, v) -> "§e$k" to "§a+" + ApexFormatters.withThousands(v, 1) }
		}
		Tab.REFORGES -> {
			val counts = LinkedHashMap<String, Int>()
			for (a in data.slots.values) counts.merge(a.reforge ?: "none", 1, Int::plus)
			counts.entries.sortedByDescending { it.value }.map { (k, n) -> "§e" + k.replaceFirstChar(Char::uppercase) to "§f$n" }
		}
		Tab.DUPLICATES -> {
			val have = owned(data)
			val counts = LinkedHashMap<String, Int>()
			for (a in data.slots.values) counts.merge(a.id, 1, Int::plus)
			counts.filter { it.value > 1 }.map { (id, n) -> "§c" + name(id) to "§fx$n" } +
				counts.keys.filter { upgrades.supersededBy(it, have) }.map { "§6" + name(it) to "§7upgrade owned" }
		}
		Tab.MISSING -> missingList().take(missingLimit).map { (id, p) -> "§f" + name(id.skyblockId) to "§6" + ApexFormatters.compactNumber(p ?: 0.0) }
	}

	fun scrollBar(boxY: Int, boxH: Int, contentH: Int, scroll: Int): Pair<Int, Int>? {
		if (contentH <= boxH) return null
		val size = maxOf(boxH * boxH / contentH, 8)
		return (boxY + (boxH - size) * scroll / maxOf(contentH - boxH, 1)) to size
	}

	fun rowVisible(rowY: Int, boxY: Int, boxH: Int) = rowY + ROW_H >= boxY && rowY <= boxY + boxH

	private val repoUpgrades = Upgrades { id -> upgradeTable()?.getAsJsonArray(id)?.map { it.asString } }
	private fun upgradeTable(): JsonObject? = RepoConstants.get("misc")?.getAsJsonObject("talisman_upgrades")

	private fun accessoryIds(): Set<ItemId> = listOf("ACCESSORY", "DUNGEON ACCESSORY")
		.flatMap { RepoItemTypeCache.byItemType[ItemType.ofName(it)].orEmpty() }
		.mapTo(LinkedHashSet()) { ItemId(it.skyblockItemId) }

	private fun ignoredIds(): Set<String> {
		val misc = RepoConstants.get("misc")
		return runCatching { misc?.getAsJsonObject("ignored_talisman")?.keySet() }.getOrNull()
			?: runCatching { misc?.getAsJsonArray("ignored_talisman")?.map { it.asString }?.toSet() }.getOrNull()
			?: emptySet()
	}

	fun counted(data: Data): List<Accessory> = counted(data, repoUpgrades)
	fun missingAccessories(data: Data) = missing(data, accessoryIds(), ignoredIds(), repoUpgrades, RepoConstants::price)

	private fun displayName(id: String) = RepoManager.getCoreItem(ItemId(id))?.displayName?.stripSectionCodes() ?: id

	private var tab = Tab.SUMMARY
	private var scroll = 0

	private var lastBox: AccessorySidebarLayout.Box? = null
	private var lastTabs: List<Pair<Tab, AccessorySidebarLayout.Box>> = emptyList()
	private var lastContentH = 0
	private var warnedNoRoom = false
	private var warnedFailure = false

	private fun isBag(screen: AbstractContainerScreen<*>) = screen.title.bareText.startsWith(BAG_TITLE)

	private fun scan(screen: AbstractContainerScreen<*>, data: Data) {
		val page = pageOf(screen.title.bareText.trim()) ?: return
		var dirty = false
		for ((index, slot) in screen.menu.slots.withIndex()) {
			if (slot.container === Client.player?.inventory || index !in 0 until 45) continue
			val item = slot.item.view()
			val entry = item.repoItemId?.let { id ->
				Accessory(id.skyblockId, Rarity.fromItem(item), item.reforgeKey()?.id, statsFrom(item.loreAccordingToNbt.map { it.bareText.trim() }))
			}
			if (record(data, "$page:$index", entry)) dirty = true
		}
		if (dirty) DConfig.markDirty()
	}

	private fun anchor(screen: AbstractContainerScreen<*>): AccessorySidebarLayout.Box {
		val r = SidePanel.anchorOf(screen)
		return AccessorySidebarLayout.Box(r.x, r.y, r.width, r.height)
	}

	private fun wrap(rows: List<Pair<String, String>>, innerW: Int): List<Pair<String, String>> = rows.flatMap { (left, right) ->
		val room = maxOf(innerW - Client.font.width(right) - 4, 20)
		BzMenuLayout.wrapText(left, room) { Client.font.width(it) }.mapIndexed { i, part -> part to if (i == 0) right else "" }
	}

	@Subscribe
	fun onClick(event: HandledScreenClickEvent) {
		if (!TConfig.enabled || !isBag(event.screen)) return
		val box = lastBox ?: return
		if (!box.contains(event.mouseX, event.mouseY)) return
		val picked = lastTabs.firstOrNull { it.second.contains(event.mouseX, event.mouseY) }
		if (picked != null) {
			dev.ashtonite365.apex.features.menus.MenuPress.acted()
			tab = picked.first
			scroll = 0
		}
		event.cancel()
	}

	@Subscribe
	fun onExclusion(event: HandledScreenPushREIEvent) {
		if (!TConfig.enabled || !isBag(event.screen)) return
		val box = lastBox ?: return
		event.rectangles.add(Rectangle(box.x, box.y, box.w, box.h))
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
			if (screen !is AbstractContainerScreen<*> || !isBag(screen)) return@AfterInit
			lastBox = null
			scroll = 0
			ScreenMouseEvents.allowMouseScroll(screen).register(ScreenMouseEvents.AllowMouseScroll { _, mx, my, _, amount ->
				val box = lastBox
				if (!TConfig.enabled || box == null || !box.contains(mx, my)) return@AllowMouseScroll true
				scroll = AccessorySidebarLayout.clampScroll(scroll - (amount * ROW_H * 2).toInt(), lastContentH, box.h)
				false
			})
		})
	}

	@Subscribe
	fun onScreenRender(event: ScreenRenderPostEvent) {
		if (!TConfig.enabled) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (!isBag(screen)) return

		try {
			render(event, screen)
		} catch (e: Exception) {
			lastBox = null
			if (!warnedFailure) {
				warnedFailure = true
				dev.ashtonite365.apex.Apex.logger.warn("[Apex] accessory bag: sidebar render failed: ${e.javaClass.simpleName}: ${e.message}", e)
			}
		}
	}

	private fun render(event: ScreenRenderPostEvent, screen: AbstractContainerScreen<*>) {
		val data = DConfig.data
		scan(screen, data)
		val font = Client.font
		val dockTo = anchor(screen)

		val probe = AccessorySidebarLayout.dock(dockTo, screen.width, screen.height, WIDTH, 1)
		if (probe == null) {
			lastBox = null
			if (!warnedNoRoom) {
				warnedNoRoom = true
				dev.ashtonite365.apex.Apex.logger.info("[Apex] accessory bag: no room beside the bag for the sidebar at this GUI scale; not drawn")
			}
			return
		}
		val innerW = probe.w - 2 * PAD
		val tabCells = BzMenuLayout.wrapTabs(Tab.entries.map { font.width(it.label) + 6 }, innerW, 2)
		val header = 3 + ((tabCells.maxOfOrNull { it.row } ?: 0) + 1) * TAB_ROW_H
		val body = wrap(rows(tab, data, repoUpgrades, ::displayName, { missingAccessories(data) }, TConfig.missingLines), innerW)
		val contentH = header + body.size * ROW_H + 3
		val box = AccessorySidebarLayout.dock(dockTo, screen.width, screen.height, WIDTH, contentH) ?: return
		lastContentH = contentH
		scroll = AccessorySidebarLayout.clampScroll(scroll, contentH, box.h)
		val (x, y, w, h) = box
		val ctx = event.drawContext
		SidePanel.drawBox(ctx, x, y, x + w, y + h)
		if (!SidePanel.palette.themed) ctx.outlineBox(x, y, w, h, 0xFF555555.toInt())
		ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1)
		val hits = ArrayList<Pair<Tab, AccessorySidebarLayout.Box>>()
		for ((i, t) in Tab.entries.withIndex()) {
			val cell = tabCells[i]
			val tx = x + PAD + cell.x
			val ty = y + 3 + cell.row * TAB_ROW_H - scroll
			ctx.centeredText(font, (if (t == tab) "§e§n" else "§7") + t.label, tx + cell.width / 2, ty, -1)
			hits += t to AccessorySidebarLayout.Box(tx, ty - 1, cell.width, 11)
		}
		for ((i, row) in body.withIndex()) {
			val ry = y + header + i * ROW_H - scroll
			if (!rowVisible(ry, y, h)) continue
			SidePanel.text(ctx, row.first, x + PAD, ry)
			if (row.second.isNotEmpty()) SidePanel.text(ctx, row.second, x + w - PAD - font.width(row.second), ry)
		}
		ctx.disableScissor()
		scrollBar(y, h, contentH, scroll)?.let { (top, size) -> ctx.fill(x + w - 3, top, x + w - 1, top + size, 0xFF888888.toInt()) }
		lastBox = box
		lastTabs = hits.filter { it.second.y + it.second.h > y && it.second.y < y + h }
	}
}
