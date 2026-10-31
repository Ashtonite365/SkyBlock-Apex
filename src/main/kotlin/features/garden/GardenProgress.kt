// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import com.google.gson.JsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.skyblock.TabListAPI
import dev.ashtonite365.apex.util.bareText

object GardenProgress {
	private val opts get() = GardenTools.TConfig

	enum class Crop(val display: String, val toolPrefixes: List<String>, val baseDrops: Double, val aliases: List<String> = emptyList()) {
		WHEAT("Wheat", listOf("THEORETICAL_HOE_WHEAT"), 1.0),
		CARROT("Carrot", listOf("THEORETICAL_HOE_CARROT"), 3.0),
		POTATO("Potato", listOf("THEORETICAL_HOE_POTATO"), 3.0),

		MELON("Melon", listOf("MELON_DICER"), 5.0, listOf("Melon Slice")),
		PUMPKIN("Pumpkin", listOf("PUMPKIN_DICER"), 1.0),
		SUGAR_CANE("Sugar Cane", listOf("THEORETICAL_HOE_CANE"), 2.0),
		COCOA_BEANS("Cocoa Beans", listOf("COCO_CHOPPER"), 3.0),
		CACTUS("Cactus", listOf("CACTUS_KNIFE"), 2.0),
		MUSHROOM("Mushroom", listOf("FUNGI_CUTTER"), 1.0),
		NETHER_WART("Nether Wart", listOf("THEORETICAL_HOE_WARTS"), 2.5);

		private fun answersTo(label: String) =
			display.equals(label, ignoreCase = true) || aliases.any { it.equals(label, ignoreCase = true) }

		companion object {
			fun forTool(toolId: String): Crop? = entries.find { crop -> crop.toolPrefixes.any(toolId::startsWith) }

			fun byDisplay(name: String): Crop? {
				val label = name.trim()
				return entries.find { it.answersTo(label) }
			}

			fun fromMenuName(name: String): Pair<Crop, Int>? {
				val title = name.trim()
				val cut = title.lastIndexOf(' ')
				val word = if (cut < 0) "" else title.substring(cut + 1)
				val numeral = word.isNotEmpty() && word.all { it in NUMERALS }
				val tier = if (numeral) roman(word) else null
				val label = if (tier == null) title else title.substring(0, cut)
				val crop = byDisplay(label) ?: return null
				return crop to (tier ?: 0)
			}
		}
	}

	fun milestoneTable(garden: JsonObject?, crop: Crop): List<Long> {
		val tiers = garden?.getAsJsonObject("crop_milestones")?.getAsJsonArray(crop.name) ?: return emptyList()
		return tiers.map { it.asLong }
	}

	data class Milestone(val tier: Int, val progress: Long, val needed: Long?)

	fun milestoneFromTotal(table: List<Long>, total: Long, overflow: Boolean = false): Milestone {
		var spent = 0L
		table.forEachIndexed { tier, size ->
			val into = total - spent
			if (into < size) return Milestone(tier, into, size)
			spent += size
		}
		val rest = total - spent
		val size = table.lastOrNull()
		if (overflow && size != null && size > 0) {
			val more = (rest / size).toInt()
			return Milestone(table.size + more, rest - more * size, size)
		}
		return Milestone(table.size, rest, null)
	}

	fun cropsToTier(table: List<Long>, total: Long, goalTier: Int): Long? {
		if (goalTier <= 0 || table.isEmpty()) return null
		var cost = 0L
		for (tier in 0 until goalTier) cost += table.getOrElse(tier) { table.last() }
		val missing = cost - total
		return if (missing > 0) missing else null
	}

	private inline fun <V> cropAssignments(text: String, value: (String) -> V?): Map<Crop, V> {
		val result = LinkedHashMap<Crop, V>()
		for (entry in text.split(',')) {
			val sides = entry.split('=')
			if (sides.size != 2) continue
			val key = sides[0].trim().uppercase().replace(' ', '_')
			val crop = Crop.entries.find { it.name == key } ?: continue
			result[crop] = value(sides[1].trim()) ?: continue
		}
		return result
	}

