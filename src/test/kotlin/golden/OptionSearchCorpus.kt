// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

object OptionSearchCorpus {
	data class Opt(val title: String, val category: String?, val description: List<String>, val bonus: Set<String>)

	val modName = "SkyBlock Apex"

	val options = listOf(
		Opt("Enable Storage Overlay", "Inventory", listOf("Shows every page of your storage at once"), emptySet()),
		Opt("Pet Overlay", "Pets", emptyList(), setOf("zookeeper")),
		Opt("Fix centred lines", "Chat", listOf("Re-centres Hypixel lines", "for your chat width"), emptySet()),
		Opt("Hide Watchdog messages", null, emptyList(), emptySet()),
		Opt("Dungeon Map", "Dungeons", listOf("Map of the current run"), setOf("catacombs", "f7")),
		Opt("Slayer Timer", "Slayer", emptyList(), emptySet()),
		Opt("ÜBER Mode", "Misc", listOf("Ünïcödé text"), emptySet()),
		Opt("Mining Waypoints", "Mining", listOf("Dwarven Mines and Crystal Hollows"), emptySet()),
		Opt("HUD scale", "HUD", emptyList(), setOf("size")),
		Opt("Pipe | Option", "Misc", emptyList(), emptySet()),
	)

	val queries = listOf("", " ", "  ", "pet", "PET", "overlay", "storage overlay", "overlay storage", "over lay", "sky", "skyblock apex",
		"dungeon|slayer", "dungeon | slayer", "|", "||", "pet|", "|pet", "zoo", "zookeeper", "f7", "catacombs map", "catacombs pets",
		"hypixel", "chat width", "width chat", "über", "ünïcödé", "UNICODE", "mines hollows", "mines  hollows", "x", "hud size",
		"size hud", "pipe", "pipe | option", "option", "e", "  e  ", "misc|mining", "mining|", "watchdog", "hide watch dog", "a|b|c|map")
}
