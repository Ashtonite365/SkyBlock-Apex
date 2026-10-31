// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.hunting

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.bareText
import org.joml.Vector2i

object PlusShardChecklist {
	@Config
	object TConfig : ManagedConfig("shard-checklist", Category.FORAGING) {
		val enabled by toggle("enabled") { false }
		val onlyCurrentBiome by toggle("only-current-biome") { true }
		val hideCaught by toggle("hide-caught") { false }
		val hud by position("hud", 140, 120) { Vector2i(5, 220) }
	}

	enum class Biome(val display: String, val colour: String) { CAVERN("Cavern", "§6"), FOREST("Forest", "§2"), HAUNTED("Haunted", "§5"), ICY("Icy", "§9") }

	val shards: Map<Biome, List<String>> = linkedMapOf(
		Biome.CAVERN to listOf("Cavernfish", "Flitter", "Shyworm", "Driftling", "Chuckwalla", "Rockmite", "Scrappy", "Snoozle", "Gemzie"),
		Biome.FOREST to listOf("Foxtrot", "Honeybug", "Hideonfloor", "Treefrog", "Woodchucker", "Fluffling", "Bluebird", "Parakeet", "Macaw"),
		Biome.HAUNTED to listOf("Solsnatcher", "Bloodbat", "Litterbug", "Areita", "Duplico", "Gazer", "Hideonwall", "Gimmiegold", "Hideyho", "Doomspiral"),
		Biome.ICY to listOf("Strongarm", "Polaris", "Billygoat", "Tepid", "Nozzlenose", "Mantis Shrimp", "Shuddersquid", "Troodon", "Wumpa"),
	)

	private val byLowerName: Map<String, String> = HashMap<String, String>().also { m ->
		for (list in shards.values) for (name in list) m[name.lowercase()] = name
	}

	private val NON_LETTERS = Regex("[^A-Za-z ]")

	fun biomeOf(lines: List<String>): Biome? {
		for (line in lines) {
			val letters = NON_LETTERS.replace(line, "").trim()
			for (biome in Biome.entries) if (letters.equals(biome.display + " Biome", ignoreCase = true)) return biome
		}
		return null
	}

	private val CATCH = Regex("(?:You caught|You captured|LOOT SHARE You received|You received) (?:an? |x?\\d+x? )?(?<name>[A-Za-z' ]+?) Shards?!?.*")

	fun shardOf(msg: String): String? {
		val name = CATCH.matchEntire(msg)?.groups?.get("name")?.value ?: return null
		return byLowerName[name.trim().lowercase()]
	}

	val caught = mutableSetOf<String>()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { caught.clear() }

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled || !PlusHunting.isHuntingIsland(SkyBlockSession.skyblockIsland)) return
		val shard = shardOf(event.bareText.trim()) ?: return
		caught.add(shard)
	}

	fun lines(current: Biome?, onlyCurrent: Boolean, hideCaught: Boolean): List<String> {
		val out = ArrayList<String>()
		for (biome in Biome.entries) {
			val names = shards.getValue(biome)
			val have = names.count(caught::contains)
			val tally = if (have == names.size) "§a" else "§c"
			out.add("${biome.colour}${biome.display} §7- $tally$have/${names.size}")
			if (onlyCurrent && biome != current) continue
			for (name in names) {
				val got = name in caught
				if (hideCaught && got) continue
				out.add("§7- (${if (got) "§a✔" else "§c✖"}§7) §f$name")
			}
		}
		return out
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || !PlusHunting.isHuntingIsland(SkyBlockSession.skyblockIsland)) return
		val biome = biomeOf(ScoreboardUtil.simplifiedScoreboardLines)
		val body = lines(biome, TConfig.onlyCurrentBiome, TConfig.hideCaught)
		event.drawPlusLines(TConfig.hud) {
			add("§e§lSafari Shard Checklist")
			for (l in body) add(l)
		}
	}
}
