// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.plus.PlusRepoData
import dev.ashtonite365.apex.util.bareText

object GardenVisitorExtras {
	private fun inGarden() = SkyBlockSession.skyblockIsland == SkyBlockIsland.GARDEN

	private fun rarityColour(rarity: String?): String = when (rarity) {
		"common" -> "§f"; "uncommon" -> "§a"; "rare" -> "§9"; "epic" -> "§5"; "legendary" -> "§6"
		"mythic" -> "§d"; "special", "very_special" -> "§c"
		else -> "§f"
	}

	fun visitorRarities(): Map<String, String> = try {
		val visitors = (PlusRepoData.loadRepoJson("constants/Garden.json") as JsonObject)["visitors"] as JsonObject
		val out = LinkedHashMap<String, String>()
		for ((name, entry) in visitors) out[name] = ((entry as JsonObject)["rarity"] as JsonPrimitive).content.lowercase()
		out
	} catch (e: Throwable) {
		emptyMap()
	}

	fun colouredName(name: String, rarities: Map<String, String> = visitorRarities()): String = rarityColour(rarities[name]) + name

	private val OFFER_ACCEPTED = Regex("OFFER ACCEPTED with (?<name>.+) \\((?<rarity>[A-Z ]+)\\)")
	private val REWARD_ENTRY = Regex("^ {4}(.+)$")
	private val AMOUNT_FIRST = Regex("^\\+?(?<amount>[\\d,]+(?:\\.\\d)?k?)x? (?<reward>.+?)$")
	private val AMOUNT_LAST = Regex("^(?<reward>.+?) x(?<amount>\\d+)$")

	fun compactReward(raw: String): String {
		val text = raw.trim()
		val first = AMOUNT_FIRST.matchEntire(text)
		if (first != null) {
			val amount = first.groups["amount"]!!.value
			val what = first.groups["reward"]!!.value
			val styled = when (what) {
				"Copper" -> "§c" to "Copper"
				"Farming XP", "Farming Experience" -> "§3" to "Farming XP"
				"Garden Experience" -> "§2" to "Garden XP"
				"Bits" -> "§b" to "Bits"
				else -> "§f" to what
			}
			return "${styled.first}$amount ${styled.second}"
		}
		val last = AMOUNT_LAST.matchEntire(text) ?: return "§f$text"
		return "§f${last.groups["amount"]!!.value} ${last.groups["reward"]!!.value}"
	}

	fun compactMessage(name: String, rewards: List<String>): String {
		val shownName = if (TConfig.visitorColourNames) colouredName(name) else "§f$name"
		return "§6§lOFFER ACCEPTED §7w/§r$shownName§6§l!§r " + rewards.joinToString("§7, ")
	}

	private class Collecting(val visitor: String) {
		val original = mutableListOf<String>()
		val compact = mutableListOf<String>()
		var touched = 0
	}

	private var collecting: Collecting? = null
	private var clientTick = 0

	private fun swallow(event: AllowChatEvent, into: Collecting, line: String, reward: String? = null) {
		into.original += line
		if (reward != null) into.compact += reward
		into.touched = clientTick
		event.cancel()
	}

	@Subscribe
	fun onAllow(event: AllowChatEvent) {
		if (!TConfig.visitorCompactChat || !inGarden()) return
		val line = event.bareText
		val start = OFFER_ACCEPTED.matchEntire(line.trim())
		if (start != null) {
			sendSummary()
			Collecting(start.groups["name"]!!.value).also { collecting = it; swallow(event, it, line) }
			return
		}
		val current = collecting ?: return
		if (line.trim() == "REWARDS") return swallow(event, current, line)
		val entry = REWARD_ENTRY.matchEntire(line) ?: return
		swallow(event, current, line, compactReward(entry.groupValues[1]))
	}

	private fun sendSummary() {
		val done = collecting ?: return
		collecting = null
		val hover = HoverEvent.ShowText(Component.literal(done.original.joinToString("\n")))
		Client.showChat(Component.literal(compactMessage(done.visitor, done.compact)).withStyle { it.withHoverEvent(hover) })
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		clientTick = event.tickCount
		val current = collecting ?: return
		if (clientTick - current.touched >= 4) sendSummary()
	}

