// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.chat

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object PlayerChatFormat {
	@Config
	object TConfig : ManagedConfig("player-chat-format", Category.CHAT) {
		val enabled by toggle("enabled") { false }
		val shortChannels by toggle("short-channels") { true }
		val hideLevel by toggle("hide-level") { false }
		val colourLevel by toggle("colour-level") { true }
		val hideRank by toggle("hide-rank") { false }
		val symbols by choice("symbols") { SymbolMode.COLOURED }
	}

	enum class SymbolMode : StringRepresentable {
		KEEP, COLOURED, HIDE;

		override fun getSerializedName() = name.lowercase()
	}

	private val channelShort = listOf("Party > " to "P > ", "Guild > " to "G > ", "Officer > " to "O > ", "Co-op > " to "CC > ")
	private val levelRegex = Regex("\\[(\\d{1,3})] ")
	private val rankRegex = Regex("\\[(VIP|VIP\\+|MVP|MVP\\+|MVP\\+\\+|YOUTUBE|ADMIN|GM|MOD|HELPER|PIG\\+\\+\\+)] ")
	private val symbolColours = mapOf('♲' to ChatFormatting.GRAY, 'Ⓑ' to ChatFormatting.GOLD, '☀' to ChatFormatting.AQUA)

	private val playerLine = Regex("^(?:(?:Party|Guild|Officer|Co-op) > )?(?:\\[\\d{1,3}] )?(?:[♲Ⓑ☀] )?(?:\\[[A-Z+]+] )?\\w{1,16}(?: \\[[^]]+])?: ")

	fun levelColour(level: Int): ChatFormatting = when {
		level >= 480 -> ChatFormatting.DARK_RED
		level >= 440 -> ChatFormatting.RED
		level >= 400 -> ChatFormatting.GOLD
		level >= 360 -> ChatFormatting.DARK_PURPLE
		level >= 320 -> ChatFormatting.LIGHT_PURPLE
		level >= 280 -> ChatFormatting.BLUE
		level >= 240 -> ChatFormatting.DARK_AQUA
		level >= 200 -> ChatFormatting.AQUA
		level >= 160 -> ChatFormatting.DARK_GREEN
		level >= 120 -> ChatFormatting.GREEN
		level >= 80 -> ChatFormatting.YELLOW
		level >= 40 -> ChatFormatting.WHITE
		else -> ChatFormatting.GRAY
	}

	@Subscribe
	fun onChat(event: ModifyChatEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		if (!playerLine.containsMatchIn(event.bareText)) return
		val out = Component.empty()
		var inPrefix = true
		for (piece in event.replaceWith.toFlatList()) {
			var text = piece.string
			if (inPrefix) {
				val colon = text.indexOf(": ")
				val prefix = if (colon >= 0) text.substring(0, colon) else text
				val rest = if (colon >= 0) text.substring(colon) else ""
				var p = prefix
				if (TConfig.shortChannels) channelShort.forEach { (a, b) -> p = p.replace(a, b) }
				if (TConfig.hideRank) p = p.replace(rankRegex, "")
				if (TConfig.hideLevel) p = p.replace(levelRegex, "")
				if (TConfig.symbols == SymbolMode.HIDE) p = p.replace(Regex("[♲Ⓑ☀] ?"), "")
				appendStyled(out, p, piece)
				if (rest.isNotEmpty()) { out.append(Component.literal(rest).withStyle(piece.style)); inPrefix = false }
				continue
			}
			out.append(Component.literal(text).withStyle(piece.style))
		}
		event.replaceWith = out
	}

	private fun appendStyled(out: MutableComponent, text: String, piece: Component) {
		if (text.isEmpty()) return
		var last = 0
		val tokens = Regex("\\[(\\d{1,3})]|[♲Ⓑ☀]").findAll(text)
		for (m in tokens) {
			if (m.range.first > last) out.append(Component.literal(text.substring(last, m.range.first)).withStyle(piece.style))
			val level = m.groupValues[1].toIntOrNull()
			val colour = when {
				level != null && TConfig.colourLevel -> levelColour(level)
				level == null && TConfig.symbols == SymbolMode.COLOURED -> symbolColours[m.value[0]]
				else -> null
			}
			val c = Component.literal(m.value).withStyle(piece.style)
			out.append(if (colour != null) c.withStyle(colour) else c)
			last = m.range.last + 1
		}
		if (last < text.length) out.append(Component.literal(text.substring(last)).withStyle(piece.style))
	}
}