	fun parseGoals(text: String): Map<Crop, Int> = cropAssignments(text) { v -> v.toIntOrNull()?.takeIf { it > 0 } }

	fun parseThresholds(text: String): Map<Crop, Long> = cropAssignments(text) { v -> GardenHelpers.parseShortNumber(v)?.toLong() }

	@Serializable
	data class Data(val totals: MutableMap<String, Long> = mutableMapOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "garden-milestones", ::Data)

	private const val NUMERALS = "IVXLC"
	private fun numeralValue(c: Char): Int = when (c) {
		'I' -> 1; 'V' -> 5; 'X' -> 10; 'L' -> 50; 'C' -> 100
		else -> -1
	}

	fun roman(s: String): Int? {
		if (s.isEmpty() || !s.all { it in NUMERALS }) return s.toIntOrNull()
		var sum = 0
		var right = 0
		for (c in s.reversed()) {
			val v = numeralValue(c)
			sum += if (v < right) -v else v
			right = v
		}
		return sum
	}

	private val TOTAL_LINE = Regex("^\\s*Total: ([0-9,]+)")
	private val FRACTION = Regex("([0-9,.]+[kKmM]?)/([0-9,.]+[kKmM]?)")

	fun totalFromMenu(table: List<Long>, tier: Int, loreLines: List<String>): Long? {
		val explicit = loreLines.asSequence().mapNotNull { TOTAL_LINE.find(it) }.firstOrNull()
		if (explicit != null) return explicit.groupValues[1].replace(",", "").toLongOrNull()
		val fraction = loreLines.asSequence().mapNotNull { FRACTION.find(it) }.firstOrNull() ?: return null
		val current = GardenHelpers.parseShortNumber(fraction.groupValues[1]) ?: return null
		return table.take(tier).sum() + current.toLong()
	}

	fun counterDelta(before: Pair<Long?, Long?>, after: Pair<Long?, Long?>): Long {
		val mined = grownBy(before.first, after.first)
		val cultivated = grownBy(before.second, after.second)
		return if (cultivated > mined) cultivated else mined
	}

	private fun grownBy(old: Long?, new: Long?): Long = if (old == null || new == null || new <= old) 0L else new - old

	fun bestCrop(totals: Map<String, Long>, tables: (Crop) -> List<Long>): Pair<Crop, Long>? {
		var best: Pair<Crop, Long>? = null
		for (crop in Crop.entries) {
			val total = totals[crop.name] ?: continue
			val m = milestoneFromTotal(tables(crop), total)
			val size = m.needed ?: continue
			val left = size - m.progress
			if (best == null || left < best.second) best = crop to left
		}
		return best
	}

	fun ffNeeded(targetCrops: Long, crop: Crop, blocksPerSecond: Double = 20.0): Double {
		val blocks = 1200.0 * blocksPerSecond
		val multiplier = targetCrops / (blocks * crop.baseDrops)
		return (multiplier - 1) * 100
	}

	fun timeLeft(left: Long, perMinute: Double?): String? {
		if (perMinute == null || perMinute <= 0.0) return null
		val seconds = (left.coerceAtLeast(0) / perMinute * 60).toLong()
		val hours = seconds / 3600
		val minutes = seconds / 60 % 60
		return when {
			hours > 0 -> "${hours}h ${minutes}m"
			minutes > 0 -> "${minutes}m ${seconds % 60}s"
			else -> "${seconds % 60}s"
		}
	}

	private val COMPOSTER_UPGRADES = listOf(
		"Composter Speed" to "speed", "Multi Drop" to "multi_drop", "Fuel Cap" to "fuel_cap",
		"Organic Matter Cap" to "organic_matter_cap", "Cost Reduction" to "cost_reduction",
	)

