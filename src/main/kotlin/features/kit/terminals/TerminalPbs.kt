// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.terminals

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.features.dungeon.TerminalExtras
import dev.ashtonite365.apex.features.dungeon.TerminalTracker
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

object TerminalPbs {
	@Serializable
	data class Data(val best: MutableMap<String, Long> = mutableMapOf())

	@Config
	object Store : DataHolder<Data>(serializer(), "terminal-pbs", ::Data)

	private val STARTS_WITH = Regex("What starts with: '.'\\?")
	private val SELECT_ALL = Regex("Select all the [\\w ]+ items!")
	private val FIXED_TITLES = mapOf(
		"Correct all the panes!" to "Panes",
		"Change all to same color!" to "Rubix",
		"Click in order!" to "Numbers",
		"Click the button on time!" to "Melody",
	)
	private const val CONFIRM_WINDOW_MS = 2000

	fun typeOf(title: String): String? {
		FIXED_TITLES[title]?.let { return it }
		if (STARTS_WITH.matches(title)) return "Starts With"
		if (SELECT_ALL.matches(title)) return "Select All"
		return null
	}

	fun record(best: MutableMap<String, Long>, type: String, ms: Long): Pair<Long?, Boolean> {
		val previous = best[type]
		val isPb = previous == null || ms < previous
		if (isPb) best[type] = ms
		return previous to isPb
	}

	private var openKind: String? = null
	private var openSince = 0L
	private var lastClosed: Pair<String, Long>? = null
	private var lastClosedAt = 0L

	@Subscribe
	fun onScreen(event: ScreenChangeEvent) {
		if (!TerminalExtras.TConfig.terminalPbs || !DungeonUtil.isInDungeonIsland) return
		val now = System.currentTimeMillis()
		val kind = openKind
		if (kind != null) {
			lastClosed = kind to (now - openSince)
			lastClosedAt = now
		}
		val title = (event.new as? AbstractContainerScreen<*>)?.title?.bareText?.trim()
		openKind = title?.let { typeOf(it) }
		openSince = now
	}

	private fun summary(type: String, ms: Long, previous: Long?, isPb: Boolean): String {
		val head = "§a$type §7solved in §6${TerminalTracker.seconds(ms)}s§7"
		if (!isPb) return head + " §8(best ${TerminalTracker.seconds(previous!!)}s)"
		val was = if (previous == null) "" else " §7was ${TerminalTracker.seconds(previous)}s"
		return "$head §d§l(PB!)$was"
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TerminalExtras.TConfig.terminalPbs) return
		val m = TerminalTracker.completedRegex.matchEntire(event.bareText.trim()) ?: return
		if (m.groupValues[1] != Client.playerName || m.groupValues[3] != "terminal") return
		val (type, ms) = lastClosed ?: return
		if (System.currentTimeMillis() - lastClosedAt > CONFIRM_WINDOW_MS) return
		lastClosed = null
		val (previous, isPb) = record(Store.data.best, type, ms)
		if (isPb) Store.markDirty()
		Client.showChat(Component.literal(summary(type, ms, previous, isPb)))
	}
}
