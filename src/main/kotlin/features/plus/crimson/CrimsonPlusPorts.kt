// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.crimson

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.islands.CrimsonIsleHelper.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object CrimsonPlusPorts {
	private val VOLCANO = Regex("^\\s*Volcano: (\\S+)")
	private val PABLO_ASKS = listOf(
		"Are you available\\? I desperately need an? ([\\w ]+) today\\.",
		"Bring me that ([\\w ]+) as soon as you can!",
		"Could you bring me an? ([\\w ]+)\\?",
		"I really need an? ([\\w ]+) today, do you have one you could spare\\?",
	).map { Regex("^\\[NPC] Pablo: (?:✆ )?$it$") }
	private val SIRIH = Regex("^\\[NPC] Sirih:(?: ✆)? Oink\\.$")

	fun volcanoStatus(line: String): String? = VOLCANO.find(line)?.groupValues?.get(1)

	fun pabloFlower(line: String): String? {
		val text = line.trim()
		for (ask in PABLO_ASKS) ask.matchEntire(text)?.let { return it.groupValues[1] }
		return null
	}

	fun isSirih(line: String) = SIRIH.matches(line.trim())

	private fun onCrimsonIsle() = SkyBlockSession.skyblockIsland == SkyBlockIsland.CRIMSON_ISLE

	private fun hint(text: String) = Client.showChat(Component.literal("§e[SkyBlock Apex] $text"))

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!onCrimsonIsle()) return
		val line = event.bareText
		if (TConfig.pabloHelper) {
			val flower = pabloFlower(line)
			if (flower != null) hint("Pablo wants: §a$flower §7(/gfs $flower 1)")
		}
		if (TConfig.sirihHelper && isSirih(line)) hint("Sirih wants: §aSulphur Ore §7(/gfs Sulphur Ore 1)")
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.volcanoDisplay || !onCrimsonIsle()) return
		var status: String? = null
		for (entry in TabListReader.currentSnapshot.body) { status = volcanoStatus(entry.bareText); if (status != null) break }
		if (status == null) return
		event.drawHudLines(TConfig.volcanoHud, listOf(Component.literal("§bVolcano Explosivity§7: §f$status")))
	}
}
