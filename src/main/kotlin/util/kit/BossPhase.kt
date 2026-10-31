// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.kit

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent

enum class BossPhase(val boss: Boss, private val opener: String, private val podPlayerSuffix: Boolean = false) {
	MAXOR(Boss.F7, "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"),
	STORM(Boss.F7, "[BOSS] Storm: Pathetic Maxor, just like expected."),
	GOLDOR(Boss.F7, "[BOSS] Goldor: Who dares trespass into my domain?"),
	GOLDOR_CORE(Boss.F7, "The Core entrance is opening!"),
	NECRON(Boss.F7, "[BOSS] Necron: You went further than any human before, congratulations."),
	WITHER_KING(Boss.F7, "[BOSS] Wither King: You... again?"),
	KUUDRA_SUPPLIES(Boss.KUUDRA, "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"),
	KUUDRA_BUILD(Boss.KUUDRA, "[NPC] Elle: OMG! Great work collecting my supplies!"),
	KUUDRA_STUN(Boss.KUUDRA, "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!"),
	KUUDRA_DPS(Boss.KUUDRA, " destroyed one of Kuudra's pods!", podPlayerSuffix = true),
	KUUDRA_SKIP(Boss.KUUDRA, "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!"),
	KUUDRA_END(Boss.KUUDRA, "[NPC] Elle: Good job everyone. A hard fought battle come to an end. Let's get out of here before we run into any more trouble!"),
	;

	enum class Boss { F7, KUUDRA }

	fun opensWith(line: String): Boolean {
		if (!podPlayerSuffix) return line == opener
		if (!line.endsWith(opener)) return false
		val name = line.substring(0, line.length - opener.length)
		return name.length in 1..16 && name.none { it == '\n' || it == '\r' || it == '\u0085' || it == '\u2028' || it == '\u2029' }
	}

	companion object {
		fun match(line: String): BossPhase? = entries.firstOrNull { it.opensWith(line) }
	}
}

data class BossPhaseEvent(val phase: BossPhase, val previous: BossPhase?, val serverTick: Long) : ApexEvent() {
	companion object : ApexEventBus<BossPhaseEvent>()
}

object BossPhaseTracker {
	var current: BossPhase? = null
		private set
	var phaseStartTick: Long = 0L
		private set

	fun handle(line: String, tick: Long): BossPhaseEvent? {
		val next = BossPhase.match(line)?.takeIf { it != current } ?: return null
		return BossPhaseEvent(next, current, tick).also { current = next; phaseStartTick = tick }
	}

	fun reset() {
		current = null
		phaseStartTick = 0L
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		handle(event.bareText, ServerTicks.totalTicks)?.let(BossPhaseEvent::publish)
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()
}
