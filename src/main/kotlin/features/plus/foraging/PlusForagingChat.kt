// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.foraging

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.features.foraging.GalateaHelpers
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.plus.matches
import net.minecraft.network.chat.Component

object PlusForagingChat {
	private val patterns = PlusPatterns.group("foraging")
	private val sweepHeader by patterns.pattern("sweep-details.header", "Sweep Details: (?<sweep>[\\d,.]+).? Sweep")
	private val sweepLine by patterns.pattern("sweep-details.line", "\\s+.+: .*")
	private val toughness by patterns.pattern("sweep-details.toughness", "\\s+(?<tree>[\\S ]+) Tree Toughness: (?<t>[\\d,.]+) (?<logs>[\\d,.]+) Logs")
	private val bracket by patterns.pattern("starlyn.start-results", "\\[NPC] (?<sister>[\\S ]+): You reached the (?<bracket>\\w+) Bracket in my contest!")
	private val points by patterns.pattern("starlyn.points-earned", "\\[NPC] (?<sister>[\\S ]+): You earned a total of (?<points>[\\d,]+) points!(?<pb> That's a new .*PERSONAL BEST.*)?")
	private val starlynOther by patterns.pattern("starlyn.other", "\\[NPC] (?:Agatha|Starlyn|[\\S ]+): (?:Your previous Personal Best was .*|Come see me at .* to claim your rewards!)")

	data class Sweep(val sweep: String, var tree: String? = null, var logs: String? = null)

	private val DETAIL_WINDOW_MS = 500L
	private var pending: Sweep? = null
	private var pendingSince = TimeMark.farPast()
	private var lastBracket: Pair<String, String>? = null

	fun parseSweepHeader(line: String): String? = sweepHeader.matchMatcher(line) { group("sweep") }

	fun parseToughness(line: String): Pair<String, String>? = toughness.matchMatcher(line) { group("tree") to group("logs") }

	fun summary(s: Sweep): String {
		val head = "§6Sweep: §2${s.sweep}∮"
		val tree = s.tree ?: return head
		val logs = s.logs?.let { ", §a$it logs§7" }.orEmpty()
		return "$head §7($tree$logs)"
	}

	@Subscribe
	fun onChat(event: AllowChatEvent) {
		val msg = event.bareText
		if (GalateaHelpers.TConfig.compactSweepDetails && compactSweep(msg)) { event.cancel(); return }
		if (GalateaHelpers.TConfig.compactStarlynContest && compactStarlyn(msg)) event.cancel()
	}

	private fun compactSweep(msg: String): Boolean {
		val header = parseSweepHeader(msg.trim())
		if (header != null) {
			sendPendingSweep()
			pending = Sweep(header)
			pendingSince = TimeMark.now()
			return true
		}
		val current = pending
		if (current != null && pendingSince.passedTime().inWholeMilliseconds < DETAIL_WINDOW_MS && sweepLine.matches(msg)) {
			parseToughness(msg)?.let { (tree, logs) -> current.tree = tree; current.logs = logs }
			return true
		}
		sendPendingSweep()
		return false
	}

	private fun compactStarlyn(msg: String): Boolean {
		if (bracket.matchMatcher(msg) { lastBracket = group("sister") to group("bracket") } != null) return true
		val shown = points.matchMatcher(msg) {
			val tier = lastBracket?.second ?: "?"
			val pb = if (group("pb") != null) " §d§lPB" else ""
			Client.showChat(Component.literal("§e${group("sister")} contest: §f$tier §7bracket, §b${group("points")} §7points$pb"))
			lastBracket = null
		}
		if (shown != null) return true
		return starlynOther.matches(msg)
	}

	private fun sendPendingSweep() {
		val done = pending ?: return
		pending = null
		Client.showChat(Component.literal(summary(done)))
	}
}
