/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): dungeon party inspector (/apex dungeonparty).
 * Dungeon party overview idea (see CREDITS.md); reimplemented from the feature description,
 * no code copied. Display only: it prints a chat summary, it never sends anything.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.skyblock.PartyUtil
import dev.ashtonite365.apex.util.bareText

object DungeonPartyInspector {
	val classes = listOf("Archer", "Berserk", "Healer", "Mage", "Tank")

	private val known = mutableMapOf<String, String>()

	private val pfJoin = "Party Finder > (\\w{2,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)".toRegex()
	private val tabEntry = "(?:\\[\\d+] )?(\\w{2,16})(?: .)? \\((Archer|Berserk|Healer|Mage|Tank)(?: ([IVXLCDM]+|\\d+))?\\)".toRegex()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		pfJoin.find(event.bareText)?.let { known[it.groupValues[1]] = "${it.groupValues[2]} ${it.groupValues[3]}" }
	}

	private fun readTab() {
		TabListReader.currentSnapshot.body.forEach { line ->
			tabEntry.find(line.bareText.trim())?.let {
				known[it.groupValues[1]] = (it.groupValues[2] + " " + it.groupValues[3]).trim()
			}
		}
	}

	fun summary(): List<Component> {
		readTab()
		val members = PartyUtil.party?.members?.map { it.name } ?: listOf(Client.playerName)
		val out = mutableListOf<Component>(Component.literal("§6[SkyBlock Apex] §eDungeon party (${members.size}/5):"))
		val taken = mutableSetOf<String>()
		for (name in members) {
			val cls = known[name]
			cls?.substringBefore(' ')?.let(taken::add)
			out.add(Component.literal(" §7- §f$name §7${cls ?: "(class unknown)"}"))
		}
		val missing = classes - taken
		out.add(Component.literal(if (missing.isEmpty()) "§aAll classes covered" else "§cMissing: §f" + missing.joinToString(", ")))
		if (members.size < 5) out.add(Component.literal("§7Open slots: ${5 - members.size}"))
		return out
	}

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.DUNGEONS, "dungeonparty", "Show who is in your party and which dungeon classes are missing") {
			onRun { summary().forEach(Client::showChat) }
		}
	}
}