	fun upgradeCost(garden: JsonObject?, key: String, currentLevel: Int): Pair<Map<String, Int>, Int>? {
		val upgrades = garden?.getAsJsonObject("composter_upgrades") ?: return null
		val next = upgrades.getAsJsonObject(key)?.getAsJsonObject("${currentLevel + 1}") ?: return null
		val items = LinkedHashMap<String, Int>()
		next.getAsJsonObject("items")?.entrySet()?.forEach { (id, n) -> items[id.uppercase()] = n.asInt }
		val copper = next.get("copper")?.asInt ?: 0
		return items to copper
	}

	private val VISITOR_HEADER = Regex("^Visitors: \\((\\d+)\\)")

	fun visitorCount(tabLines: List<String>): Int? {
		for (line in tabLines) {
			val n = VISITOR_HEADER.find(line.trim())?.groupValues?.get(1)?.toIntOrNull()
			if (n != null) return n
		}
		return null
	}

	private var visitorsBefore: Int? = null

	private fun watchVisitors() {
		val alertAt = opts.visitorFullAt
		if (alertAt <= 0) return
		val now = visitorCount(TabListReader.currentSnapshot.body.map { it.bareText }) ?: return
		val before = visitorsBefore
		visitorsBefore = now
		if (before != null && before < alertAt && now >= alertAt) Alerts.show(Component.literal("§a§l$now visitors waiting"))
	}

	private var heldCrop: Crop? = null
	private var counterRead: Pair<Long?, Long?> = null to null

	@Subscribe
	fun onMilestoneMenu(event: ChestInventoryUpdateEvent) {
		if (!opts.cropMilestones || Client.screenTitle != "Crop Milestones") return
		val garden = RepoConstants.get("garden")
		val store = DConfig.data ?: return
		Client.containerScreen?.menu?.slots.orEmpty().forEach { slot ->
			val (crop, tier) = Crop.fromMenuName(slot.item.hoverName.bareText) ?: return@forEach
			val lore = slot.item.view().loreAccordingToNbt.map { it.bareText }
			totalFromMenu(milestoneTable(garden, crop), tier, lore)?.let { store.totals[crop.name] = it }
		}
		DConfig.markDirty()
		GardenCropSpeedMeter.onMilestonesRead(store.totals)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0 || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val tool = Client.player?.mainHandItem ?: return
		val crop = tool.view().repoItemId?.skyblockId?.let(Crop::forTool)
		val attributes = tool.view().customDataTag
		val read = attributes.getLong("mined_crops").orElse(null) to attributes.getLong("farmed_cultivating").orElse(null)
		if (crop != null && crop == heldCrop) {
			val gained = counterDelta(counterRead, read)
			if (gained > 0) addCrops(crop, gained)
		}
		heldCrop = crop
		counterRead = read
		watchVisitors()
	}

	private fun addCrops(crop: Crop, gained: Long) {
		val store = DConfig.data ?: return
		val table = milestoneTable(RepoConstants.get("garden"), crop)
		val oldTier = store.totals[crop.name]?.let { milestoneFromTotal(table, it, true).tier }
		val total = (store.totals[crop.name] ?: 0L) + gained
		store.totals[crop.name] = total
		DConfig.markDirty()
		val newTier = milestoneFromTotal(table, total, true).tier
		if (oldTier != null && newTier > oldTier) announceTier(crop, oldTier, newTier, table.size)
	}

	private fun announceTier(crop: Crop, from: Int, to: Int, topTier: Int) {
		val goal = parseGoals(opts.milestoneCustomGoals)[crop]
		val text = when {
			goal != null && from < goal && to >= goal ->
				"§e§l[Apex] You have reached your milestone goal of §b§l$goal §e§lin the §b§l${crop.display} §e§lcrop!"
			opts.milestoneOverflowChat && to > topTier -> "§b[Apex] ${crop.display} overflow milestone §8$from➜§3$to"
			else -> return
		}
		Client.showChat(Component.literal(text))
	}

	private fun short(n: Long) = ApexFormatters.compactNumber(n.toDouble())