	@Serializable
	data class Stats(
		var accepted: Int = 0, var denied: Int = 0, var copper: Long = 0, var farmingXp: Long = 0, var gardenXp: Long = 0,
		var bits: Long = 0, var mithrilPowder: Long = 0, var gemstonePowder: Long = 0,
		val rarities: MutableMap<String, Int> = mutableMapOf(), val rewards: MutableMap<String, Int> = mutableMapOf(),
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Stats>(serializer(), "garden-visitor-stats", ::Stats)

	private enum class Counted(val suffix: String) {
		COPPER("Copper"), GARDEN_XP("Garden Experience"), FARMING_XP("Farming XP"), BITS("Bits"),
		MITHRIL("Mithril Powder"), GEMSTONE("Gemstone Powder");

		val pattern = Regex("\\+(.+) $suffix")

		fun add(s: Stats, n: Long) = when (this) {
			COPPER -> s.copper += n
			GARDEN_XP -> s.gardenXp += n
			FARMING_XP -> s.farmingXp += n
			BITS -> s.bits += n
			MITHRIL -> s.mithrilPowder += n
			GEMSTONE -> s.gemstonePowder += n
		}
	}

	fun parseAmount(s: String): Long? {
		val digits = s.replace(",", "").trim()
		if (!digits.endsWith("k")) return digits.toDoubleOrNull()?.toLong()
		val thousands = digits.substring(0, digits.length - 1).toDoubleOrNull() ?: return null
		return (thousands * 1000).toLong()
	}

	fun record(s: Stats, line: String): Boolean {
		val text = line.trim()
		OFFER_ACCEPTED.matchEntire(text)?.let { offer ->
			s.accepted++
			val rarity = offer.groups["rarity"]!!.value
			s.rarities[rarity] = (s.rarities[rarity] ?: 0) + 1
			return true
		}
		for (kind in Counted.entries) {
			val amount = kind.pattern.matchEntire(text)?.let { parseAmount(it.groupValues[1]) } ?: continue
			kind.add(s, amount)
			return true
		}
		val rare = GardenVisitors.rareRewardsIn(listOf(text)).firstOrNull() ?: return false
		s.rewards[rare] = (s.rewards[rare] ?: 0) + 1
		return true
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.visitorDropStats || !inGarden()) return
		val stats = DConfig.data ?: return
		if (record(stats, event.bareText)) DConfig.markDirty()
	}

	@Subscribe
	fun onClick(event: SlotClickEvent) {
		if (!TConfig.visitorDropStats || !inGarden()) return
		if (event.stack.hoverName.bareText.trim() != "Refuse Offer") return
		val stats = DConfig.data ?: return
		stats.denied++
		DConfig.markDirty()
	}

	private fun commas(n: Long) = ApexFormatters.withThousands(n, 0)

	private val RARITY_ROW = listOf("UNCOMMON" to "§a", "RARE" to "§9", "LEGENDARY" to "§6", "MYTHIC" to "§d", "SPECIAL" to "§c")

	fun statLines(s: Stats): List<String> {
		val out = mutableListOf(
			"§e§lVisitor Statistics",
			"§eTotal: ${commas((s.accepted + s.denied).toLong())} §7(§2${s.accepted} accepted§7, §c${s.denied} denied§7)",
			RARITY_ROW.joinToString("§f-") { (rarity, colour) -> colour + (s.rarities[rarity] ?: 0) },
			"§c${commas(s.copper)} Copper",
			"§3${commas(s.farmingXp)} §7Farming EXP",
			"§2${commas(s.gardenXp)} §7Garden EXP",
		)
		val optional = listOf(
			s.bits to "§b%s Bits", s.mithrilPowder to "§2%s Mithril Powder", s.gemstonePowder to "§d%s Gemstone Powder",
		)
		for ((n, template) in optional) if (n > 0) out += template.replace("%s", commas(n))
		s.rewards.keys.sorted().forEach { out += "§b${s.rewards[it]} §9$it" }
		return out
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.visitorDropStats || !inGarden()) return
		val stats = DConfig.data ?: return
		event.drawHudLines(TConfig.visitorStatsPosition, statLines(stats).map { Component.literal(it) })
	}

	enum class Status(val label: String) { NEW("§eNew"), WAITING("§6Waiting"), READY("§aReady") }

	fun status(wanted: Map<String, Int>?, have: Map<String, Int>): Status {
		if (wanted == null) return Status.NEW
		val missing = wanted.entries.any { (item, n) -> (have[item] ?: 0) < n }
		return if (missing) Status.WAITING else Status.READY
	}

	private fun carried(): Map<String, Int> {
		val counts = HashMap<String, Int>()
		val inventory = Client.player?.inventory ?: return counts
		repeat(inventory.containerSize) { i ->
			val stack = inventory.getItem(i)
			if (stack.isEmpty) return@repeat
			val name = stack.hoverName.bareText.trim()
			counts[name] = (counts[name] ?: 0) + stack.count
		}
		return counts
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.visitorHighlight || !inGarden()) return
		val world = Client.level ?: return
		val known = visitorRarities().keys
		if (known.isEmpty()) return
		val offers = GardenHelpers.DConfig.data?.wanted.orEmpty()
		val inventory = carried()
		val me = Client.player ?: return
		WorldDraw.drawInWorld(event) {
			for (npc in world.entitiesForRendering()) {
				if (!npc.hasCustomName() || npc.distanceToSqr(me) > 4096.0) continue
				val name = npc.customName?.bareText?.trim() ?: continue
				if (name !in known) continue
				text(Vec3(npc.x, npc.y + npc.bbHeight + 0.6, npc.z), Component.literal(status(offers[name], inventory).label))
			}
		}
	}
}
