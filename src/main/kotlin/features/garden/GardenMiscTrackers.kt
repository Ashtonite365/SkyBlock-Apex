// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.plus.matchMatcher
import net.minecraft.network.chat.Component

object GardenMiscTrackers {
	private val group = PlusPatterns.group("garden.misc")
	private val levelMenu by group.pattern("level.menu", "Garden Level (?<level>\\w+)")
	private val xpGain by group.pattern("level.xp", "\\s*\\+(?<exp>[\\d,.]+) Garden Experience")
	private val capsule by group.pattern("hoe.overflow", "OVERFLOW! Your (?<tool>.+) has just dropped a Tool Exp Capsule!")
	private val feverStart by group.pattern("fever.start", "WOAH! You caught a case of the CROP FEVER for \\d+ seconds!")
	private val feverEnd by group.pattern("fever.end", "GONE! Your CROP FEVER has been cured!")
	private val feverDrop by group.pattern("fever.drop", "(?<rarity>[\\w ]+)! You dropped (?<amount>\\d+)x (?<crop>[\\w ]+)!")
	private val rareCrop by group.pattern("rare.crop", "(?:VERY )?RARE CROP! (?<name>.+?)(?: \\(\\+[\\d.]+\\))?(?: \\(automatically donated\\))?")

	private fun repoPrice(id: String) = RepoConstants.price(ItemId(id))

	var gardenLevel: Int? = null
	var sessionXp = 0.0
	val capsules = mutableMapOf<String, Int>()
	var inFever = false
	var feverCount = 0
	val feverTracker = ProfitTracker("Crop Fever", priceOf = { repoPrice(it) })
	val rareTracker = ProfitTracker("Rare Crops", priceOf = { repoPrice(it) })

	fun handle(msg: String, lookup: (String) -> String? = { ItemNameLookup.guessItemByName(it, false)?.skyblockId }): Boolean {
		if (xpGain.matchMatcher(msg) { sessionXp += group("exp").replace(",", "").toDouble() } != null) return true
		if (capsule.matchMatcher(msg) { capsules[group("tool")] = (capsules[group("tool")] ?: 0) + 1 } != null) return true
		if (feverStart.matchMatcher(msg) { inFever = true; feverCount += 1 } != null) return true
		if (feverEnd.matchMatcher(msg) { inFever = false } != null) return true
		if (inFever) {
			val dropped = feverDrop.matchMatcher(msg) {
				val id = lookup(group("crop"))
				if (id != null) feverTracker.addItem(id, group("amount").toLong())
			}
			if (dropped != null) return true
		}
		return rareCrop.matchMatcher(msg) {
			val id = lookup(group("name"))
			if (id != null) rareTracker.addItem(id)
		} != null
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (SkyBlockSession.skyblockIsland == SkyBlockIsland.GARDEN) handle(event.bareText)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		val title = Client.screenTitle ?: return
		val level = levelMenu.matchMatcher(title) { GardenProgress.roman(group("level")) } ?: return
		gardenLevel = level
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val lines = ArrayList<Component>()
		if (TConfig.gardenLevelDisplay) {
			val level = gardenLevel?.toString() ?: "?"
			lines += Component.literal("§aGarden Level $level §7(+${ApexFormatters.compactNumber(sessionXp)} XP this session)")
		}
		if (TConfig.hoeLevelDisplay) for ((tool, count) in capsules) lines += Component.literal("§6$tool§7: §f$count §7exp capsules")
		if (TConfig.cropFeverTracker && feverCount > 0) {
			val active = if (inFever) " §a(active)" else ""
			lines += Component.literal("§cCrop Fever§7: §f$feverCount$active")
			lines += feverTracker.lines()
		}
		if (TConfig.rareCropTracker && rareTracker.current.items.isNotEmpty()) lines += rareTracker.lines()
		if (lines.isNotEmpty()) event.drawPlusLines(TConfig.gardenMiscPosition, lines)
	}
}