	private fun milestoneLines(crop: Crop, total: Long, garden: JsonObject?): List<String> {
		val table = milestoneTable(garden, crop)
		val m = milestoneFromTotal(table, total, opts.milestoneOverflow)
		val rate = GardenTools.cropsPerMinute()
		val out = mutableListOf<String>()
		val size = m.needed
		out += if (size == null) "§a${crop.display} §f${m.tier} §6MAX"
		else "§a${crop.display} §f${m.tier} §7(${ApexFormatters.withThousands(m.progress)}/${ApexFormatters.withThousands(size)}, " +
			"%.1f%%)".format(m.progress * 100.0 / size)
		if (opts.milestoneTimeLeft && size != null) timeLeft(size - m.progress, rate)?.let { out += "§eNext milestone in: §f$it" }
		val goal = parseGoals(opts.milestoneCustomGoals)[crop] ?: return out
		val missing = cropsToTier(table, total, goal)
		out += if (missing == null) "§eGoal $goal: §aReached!"
		else "§eGoal $goal: §f${short(missing)} left" + (timeLeft(missing, rate)?.let { " §7($it)" } ?: "")
		return out
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val garden = RepoConstants.get("garden")
		val totals = DConfig.data?.totals.orEmpty()
		val crop = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId?.let(Crop::forTool)
		val text = mutableListOf<String>()
		val held = crop?.let { c -> totals[c.name]?.let { c to it } }
		if (opts.cropMilestones && held != null) text += milestoneLines(held.first, held.second, garden)
		if (opts.bestCrop) bestCrop(totals) { milestoneTable(garden, it) }?.let { (c, left) ->
			text += "§eBest crop: §f${c.display} §7(${short(left)} to next)"
		}
		val target = crop?.let { parseThresholds(opts.contestGoldThresholds)[it] }
		if (crop != null && target != null) {
			text += "§6Gold needs §f${short(target)} §7≈ §f%.0f FF".format(ffNeeded(target, crop).coerceAtLeast(0.0))
			if (opts.contestTimeNeeded) timeLeft(target, GardenTools.cropsPerMinute())?.let { text += "§6Gold at current speed in: §f$it §7(contest: 20m)" }
		}
		val lines = text.mapTo(mutableListOf<Component>()) { Component.literal(it) }
		lines += GardenFortune.hudLines(crop)
		if (lines.isNotEmpty()) event.drawHudLines(opts.milestoneHudPosition, lines)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!opts.composterUpgradeCosts || Client.screenTitle != "Composter Upgrades") return
		val title = event.stack.hoverName.bareText.trim()
		val (label, key) = COMPOSTER_UPGRADES.find { title.startsWith(it.first) } ?: return
		val level = roman(title.removePrefix(label).trim()) ?: 0
		val (items, copper) = upgradeCost(RepoConstants.get("garden"), key, level) ?: return
		event.lines += Component.literal("§eNext level costs:")
		var coins = 0.0
		items.forEach { (id, amount) ->
			val cost = RepoConstants.price(ItemId(id))?.times(amount)
			if (cost != null) coins += cost
			event.lines += Component.literal("§7 ${amount}x ${id.lowercase().replace('_', ' ')} §6${cost?.let(ApexFormatters::compactNumber) ?: "?"}")
		}
		event.lines += Component.literal("§7 §c$copper Copper §7+ §6${ApexFormatters.compactNumber(coins)} coins")
	}

	@Subscribe
	fun onComposterScreen(event: ScreenRenderPostEvent) {
		if (!opts.composterOverlay) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (screen.title.bareText.trim() != "Composter") return
		val widget = TabListAPI.getWidgetLines(TabListAPI.WidgetName.COMPOSTER).map { it.bareText.trim() }.filterNot { it.isEmpty() }
		if (widget.isEmpty()) return
		val frame = screen as AccessorHandledScreen
		val left = frame.x_Apex + frame.backgroundWidth_Apex + 4
		val top = frame.y_Apex
		val width = 8 + widget.maxOf { Client.font.width(it) }
		HudStyle.drawBackground(event.drawContext, left, top, left + width, top + 6 + 10 * widget.size)
		var y = top + 4
		for (line in widget) {
			event.drawContext.text(Client.font, line, left + 4, y, HudStyle.textColour(), HudStyle.TConfig.textShadow)
			y += 10
		}
	}
}
