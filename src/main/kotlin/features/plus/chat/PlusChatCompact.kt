// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.chat

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.TextColor
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object PlusChatCompact {
	@Config
	object TConfig : ManagedConfig("plus-chat-compact", Category.CHAT) {
		val shortenCoins by toggle("shorten-coins") { false }
		val compactPotions by toggle("compact-potions") { false }
		val compactBestiary by toggle("compact-bestiary") { false }
		val compactExperiments by toggle("compact-experiments") { false }
		val darkAuctionItem by toggle("dark-auction-item") { false }
		val compactJacob by toggle("compact-jacob") { false }
		val compactStash by toggle("compact-stash") { false }
	}

	private val NUMBER = Regex("(\\d[\\d,.]+)(?![\\d.,kMB])(?= )")
	private val GOLD: TextColor? = TextColor.fromLegacyFormat(ChatFormatting.GOLD)

	fun shortenNumbers(text: String): String = NUMBER.replace(text) { found ->
		when (val n = found.groupValues[1].filter { it != ',' }.toDoubleOrNull()) {
			null -> found.value
			else -> if (n >= 1000) ApexFormatters.compactNumber(n) else found.value
		}
	}

	private fun isGold(part: Component) = part.style.color == GOLD

	private fun shortenGoldParts(message: Component): Component? {
		val parts = message.toFlatList()
		if (!parts.any { isGold(it) && NUMBER.containsMatchIn(it.string + " ") }) return null
		var touched = false
		val rebuilt = Component.empty()
		for (part in parts) {
			val before = part.string
			val after = if (isGold(part)) shortenNumbers("$before ").dropLast(1) else before
			touched = touched || after != before
			rebuilt.append(Component.literal(after).withStyle(part.style))
		}
		return rebuilt.takeIf { touched }
	}

	private const val EFFECTS_HINT = "! Press TAB or type /effects to view your active effects!"
	private const val CANDY = "Poisoned Candy I"

	private class BuffShape(body: String, val byPlayer: Boolean) {
		val regex = Regex("^BUFF! $body$")
	}

	private val BUFF_SHAPES = listOf(
		BuffShape("You were splashed by (?<player>.*) with (?<effect>.*)" + Regex.escape(EFFECTS_HINT), true),
		BuffShape("You have gained (?<effect>.*)" + Regex.escape(EFFECTS_HINT), false),
		BuffShape("You splashed yourself with (?<effect>.*)" + Regex.escape(EFFECTS_HINT), false),
		BuffShape("You have gained (?<effect>$CANDY)!", false),
		BuffShape("You splashed yourself with (?<effect>$CANDY)!", false),
		BuffShape("You were splashed by (?<player>.*) with (?<effect>$CANDY)!", true),
	)

	fun compactPotion(line: String): String? = BUFF_SHAPES.firstNotNullOfOrNull { shape ->
		shape.regex.matchEntire(line)?.let { m ->
			val effect = m.groups["effect"]!!.value
			val suffix = if (shape.byPlayer) m.groups["player"]?.value?.let { " §aby §f$it" } ?: "" else ""
			"§a§lPotion Effect! §r$effect$suffix"
		}
	}

	private fun potionLine(text: String): Component = Component.literal(text).withStyle {
		it.withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick to view your potion effects.")))
			.withClickEvent(ClickEvent.RunCommand("/effects"))
	}

	private val SIRIUS_OFFER = Regex("^\\[NPC] Sirius: (?:First|Next) up we have an? (?<item>[^,]+), the starting bid is [\\d,]+ Coins!$")
	private val SIRIUS_RESULT = Regex("^\\[NPC] Sirius: (?:The highest bidder was \\w+ with a bid of [\\d,]+ Coins!|No one made any bids!.*)$")
	private var offered: String? = null

	fun darkAuction(line: String): String? {
		val offer = SIRIUS_OFFER.matchEntire(line)
		if (offer != null) {
			offered = offer.groups["item"]!!.value
			return null
		}
		if (!SIRIUS_RESULT.matches(line)) return null
		val item = offered
		offered = null
		return item
	}

	@Subscribe
	fun onModify(event: ModifyChatEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val plain = event.bareText.trim()
		if (TConfig.darkAuctionItem) {
			val item = darkAuction(plain)
			if (item != null) {
				event.replaceWith = event.replaceWith.copy().append(Component.literal(" §7($item)"))
				return
			}
		}
		if (TConfig.compactPotions) {
			val compact = compactPotion(plain)
			if (compact != null) {
				event.replaceWith = potionLine(compact)
				return
			}
		}
		if (TConfig.shortenCoins) shortenGoldParts(event.replaceWith)?.let { event.replaceWith = it }
	}

	class BestiaryCompactor {
		var active = false
			private set
		var milestone: String? = null
			private set
		private val collected = ArrayList<String>()
		private var seen = 0
		private var collecting = true

		private fun isRule(line: String) = line.length > 20 && line.all { it == '▬' }

		fun feed(raw: String): Pair<Boolean, Pair<String, List<String>>?> {
			val line = raw.trim()
			if (!active) return if (line == "BESTIARY") { start(line); true to null } else false to null
			if (++seen > 15) {
				active = false
				return true to null
			}
			if (isRule(line)) {
				active = false
				return true to collected.getOrNull(1)?.let { it to collected.toList() }
			}
			MILESTONE.matchEntire(line)?.let {
				collecting = false
				milestone = it.groupValues[1]
			}
			if (collecting && line.isNotEmpty()) collected += line
			return true to null
		}

		private fun start(header: String) {
			active = true
			seen = 0
			collecting = true
			milestone = null
			collected.clear()
			collected += header
		}

		private companion object {
			val MILESTONE = Regex("^.+ (\\d{1,3}➡\\d{1,3})$")
		}
	}

	private val bestiary = BestiaryCompactor()

	@Subscribe
	fun onAllow(event: AllowChatEvent) {
		if (!TConfig.compactBestiary || !SkyBlockSession.inSkyBlock) return
		val (hide, done) = bestiary.feed(event.bareText)
		if (!hide) return
		event.cancel()
		if (done == null) return
		val (title, hover) = done
		Client.showChat(Component.literal("§6§lBESTIARY §r$title").withStyle {
			it.withHoverEvent(HoverEvent.ShowText(Component.literal(hover.joinToString("\n"))))
		})
		bestiary.milestone?.let { Client.showChat(Component.literal("§6§lBESTIARY MILESTONE §e$it")) }
	}
}
