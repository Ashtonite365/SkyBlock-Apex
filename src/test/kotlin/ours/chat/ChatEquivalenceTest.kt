// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.brigadier.context.CommandContextBuilder
import java.lang.reflect.Proxy
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.ChatFormatting
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.features.chat.AutoCompletions
import dev.ashtonite365.apex.features.chat.ChatLinks
import dev.ashtonite365.apex.features.chat.CopyChat
import dev.ashtonite365.apex.features.chat.PartyCommands
import dev.ashtonite365.apex.features.chat.QuickCommands
import dev.ashtonite365.apex.test.ours.OursGolden

class ChatEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun attempt(block: () -> Any?): String = try {
		block().toString()
	} catch (e: Exception) {
		"threw ${e.javaClass.simpleName}"
	}

	@Test
	fun wikiKeyMigrationIsTheSame() {
		val out = mutableListOf<String>()
		val inputs = listOf(
			"{}", "{\"auto-completions\":{}}", "{\"auto-completions\":{\"wiki-key\":5}}",
			"{\"auto-completions\":{\"wiki-key\":{}}}",
			"{\"auto-completions\":{\"wiki-key\":{\"button\":null,\"modifiers\":{\"modifiers\":0}}}}",
			"{\"auto-completions\":{\"wiki-key\":{\"button\":null,\"modifiers\":{\"modifiers\":3}}}}",
			"{\"auto-completions\":{\"wiki-key\":{\"button\":{\"keyCode\":70},\"modifiers\":{\"modifiers\":0}}}}",
			"{\"auto-completions\":{\"wiki-key\":{\"modifiers\":{\"modifiers\":\"x\"}}}}",
			"{\"auto-completions\":{\"wiki-key\":{\"modifiers\":{}}}}",
			"{\"auto-completions\":{\"wiki-key\":{\"button\":null,\"modifiers\":5}}}",
			"{\"auto-completions\":5}",
		)
		for (text in inputs) {
			val data = JsonParser.parseString(text).asJsonObject
			val result = attempt { AutoCompletions.migrateWikiKey(data) }
			out += "$text -> $result ${OursGolden.json(data)}"
		}
		out += "identifier ${AutoCompletions.identifier} ${ChatLinks.identifier} ${CopyChat.identifier} ${QuickCommands.identifier}"
		OursGolden.check("chat-wiki-migration", out)
	}

	@Test
	fun linksAreRewrittenTheSame() {
		val out = mutableListOf<String>()
		out += "regex ${ChatLinks.urlRegex.pattern} ext=${ChatLinks.imageExtensions}"
		val urls = listOf("https://a.b", "https://example.com/x.png", "https://example.com/x.PNG", "https://x.y/z.jpeg?q", "https://x.y/z.gif.", "https://x", "http://a.b/c", "https://a.b/c d", "https://a.b/c.webp", "x.png", "")
		for (u in urls) out += "url [$u] image=${ChatLinks.isImageUrl(u)} entire=${ChatLinks.urlRegex.matchEntire(u)?.groupValues} find=${ChatLinks.urlRegex.find(u)?.groupValues}"

		val messages = listOf(
			Component.literal("no links here"),
			Component.literal("see https://example.org/page now"),
			Component.literal("two https://a.example/x and https://b.example/y."),
			Component.literal("end https://example.org/pic.png"),
			Component.literal("bad https://exa mple"),
			Component.literal("weird https://ex^ample.org/%%% end"),
			Component.literal("scheme ftp://example.org/ only"),
			Component.literal("styled ").withStyle(ChatFormatting.RED).append(Component.literal("https://red.example/a b").withStyle(Style.EMPTY.withBold(true))),
			Component.literal("https://start.example/ at start"),
			Component.literal("dot https://dot.example/a. next"),
			Component.translatable("chat.type.text", "Name", "go https://tr.example/q"),
		)

		@Suppress("UNCHECKED_CAST")
		val options = ChatLinks.TConfig.sortedOptions as List<dev.ashtonite365.apex.gui.config.ManagedOption<Any>>
		val previous = options.map { it._actualValue }
		ChatLinks.TConfig.explicitDefaultLoad()
		for (m in messages) {
			val event = ModifyChatEvent(m)
			val outcome = try { ChatLinks.onModifyChat(event); "ok" } catch (e: Throwable) { "ERR:" + e.javaClass.simpleName + " " + e.message }
			out += "chat ${OursGolden.text(m)} $outcome -> ${OursGolden.text(event.replaceWith)}"
		}
		options.forEachIndexed { i, o -> o._actualValue = previous[i] }
		out += "cache ${ChatLinks.imageCache.keys.sorted()}"
		OursGolden.check("chat-links", out)
	}

	private val feedback = mutableListOf<String>()

	private val source: DefaultSource = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DefaultSource::class.java)) { _, m, args ->
		when (m.name) {
			"sendFeedback" -> { feedback += "fb " + OursGolden.text(args[0] as Component); null }
			"sendError" -> { feedback += "err " + OursGolden.text(args[0] as Component); null }
			"hashCode" -> 1
			"equals" -> false
			"toString" -> "src"
			else -> null
		}
	} as DefaultSource

	@Test
	fun floorNamesResolveTheSame() {
		val out = mutableListOf<String>()
		val prefixes = listOf("", "f", "fl", "floor", "floorx", "kuudratier", "tier", "xfloor")
		for (t in listOf("", "f", "f7", "floor7", "fl3", "t5", "tier2", "kuudratier", "kuudra", "m", "x")) for (p in prefixes)
			out += "partial [$t] [$p] ${QuickCommands.removePartialPrefix(t, p)}"
		out += "names ${QuickCommands.kuudraLevelNames} ${QuickCommands.dungeonLevelNames} server=${QuickCommands.serverJoinCommand("f7 now")} tree=${QuickCommands.lastReceivedTreePacket}"
		val ctx = CommandContextBuilder<DefaultSource>(null, source, null, 0).build("")
		val names = listOf("f7", "f0", "f8", "m3", "m7", "m8", "m0", "e", "entrance", "f", "floor", "floorseven", "fs", "floorfive", "fi", "ft", "m", "master", "masterone", "mt", "t1", "t5", "t6", "t0", "th", "tb", "tf", "ti", "tier", "kuudratier3", "kuudrat", "kuudratiernormal", "k", "catacombs4", "c", "d", "dungeons2", "dungeons", "x", "", "7", "fl1", "fe", "floorentrance", "m e", "tierhot", "tierz", "f-1", "f 7", "master7x", "floor10")
		for (n in names) {
			feedback.clear()
			val r = with(QuickCommands) { ctx.getNameForFloor(n) }
			out += "floor [$n] -> $r $feedback"
		}
		OursGolden.check("chat-floors", out)
	}

	@Test
	fun partyChatParsesTheSame() {
		val out = mutableListOf<String>()
		for (line in listOf("Party > [MVP+] Name: hi", "P > Name: yo", "Guild > Name [Officer]: x", "From [VIP] Abc: m", "Co-op > Def: n", "Party > Name:", "random", "Party > [302] ♲ [MVP++] Name ⚒: !w"))
			out += "parse [$line] ${PartyCommands.parse(line)}"
		for (m in listOf("hi <3", "/pc o/ ez", "/warp ez", "/w Name :shrug:", "plain", "ez", "/r", ""))
			out += "emote [$m] ${PartyCommands.replaceEmotes(m)}"
		out += "copy ${CopyChat.orderedTextToString(Component.literal("ab").append(Component.literal("cd").withStyle(ChatFormatting.BLUE)).visualOrderText)}"
		OursGolden.check("chat-party", out)
	}
}
