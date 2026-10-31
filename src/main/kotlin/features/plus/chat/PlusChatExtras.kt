// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.chat

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.ChatFilters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.getLegacyFormatString
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component

object PlusChatExtras {
	@Config
	object TConfig : ManagedConfig("plus-chat-extras", Category.CHAT) {
		val hideFarDeaths by toggle("hide-far-deaths") { false }
		val skyblockXpInChat by toggle("skyblock-xp-in-chat") { false }

		init {
			ChatFilters.register(ChatFilters.Filter("far-deaths", { hideFarDeaths && SkyBlockSession.inSkyBlock && !inInstance() }) { isFarDeath(it) })
		}
	}

	private const val NEAR_BLOCKS = 25
	private val NEAR_MEMORY = 30.seconds

	private fun inInstance(): Boolean {
		val island = SkyBlockSession.skyblockIsland
		return island == SkyBlockIsland.DUNGEON || island == SkyBlockIsland.KUUDRA
	}

	private val DEATH = Regex("^ ?☠ (?<name>\\w+) (?<reason>.+)$")

	fun deathName(line: String): String? {
		val name = DEATH.matchEntire(line)?.groups?.get("name")?.value ?: return null
		return if (name == "You") null else name
	}

	private val nearbySince = HashMap<String, TimeMark>()

	private fun isFarDeath(line: String): Boolean {
		val name = deathName(line) ?: return false
		val seen = nearbySince[name] ?: return true
		return seen.passedTime() > NEAR_MEMORY
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0 || !TConfig.hideFarDeaths) return
		val me = Client.player ?: return
		val players = Client.level?.players() ?: return

		if (event.tickCount % 1200 == 0) nearbySince.values.removeIf { it.passedTime() > NEAR_MEMORY }
		for (other in players) {
			if (other === me || other.distanceTo(me) >= NEAR_BLOCKS) continue
			nearbySince[other.name.string] = TimeMark.now()
		}
	}

	private val XP_SEGMENT = Regex(".*(§b\\+\\d+ SkyBlock XP §.\\([^()]+\\)§b \\(\\d+/\\d+\\)).*")

	fun xpSegment(formatted: String): String? = XP_SEGMENT.matchEntire(formatted)?.groupValues?.get(1)

	private var lastSegment: String? = null

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		if (!TConfig.skyblockXpInChat) return
		val segment = xpSegment(event.text.getLegacyFormatString()) ?: return
		if (segment == lastSegment) return
		lastSegment = segment
		Client.showChat(Component.literal(segment))
	}
}
