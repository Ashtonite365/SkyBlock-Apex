// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.profileviewer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dev.ashtonite365.apex.util.ApexFormatters

object ProfileViewerPages {
	private fun fmt(n: Number?) = ApexFormatters.withThousands((n ?: 0).toLong())
	fun prettify(key: String) = key.split('_', ' ').filter { it.isNotEmpty() }
		.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }

	private fun noData(what: String) = listOf("§cNo $what data for this profile.", "§7(API disabled, not unlocked yet, or not in the API.)")

	private fun numericLines(obj: JsonObject, prefix: String = "", limit: Int = 30): List<String> =
		obj.entries.flatMap { (k, v) ->
			when (v) {
				is JsonPrimitive -> v.num()?.let { listOf("§7$prefix${prettify(k)}: §f${fmt(it)}") } ?: listOf()
				is JsonObject -> if (prefix.isEmpty()) numericLines(v, "${prettify(k)} › ", limit) else listOf()
				is JsonArray -> listOf("§7$prefix${prettify(k)}: §f${v.size}")
				else -> listOf()
			}
		}.take(limit)

	const val MAX_CROP_UPGRADES = 10

	fun capped(lines: List<String>, max: Int): List<String> =
		if (lines.size <= max) lines else lines.take(max - 1) + "§8 +${lines.size - max + 1} more"

	fun heartOfTheForest(member: JsonObject): List<String> {
		val tree = member.obj("skill_tree") ?: return listOf()
		val xp = tree.path("experience", "foraging").num()
		val nodes = tree.path("nodes", "foraging") as? JsonObject
		if (xp == null && nodes == null) return listOf()
		return buildList {
			add("§2Heart of the Forest §7XP: §f${fmt(xp)}")
			add("§7Tokens spent: §f${fmt(tree.path("tokens_spent", "foraging").num())}")
			nodes?.let { n -> add("§7Perks unlocked: §f${n.entries.count { (k, v) -> !k.startsWith("toggle_") && (v.long() ?: 0) > 0 }}") }
			tree.path("selected_ability", "foraging").str()?.let { add("§7Ability: §f${prettify(it)}") }
		}
	}

	fun bestiary(member: JsonObject): List<String> {
		val bestiary = member.obj("bestiary") ?: return noData("Bestiary")
		val kills = bestiary.obj("kills") ?: return noData("Bestiary")
		val counts = kills.entries.mapNotNull { (k, v) -> v.long()?.let { k to it } }.filter { it.first != "last_killed_mob" }
		if (counts.isEmpty()) return noData("Bestiary")
		val deaths = bestiary.obj("deaths")?.values?.sumOf { it.long() ?: 0 } ?: 0
		return buildList {
			add("§cBestiary milestone: §f${bestiary.path("milestone", "last_claimed_milestone").long() ?: 0}")

			add("§7Total kills: §f${fmt(counts.sumOf { it.second })}")
			add("§7Deaths: §f${fmt(deaths)}")
			add("§7Mob types: §f${counts.size}")
			add("")

			counts.groupBy { it.first.substringBeforeLast('_').takeIf { s -> s.isNotEmpty() } ?: it.first }
				.mapValues { e -> e.value.sumOf { it.second } }
				.entries.sortedByDescending { it.value }.take(24)
				.forEach { add("§7${prettify(it.key)}: §f${fmt(it.value)}") }
		}
	}

	fun garden(garden: JsonObject?, loading: Boolean): List<String> {
		if (loading) return listOf("§7Loading garden...")
		val g = garden?.obj("garden") ?: return noData("Garden")
		return buildList {
			add("§aGarden XP: §f${fmt(g["garden_experience"].num())}")
			add("§7Unlocked plots: §f${(g["unlocked_plots_ids"] as? JsonArray)?.size ?: 0}/24")
			g.obj("commission_data")?.let { c ->
				val visits = c.obj("visits")
				add("§7Visitors served: §f${fmt(c["total_completed"].num())} §7| Unique: §f${fmt(c["unique_npcs_served"].num())} §7| Visits: §f${fmt(visits?.values?.sumOf { it.long() ?: 0 })}")
			} ?: add("§7No visitor data.")
			g.obj("composter_data")?.let { c ->
				add("§6Composter: §f${fmt(c["compost_items"].num())} compost")
			} ?: add("§7No composter data.")
			add("")
			add("§eCrop milestones (collected):")
			val crops = g.obj("resources_collected")
			if (crops == null || crops.isEmpty()) add("§7 none")
			else crops.entries.sortedByDescending { it.value.num() ?: 0.0 }.forEach { add(" §7${prettify(it.key)}: §f${fmt(it.value.num())}") }
			g.obj("crop_upgrade_levels")?.takeIf { it.isNotEmpty() }?.let { up ->
				add("§eCrop upgrades:")
				addAll(capped(up.entries.sortedByDescending { it.value.long() ?: 0 }.map { " §7${prettify(it.key)}: §f${it.value.long() ?: 0}" }, MAX_CROP_UPGRADES))
			}
		}
	}

	fun chocolate(member: JsonObject): List<String> {
		val easter = member.path("events", "easter") as? JsonObject ?: return noData("Chocolate Factory")
		val rabbits = easter.obj("rabbits")
		val rabbitCount = rabbits?.keys?.count { it != "collected_eggs" && it != "collected_locations" } ?: 0
		return buildList {
			add("§6Chocolate: §f${fmt(easter["chocolate"].num())}")
			add("§6All-time chocolate: §f${fmt(easter["total_chocolate"].num())}")
			add("§6Since prestige: §f${fmt(easter["chocolate_since_prestige"].num())}")
			add("§dPrestige: §f${easter["chocolate_level"].long() ?: 1} §7| Rabbits found: §f$rabbitCount")
			add("§7Barn capacity level: §f${easter["rabbit_barn_capacity_level"].long() ?: 0}")
			add("§7Click upgrades: §f${easter["click_upgrades"].long() ?: 0} §7| Time tower: §f${easter.path("time_tower", "level").long() ?: 0}")
			add("§7Rabbit shrine: §f${easter["rabbit_rarity_upgrades"].long() ?: 0} §7| Coach Jackrabbit: §f${easter["chocolate_multiplier_upgrades"].long() ?: 0}")
			add("")
			add("§eEmployees:")
			easter.obj("employees")?.entries?.forEach { add(" §7${prettify(it.key.removePrefix("rabbit_"))}: §f${it.value.long() ?: 0}") }
				?: add("§7 none")
		}
	}

	fun glacite(member: JsonObject): List<String> {
		val g = member.obj("glacite_player_data") ?: return noData("Glacite Tunnels")
		val core = member.obj("mining_core")
		return buildList {
			add("§bGlacite powder: §f${fmt(core?.get("powder_glacite").long())} §7(total §f${fmt((core?.get("powder_glacite").long() ?: 0) + (core?.get("powder_spent_glacite").long() ?: 0))}§7)")
			add("§7Mineshafts entered: §f${fmt(g["mineshafts_entered"].num())}")
			add("§7Fossil dust: §f${fmt(g["fossil_dust"].num())}")
			add("§7Fossils donated: §f${(g["fossils_donated"] as? JsonArray)?.size ?: 0}/8")
			add("")
			add("§eCorpses looted:")
			val corpses = g.obj("corpses_looted")
			if (corpses == null || corpses.isEmpty()) add("§7 none")
			else corpses.entries.forEach { add(" §7${prettify(it.key)}: §f${fmt(it.value.num())}") }
		}
	}

	fun foraging(member: JsonObject): List<String> {
		val sections = listOf("foraging_core", "foraging", "galatea", "agatha", "hunting")
			.mapNotNull { k -> member.obj(k)?.let { k to it } }
		val foragingXp = member.path("player_data", "experience", "SKILL_FORAGING").num()
		val huntingXp = member.path("player_data", "experience", "SKILL_HUNTING").num()
		if (sections.isEmpty() && foragingXp == null) return noData("Foraging / Galatea")
		return buildList {
			foragingXp?.let { add("§2Foraging XP: §f${fmt(it)}") }
			huntingXp?.let { add("§6Hunting XP: §f${fmt(it)}") }
			for ((name, obj) in sections) {
				add("")
				add("§e${prettify(name)}:")
				addAll(numericLines(obj, limit = 14).map { " $it" })
			}
			if (sections.isEmpty()) add("§7No Galatea / foraging progress data in the API.")
		}
	}

	fun attributes(member: JsonObject): List<String> {
		val shards = member.obj("attributes") ?: member.obj("shards") ?: member.path("hunting", "attributes") as? JsonObject
			?: return noData("Hunting / Attributes")
		val stacks = shards.obj("stacks") ?: shards
		return buildList {
			add("§6Attributes / shards:")
			stacks.entries.sortedByDescending { it.value.num() ?: 0.0 }.forEach {
				it.value.num()?.let { n -> add(" §7${prettify(it.key)}: §f${fmt(n)}") }
			}
			if (size == 1) add("§7 none")
		}
	}

	fun sacks(member: JsonObject): List<String> {
		val counts = member.path("inventory", "sacks_counts") as? JsonObject ?: return noData("Sacks")
		val nonZero = counts.entries.mapNotNull { (k, v) -> v.long()?.takeIf { it > 0 }?.let { k to it } }
		if (nonZero.isEmpty()) return listOf("§7Sacks are empty.")
		return listOf("§7Sack items: §f${nonZero.size} types, ${fmt(nonZero.sumOf { it.second })} items") +
			nonZero.sortedByDescending { it.second }.take(30).map { "§7${prettify(it.first)}: §f${fmt(it.second)}" }
	}

	fun magicalPower(member: JsonObject): Int? {
		val items = ProfileViewer.decodeInventory(member.path("inventory", "bag_contents", "talisman_bag")).filterNotNull()
		if (items.isEmpty()) return null
		val seen = mutableSetOf<String>()
		var total = 0
		for (item in items) {
			val id = item.catalogItemId?.skyblockId ?: continue
			if (!seen.add(id)) continue
			val rarityLine = item.lore.lastOrNull { it.isNotBlank() }?.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "") ?: continue
			val mp = when {
				rarityLine.startsWith("VERY SPECIAL") -> 5
				rarityLine.startsWith("SPECIAL") -> 3
				rarityLine.contains("MYTHIC") -> 22
				rarityLine.contains("LEGENDARY") -> 16
				rarityLine.contains("EPIC") -> 12
				rarityLine.contains("UNCOMMON") -> 5
				rarityLine.contains("RARE") -> 8
				rarityLine.contains("COMMON") -> 3
				else -> 0
			}
			total += if (id == "HEGEMONY_ARTIFACT") mp * 2 else mp
		}
		member.path("rift", "access", "consumed_prism").bool()?.takeIf { it }?.let { total += 11 }
		return total
	}

	fun petScore(member: JsonObject): Int {
		val pets = member.path("pets_data", "pets") as? JsonArray ?: return 0
		val best = mutableMapOf<String, Int>()
		for (p in pets) {
			val o = p as? JsonObject ?: continue
			val type = o["type"].str() ?: continue
			val r = when (o["tier"].str()) { "COMMON" -> 1; "UNCOMMON" -> 2; "RARE" -> 3; "EPIC" -> 4; "LEGENDARY" -> 5; "MYTHIC" -> 6; else -> 0 }
			best[type] = maxOf(best[type] ?: 0, r)
		}
		return best.values.sum()
	}
}
