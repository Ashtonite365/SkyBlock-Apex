// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.diana

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText

object DianaWaypoints {
	val identifier: String get() = "diana"

	@Config
	object TConfig : ManagedConfig(category = Category.EVENTS, name = identifier) {
		val ancestralSpadeSolver by riskyToggle("ancestral-spade", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val ancestralSpadeTeleport by keyBindingWithDefaultUnbound("ancestral-teleport")
		val nearbyWaypoints by toggle("nearby-waypoints") { true }

		val burrowLabels by riskyToggle("burrow-labels", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val burrowBeams by riskyToggle("burrow-beams", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val guessBeam by riskyToggle("guess-beam", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val chainAlerts by toggle("chain-alerts") { false }

		val muteBuggedSpade by toggle("mute-bugged-spade") { false }
		val griffinPetWarning by toggle("griffin-pet-warning") { false }

		val highlightRareMobs by toggle("highlight-rare-mobs") { false }
		val rareMobAlert by toggle("rare-mob-alert") { false }
	}

	private val CHAIN_END = Regex("^You (?:finished|completed) the Griffin [Bb]urrow chain!.*")
	private val CHAIN_FAIL = Regex("^Defeat all the burrow defenders in order to dig it!.*")

	@Subscribe
	fun onChat(event: dev.ashtonite365.apex.events.ProcessChatEvent) {
		if (!TConfig.chainAlerts) return
		val line = event.bareText.trim()
		when {
			CHAIN_END.matches(line) -> dev.ashtonite365.apex.util.Alerts.show(net.minecraft.network.chat.Component.literal("§a§lBurrow chain complete"))
			CHAIN_FAIL.matches(line) -> dev.ashtonite365.apex.util.Alerts.show(net.minecraft.network.chat.Component.literal("§c§lKill the burrow mob first"))
		}
	}

	@Subscribe
	fun burrowUse(event: UseBlockEvent) = NearbyBurrowsSolver.onBlockClick(event.hitResult.blockPos)

	@Subscribe
	fun burrowAttack(event: AttackBlockEvent) = NearbyBurrowsSolver.onBlockClick(event.blockPos)
}
