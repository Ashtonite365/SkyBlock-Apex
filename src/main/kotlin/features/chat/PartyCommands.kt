// SPDX-License-Identifier: GPL-3.0-or-later AND BSD-3-Clause

package dev.ashtonite365.apex.features.chat

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.PartyMessageReceivedEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object PartyCommands {

	init {
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_CHAT.register { typed ->
			if (!TConfig.chatEmotes) typed else replaceEmotes(typed) ?: typed
		}
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_COMMAND.register { command ->
			if (!TConfig.chatEmotes) command else replaceEmotes("/$command")?.drop(1) ?: command
		}
	}

	enum class Channel { PARTY, GUILD, PRIVATE, COOP }

	data class Incoming(val channel: Channel, val name: String, val message: String)

	private const val SENDER = "(?:\\[\\d{1,4}] )?(?:[^\\w\\s\\[\\]:] )?(?:\\[[^]]*?] ?)?(\\w{1,16})(?: [^\\w\\s:])?(?: \\[[^]]*?])?"

	private val CHANNELS = listOf(Channel.PARTY to "(?:Party|P) > ", Channel.GUILD to "Guild > ", Channel.PRIVATE to "From ", Channel.COOP to "Co-op > ")

	private val LINE = Regex("^(?:" + CHANNELS.joinToString("|") { (_, prefix) -> "$prefix$SENDER: ?(.+)$" } + ")")

	fun parse(raw: String): Incoming? {
		val groups = LINE.find(raw)?.groupValues ?: return null
		for ((i, entry) in CHANNELS.withIndex()) {
			val name = groups[2 * i + 1]
			if (name.isNotEmpty()) return Incoming(entry.first, name, groups[2 * i + 2])
		}
		return null
	}

	private val emotes = mapOf(
		"<3" to "❤", "o/" to "( ﾟ◡ﾟ)/", ":star:" to "✮", ":yes:" to "✔", ":no:" to "✖", ":java:" to "☕",
		":arrow:" to "➜", ":shrug:" to "¯\\_(\u30c4)_/¯", ":tableflip:" to "(╯°□°）╯︵ ┻━┻", ":totem:" to "☉_☉",
		":typing:" to "✎...", ":maths:" to "√(π+x)=L", ":snail:" to "@'-'", "ez" to "ｅｚ", ":thinking:" to "(0.o?)",
		":gimme:" to "༼つ◕_◕༽つ", ":wizard:" to "('-')⊃━☆ﾟ.*･｡ﾟ", ":pvp:" to "⚔", ":peace:" to "✌",
		":puffer:" to "<('O')>", "h/" to "ヽ(^◇^*)/", ":sloth:" to "(・⊝・)", ":dog:" to "(ᵔᴥᵔ)", ":dj:" to "ヽ(⌐■_■)ノ♬",
		":yey:" to "ヽ (◕◡◕) ﾉ", ":snow:" to "☃", ":dab:" to "<o/", ":cat:" to "= ＾● ⋏ ●＾ =", ":cute:" to "(✿◠‿◠)",
		":skull:" to "☠", ":bum:" to "♿",
	)

	private val CHAT_COMMANDS = setOf("/pc", "/ac", "/gc", "/cc", "/msg", "/w", "/r")

	fun replaceEmotes(message: String): String? {
		if (message.startsWith("/") && message.substringBefore(' ') !in CHAT_COMMANDS) return null
		val words = message.split(" ")
		var replaced = false
		val result = ArrayList<String>(words.size)
		for (w in words) {
			val emote = emotes[w]
			if (emote != null) replaced = true
			result.add(emote ?: w)
		}
		return if (replaced) result.joinToString(" ") else null
	}

	@Config
	object TConfig : ManagedConfig("party-commands", Category.CHAT) {
		val chatEmotes by toggle("chat-emotes") { false }
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val incoming = parse(event.bareText) ?: return
		if (incoming.channel != Channel.PARTY) return
		PartyMessageReceivedEvent.publish(PartyMessageReceivedEvent(event, incoming.message, incoming.name))
	}
}
