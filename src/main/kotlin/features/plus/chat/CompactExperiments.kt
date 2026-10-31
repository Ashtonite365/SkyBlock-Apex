// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.chat

import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark

object CompactExperiments {
	private val TABLE_TITLE = Regex("^((Superpairs|Chronomatron|Ultrasequencer) (\\(.+\\)|➜ Stakes|Rewards)|Experimentation Table)$")
	private val CLAIMED = Regex("^You claimed the \\S+ rewards!$")
	private val REWARD_LINE = Regex("^ ?\\+(?<reward>.+)$")

	fun isExperimentInventory(title: String) = TABLE_TITLE.matches(title)
	fun isClaim(line: String) = CLAIMED.matches(line)
	fun reward(line: String): String? = REWARD_LINE.matchEntire(line)?.groups?.get("reward")?.value

	private fun hoverLine(text: String, hover: String): Component =
		Component.literal(text).withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal(hover))) }

	private class RewardBatch(val title: String) {
		val items = ArrayList<String>()
		var lastTick = 0
	}

	private var leftTableAt = TimeMark.farPast()
	private var tableOpen = false
	private var batch: RewardBatch? = null
	private var currentTick = 0

	@Subscribe
	fun onScreen(event: ScreenChangeEvent) {
		if (tableOpen) leftTableAt = TimeMark.now()
		val title = event.new?.title?.string
		tableOpen = title != null && isExperimentInventory(title)
	}

	@Subscribe
	fun onAllow(event: AllowChatEvent) {
		if (!PlusChatCompact.TConfig.compactExperiments) return
		if (leftTableAt.passedTime() > 3.seconds) return
		val raw = event.bareText
		val trimmed = raw.trim()
		if (isClaim(trimmed)) {
			batch = RewardBatch(trimmed)
			event.cancel()
			return
		}
		val open = batch ?: return
		val item = reward(raw) ?: return
		open.items += item
		open.lastTick = currentTick
		event.cancel()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		currentTick = event.tickCount
		val open = batch ?: return
		if (open.items.isEmpty() || currentTick - open.lastTick < 3) return
		Client.showChat(hoverLine("§e${open.title}", open.items.joinToString("\n") { "§8+§r$it" }))
		batch = null
	}

	class JacobCompactor {
		private var inside = false
		private val body = ArrayList<String>()
		private var contestCount = 0

		fun feed(raw: String): Pair<Boolean, Pair<String, List<String>>?> {
			val line = raw.trim()
			if (!inside) {
				if (line != HEADER) return false to null
				inside = true
				body.clear()
				contestCount = 0
				return true to null
			}
			if (isRule(line) || body.size > 60) {
				inside = false
				val plural = if (contestCount == 1) "" else "s"
				return true to ("§6§l$HEADER §7($contestCount contest$plural, hover)" to body.toList())
			}
			if (line.startsWith("» ")) contestCount++
			if (line.isNotEmpty()) body += line
			return true to null
		}

		private fun isRule(line: String) = line.length > 20 && line.all { it == '▬' }

		private companion object {
			const val HEADER = "FARMING CONTEST REWARDS CLAIMED"
		}
	}

	private val jacob = JacobCompactor()

	@Subscribe
	fun onJacob(event: AllowChatEvent) {
		if (!PlusChatCompact.TConfig.compactJacob) return
		val (hide, done) = jacob.feed(event.bareText)
		if (!hide) return
		event.cancel()
		done?.let { (text, hover) -> Client.showChat(hoverLine(text, hover.joinToString("\n"))) }
	}

	class StashCompactor {
		private var pendingAmount: String? = null
		private var pendingKind = "items"
		private var pendingTypes: String? = null

		fun feed(raw: String): Pair<Boolean, Pair<String, Boolean>?> {
			val line = raw.trim()
			STASHED.matchEntire(line)?.let {
				pendingAmount = it.groupValues[1]
				pendingKind = it.groupValues[2]
				pendingTypes = null
				return true to null
			}
			val amount = pendingAmount ?: return false to null
			TYPES.matchEntire(line)?.let {
				pendingTypes = it.groupValues[1]
				return true to null
			}
			pendingAmount = null
			if (!PICK_UP.matches(line)) return false to null
			val types = pendingTypes?.let { n -> " §7($n type${if (n == "1") "" else "s"})" }.orEmpty()
			return true to ("§eYou have §a$amount §e$pendingKind stashed$types. §6Click to pick up!" to (pendingKind == "materials"))
		}

		private companion object {
			val STASHED = Regex("^You have ([\\d,]+) (items|materials) stashed away!$")
			val TYPES = Regex("^\\(This totals (\\d+) types? of (?:items|materials) stashed!\\)$")
			val PICK_UP = Regex("^>>> CLICK HERE to pick (them|it) up! <<<$")
		}
	}

	private val stash = StashCompactor()

	@Subscribe
	fun onStash(event: AllowChatEvent) {
		if (!PlusChatCompact.TConfig.compactStash) return
		val (hide, done) = stash.feed(event.bareText)
		if (!hide) return
		event.cancel()
		if (done == null) return
		val command = "/pickupstash"
		Client.showChat(Component.literal(done.first).withStyle {
			it.withClickEvent(ClickEvent.RunCommand(command)).withHoverEvent(HoverEvent.ShowText(Component.literal(command)))
		})
	}
}
