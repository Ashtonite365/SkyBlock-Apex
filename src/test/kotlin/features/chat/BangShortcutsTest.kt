// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.chat

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.chat.BangShortcuts
import dev.ashtonite365.apex.features.chat.BangShortcuts.Prompt
import dev.ashtonite365.apex.features.chat.BangShortcuts.Shortcut
import dev.ashtonite365.apex.features.chat.PartyCommands
import net.minecraft.network.chat.ClickEvent
import dev.ashtonite365.apex.gui.config.ManagedOption

class BangShortcutsTest {
	private val cfg = BangShortcuts.TConfig
	private lateinit var saved: Map<String, Any?>

	private object FakeEnv : BangShortcuts.Env {
		override fun coords() = "x: 10, y: 70, z: -5"
		override fun fps() = 144
		override fun location() = "Dungeon Hub"
		override fun holding() = "Hyperion"
		override fun now(): ZonedDateTime = ZonedDateTime.of(2026, 10, 29, 1, 30, 0, 0, ZoneId.of("Australia/Sydney"))
		override val random: Random = Random(7)
		override val selfName = "Ashtonite365"
		var clock = 1_000_000L
		override fun millis() = clock
	}

	@Suppress("UNCHECKED_CAST")
	private fun set(id: String, on: Boolean) = (cfg.allOptions[id] as ManagedOption<Boolean>).set(on)

	@BeforeEach fun setUp() {
		saved = cfg.allOptions.mapValues { it.value._actualValue }
		for (id in cfg.allOptions.keys) set(id, true)
		BangShortcuts.env = FakeEnv
		BangShortcuts.clearReminders()
		BangShortcuts.PromptLimit.reset()
	}

	@Suppress("UNCHECKED_CAST")
	@AfterEach fun tearDown() {
		for ((id, v) in saved) (cfg.allOptions[id] as ManagedOption<Any>)._actualValue = v
		BangShortcuts.env = BangShortcuts.GameEnv
		BangShortcuts.clearReminders()
		BangShortcuts.PromptLimit.reset()
	}

	private data class Typed(val allowed: Boolean, val outgoing: String?, val sentCommands: List<String>, val shown: List<String>)

	private fun type(line: String): Typed {
		val sent = mutableListOf<String>()
		val shown = mutableListOf<String>()
		return if (line.startsWith("/")) {
			val cmd = line.drop(1)
			val allowed = BangShortcuts.onTypedCommand(cmd) { shown += it }
			Typed(allowed, if (allowed) "/" + BangShortcuts.modified(cmd) else null, sent, shown)
		} else {
			val allowed = BangShortcuts.onTypedChat(line, { sent += it }, { shown += it })
			Typed(allowed, if (allowed) BangShortcuts.modified(line) else null, sent, shown)
		}
	}

	private fun assertChatCommand(typed: String, command: String) {
		val r = type(typed)
		assertEquals(Typed(false, null, listOf(command), emptyList()), r, typed)
	}

	private fun assertPcCommand(typed: String, command: String) {
		val r = type("/pc $typed")
		assertEquals(Typed(true, "/$command", emptyList(), emptyList()), r, "/pc $typed")
	}

	private fun assertUnchanged(line: String) {
		val r = type(line)
		assertEquals(Typed(true, line, emptyList(), emptyList()), r, line)
	}

	private val partyRewrites = buildList {
		for (a in listOf("warp", "w", "pw", "pwarp", "partywarp")) add("!$a" to "p warp")
		for (a in listOf("allinvite", "allinv")) add("!$a" to "p settings allinvite")
		for (a in listOf("pt", "ptme", "transfer")) add("!$a Foo_1" to "p transfer Foo_1")
		add("!promote Bar" to "p promote Bar")
		add("!demote Bar" to "p demote Bar")
		for (a in listOf("kick", "k")) add("!$a Baz" to "p kick Baz")
		for (a in listOf("kickoffline", "ko")) add("!$a" to "p kickoffline")
		for (a in listOf("invite", "inv")) add("!$a Qux" to "p invite Qux")
		for (a in listOf("reinvite", "reinv")) add("!$a Qux" to "p invite Qux")
		val floors = listOf("ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN")
		floors.forEachIndexed { i, f ->
			add("!f${i + 1}" to "joininstance CATACOMBS_FLOOR_$f")
			add("!m${i + 1}" to "joininstance MASTER_CATACOMBS_FLOOR_$f")
		}
		listOf("NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL").forEachIndexed { i, t -> add("!t${i + 1}" to "joininstance KUUDRA_$t") }
	}

	@Test fun everyPartyAliasFromChat() {
		assertEquals(39, partyRewrites.size)
		for ((typed, command) in partyRewrites) assertChatCommand(typed, command)
	}

	@Test fun everyPartyAliasFromPartyChatCommand() {
		for ((typed, command) in partyRewrites) assertPcCommand(typed, command)
	}

	@Test fun caseAndSpacing() {
		assertChatCommand("!WARP", "p warp")
		assertChatCommand("  !Kick   FooBar  ", "p kick FooBar")
		assertChatCommand("!F7", "joininstance CATACOMBS_FLOOR_SEVEN")
		assertPcCommand("!ko", "p kickoffline")
	}

	private val infoLines = mapOf(
		"!coords" to "x: 10, y: 70, z: -5", "!co" to "x: 10, y: 70, z: -5", "!fps" to "FPS: 144",
		"!location" to "Location: Dungeon Hub", "!holding" to "Holding: Hyperion",
		"!time" to "Time: 2026-10-29 01:30:00 AEDT",
	)

	@Test fun infoShortcutsReplaceTheLineInTheSameChannel() {
		for ((typed, line) in infoLines) {
			assertEquals(Typed(true, line, emptyList(), emptyList()), type(typed), typed)
			for (prefix in listOf("pc", "gc", "ac", "cc", "r", "msg Foo", "w Foo"))
				assertEquals(Typed(true, "/$prefix $line", emptyList(), emptyList()), type("/$prefix $typed"), "/$prefix $typed")
		}
	}

	@Test fun randomShortcuts() {
		val patterns = mapOf(
			"!cf" to Regex("Coin flip: (heads|tails)"), "!dice" to Regex("Rolled a [1-6]"), "!roll" to Regex("Rolled a [1-6]"),
			"!8ball" to Regex("8-ball: .+"), "!8ball will it drop?" to Regex("8-ball: .+"),
		)
		repeat(20) {
			for ((typed, p) in patterns) {
				val r = type(typed)
				assertTrue(r.allowed && r.sentCommands.isEmpty() && r.shown.isEmpty(), typed)
				assertTrue(p.matches(r.outgoing!!), "$typed -> ${r.outgoing}")
				val pc = type("/pc $typed").outgoing!!
				assertTrue(pc.startsWith("/pc ") && p.matches(pc.removePrefix("/pc ")), pc)
			}
		}
		assertFalse(type("!8ball").outgoing!!.contains("!"))
	}

	@Test fun localOnlyShortcutsSendNothing() {
		for (line in listOf("!help", "/pc !help", "/gc !help")) {
			val r = type(line)
			assertFalse(r.allowed, line); assertEquals(emptyList<String>(), r.sentCommands)
			val help = r.shown.single()
			for (s in Shortcut.entries) assertTrue(help.contains(s.label), "help lists ${s.label}")
		}
		val dt = type("!dt need food")
		assertEquals(Typed(false, null, emptyList(), dt.shown), dt); assertEquals(1, dt.shown.size)
		assertEquals("need food", BangShortcuts.downtimeReason)
		assertEquals("no reason given", type("!downtime").let { BangShortcuts.downtimeReason })
		val undt = type("/pc !undt")
		assertFalse(undt.allowed); assertEquals(emptyList<String>(), undt.sentCommands)
		assertNull(BangShortcuts.downtimeReason)
		assertTrue(type("!undowntime").shown.single().startsWith("No downtime"))
	}

	@Test fun nonMatchingLinesPassThrough() {
		for (line in listOf(
			"hello", "warp", "! warp", "!", "!!", "!unknown", "!warp now", "!kick", "!kick a b", "!kick not-a-name",
			"!kick ThisNameIsWayTooLong", "!pt", "!f8", "!m0", "!t6", "!fps please", "!help me", "!coordsx", "gg !warp",
			"/warp", "/p warp", "/pc hello", "/pc", "/msg Foo", "/msg Foo hi", "/warp !coords", "/p !warp", "/gc !warp",
			"/msg Foo !kick Bar", "/r !f7", "/cc !allinvite", "/joininstance !f7",
		)) assertUnchanged(line)
	}

	@Test fun masterToggleOffPassesEverythingThrough() {
		set("enabled", false)
		for ((typed, _) in partyRewrites) { assertUnchanged(typed); assertUnchanged("/pc $typed") }
		for (typed in infoLines.keys + listOf("!cf", "!dice", "!8ball", "!help", "!dt x", "!undt")) assertUnchanged(typed)
		assertNull(BangShortcuts.downtimeReason)
	}

	@Test fun eachToggleOffPassesOnlyItsShortcutsThrough() {
		val sample = mapOf(
			"warp" to "!pw", "allinvite" to "!allinv", "transfer" to "!pt Foo", "promote" to "!promote Foo",
			"demote" to "!demote Foo", "kick" to "!k Foo", "kickoffline" to "!ko", "invite" to "!inv Foo",
			"reinvite" to "!reinv Foo", "instances" to "!m7", "coords" to "!co", "fps" to "!fps", "location" to "!location",
			"holding" to "!holding", "time" to "!time", "coinflip" to "!cf", "dice" to "!roll", "eightball" to "!8ball",
			"downtime" to "!dt", "help" to "!help",
		)
		assertEquals(cfg.allOptions.keys - setOf("enabled", "others-prompts", "dm-invite-prompt"), sample.keys)
		for ((id, typed) in sample) {
			set(id, false)
			assertUnchanged(typed); assertUnchanged("/pc $typed")
			for ((otherId, other) in sample) if (otherId != id) assertTrue(type(other) != Typed(true, other, emptyList(), emptyList()), "$other with $id off")
			BangShortcuts.clearReminders()
			set(id, true)
		}
	}

	@Test fun defaults() {
		val fresh = cfg.allOptions.mapValues { (it.value as ManagedOption<*>).default() }
		val offByDefault = setOf("enabled", "others-prompts", "dm-invite-prompt")
		for (id in offByDefault) assertEquals(false, fresh[id], id)
		for ((id, v) in fresh) if (id !in offByDefault) assertEquals(true, v, id)
		assertEquals(23, fresh.size)
	}

	@Test fun nothingIsSentWithoutATypedMessage() {
		set("others-prompts", false); set("dm-invite-prompt", false)
		type("!dt food")
		for (line in listOf("Party > [MVP+] Foo: !warp", "Party > Foo: !kick Ashton", "Guild > Bar [Member]: !coords",
			"From Baz: !inv", "Party > Foo: !f7", "                             > EXTRA STATS <")) {
			BangShortcuts.onChat(ProcessChatEvent(Component.literal(line), false))
		}
		assertNull(BangShortcuts.downtimeReason)
		assertTrue(BangShortcuts.isRunEnd("KUUDRA DOWN!"))

		assertEquals(Typed(true, "FPS: 144", emptyList(), emptyList()), type("!fps"))
		BangShortcuts.onTypedChat("!fps", { error("no send") }, { error("no show") })
		assertEquals("hello", BangShortcuts.modified("hello"))
		assertEquals("pc hi", BangShortcuts.modified("pc hi"))

		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/chat/BangShortcuts.kt").readText()
		val sends = Regex("""runCommand\(|sendCommand\(|sendChat\(|chatAsPlayer|runCommandUnchecked|ServerboundChat""").findAll(src).toList()
		assertEquals(1, sends.size, sends.map { it.value }.toString())
		val sendLine = src.lines().single { "runCommand(" in it }
		assertTrue(sendLine.trim().startsWith("ClientSendMessageEvents.ALLOW_CHAT.register"), sendLine)
		for (bad in listOf("TickEvent", "onNextTick", "schedule(", "Thread", "Timer(", "delay(", "PartyMessageReceivedEvent", "sendUnattended"))
			assertFalse(src.contains(bad), bad)
		val onChatBody = src.substringAfter("fun onChat(").substringBefore("\n\t}")
		assertFalse(onChatBody.contains("Command") || onChatBody.contains("send"), onChatBody)
	}

	private fun party(name: String, msg: String) = BangShortcuts.promptFor(PartyCommands.Incoming(PartyCommands.Channel.PARTY, name, msg))
	private fun dm(name: String, msg: String) = BangShortcuts.promptFor(PartyCommands.Incoming(PartyCommands.Channel.PRIVATE, name, msg))

	private fun clickCommand(prompt: Prompt): String {
		val click = BangShortcuts.promptLine(prompt).style.clickEvent
		assertTrue(click is ClickEvent.RunCommand, "$click")
		return (click as ClickEvent.RunCommand).command()
	}

	private val othersExpected = buildMap {
		for (a in listOf("warp", "w", "pw", "pwarp", "partywarp")) put("!$a", "/p warp")
		for (a in listOf("allinvite", "allinv")) put("!$a", "/p settings allinvite")
		for (a in listOf("pt", "ptme", "transfer")) { put("!$a", "/p transfer Foo"); put("!$a Bob", "/p transfer Bob") }
		for (a in listOf("kick", "k")) put("!$a Bob", "/p kick Bob")
		for (a in listOf("kickoffline", "ko")) put("!$a", "/p kickoffline")
		put("!promote", "/p promote Foo"); put("!promote Bob", "/p promote Bob")
		put("!demote", "/p demote Foo"); put("!demote Bob", "/p demote Bob")
		for (a in listOf("reinvite", "reinv")) { put("!$a", "/p invite Foo"); put("!$a Bob", "/p invite Bob") }
		for ((typed, command) in partyRewrites) if (typed.startsWith("!f") || typed.startsWith("!m") || typed.startsWith("!t")) put(typed, "/$command")
		put("!dt", "/apex dtreminder Foo no reason given"); put("!downtime food run", "/apex dtreminder Foo food run")
		put("!coords", "/pc x: 10, y: 70, z: -5"); put("!co", "/pc x: 10, y: 70, z: -5"); put("!fps", "/pc FPS: 144")
		put("!location", "/pc Location: Dungeon Hub"); put("!holding", "/pc Holding: Hyperion")
		put("!time", "/pc Time: 2026-10-29 01:30:00 AEDT")
		put("!boop Bob", "/boop Bob")
	}

	@Test fun othersPartyCommandsBecomeOneClickPrompts() {
		for ((typed, command) in othersExpected) {
			BangShortcuts.PromptLimit.reset()
			val prompt = party("Foo", typed) ?: error("no prompt for $typed")
			assertEquals(command, prompt.command, typed)
			assertEquals(command, clickCommand(prompt), typed)
			assertTrue(prompt.text.startsWith("Foo asked: $typed, click to"), prompt.text)
		}
		assertEquals("Foo asked: !kick Bob, click to run /p kick Bob", party("Foo", "!kick Bob")!!.text)
		for ((typed, p) in mapOf("!cf" to Regex("/pc Coin flip: (heads|tails)"), "!dice" to Regex("/pc Rolled a [1-6]"),
				"!roll" to Regex("/pc Rolled a [1-6]"), "!8ball" to Regex("/pc 8-ball: .+"), "!8ball am I lucky" to Regex("/pc 8-ball: .+")))
			assertTrue(p.matches(party("Foo", typed)!!.command), typed)
	}

	@Test fun nothingIsSentWithoutAClickAndOneClickSendsOne() {
		val sent = mutableListOf<String>()
		for ((typed, _) in othersExpected) {
			BangShortcuts.PromptLimit.reset()

			val line = BangShortcuts.promptLineFor("Party > [MVP+] Foo: $typed") ?: error("no line for $typed")
			assertEquals(emptyList<String>(), sent)

			val command = (line.style.clickEvent as ClickEvent.RunCommand).command()
			assertFalse(command.contains('\n'))
			val cmd = command.drop(1)
			assertTrue(BangShortcuts.onTypedCommand(cmd) { error("click must not be swallowed: $it") }, cmd)
			assertEquals(cmd, BangShortcuts.modified(cmd))
		}
		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/chat/BangShortcuts.kt").readText()
		val promptPart = src.substringAfter("data class Prompt(")
		for (bad in listOf("runCommand(", "sendCommand(", "sendChat(", "chatAsPlayer", "sendUnattended", "connection"))
			assertFalse(promptPart.contains(bad), bad)

		BangShortcuts.addReminder("Foo food")
		assertEquals(mapOf("Foo" to "food"), BangShortcuts.downtimeReminders)
	}

	@Test fun othersParsingMatchesTheTypedShortcuts() {
		for (s in BangShortcuts.othersShortcuts) for (name in s.names) {
			val typed = when (s.args) { BangShortcuts.Args.NAME -> "!$name Bob"; else -> "!$name" }
			BangShortcuts.PromptLimit.reset()
			assertTrue(BangShortcuts.matchChat(typed) != null, typed)
			assertTrue(party("Foo", typed) != null, typed)
			BangShortcuts.PromptLimit.reset()
			assertEquals(BangShortcuts.matchChat("$typed extra") != null, party("Foo", "$typed extra") != null, "$typed extra")
		}
	}

	@Test fun unknownOrOffOthersLinesAreIgnored() {
		for (typed in listOf("!help", "!undt", "!undowntime", "!inv Bob", "!invite", "!racism", "!foo", "hello", "warp",
				"!warp now", "!kick", "!kick bad-name!", "!f8", "!t0", "!boop", "!fps please", "gg !warp"))
			assertNull(party("Foo", typed), typed)
		assertNull(party("Ashtonite365", "!warp"))
		assertNull(BangShortcuts.promptFor(PartyCommands.Incoming(PartyCommands.Channel.GUILD, "Foo", "!warp")))
		assertNull(BangShortcuts.promptFor(PartyCommands.Incoming(PartyCommands.Channel.COOP, "Foo", "!coords")))
		set("kick", false); assertNull(party("Foo", "!kick Bob")); assertTrue(party("Foo2", "!warp") != null); set("kick", true)
		set("others-prompts", false); assertNull(party("Foo3", "!warp")); assertTrue(dm("Foo3", "!inv") != null); set("others-prompts", true)
		set("enabled", false); assertNull(party("Foo4", "!warp")); assertNull(dm("Foo4", "!invite"))
	}

	@Test fun dmInvitePrompt() {
		val p = dm("Foo", "!invite")!!
		assertEquals(Prompt("Foo", "!invite", "/p invite Foo", "Invite Foo to party"), p)
		assertEquals("/p invite Foo", clickCommand(p))
		BangShortcuts.PromptLimit.reset()
		assertEquals("/p invite Bar", dm("Bar", " !INV ")!!.command)
		BangShortcuts.PromptLimit.reset()
		val viaLine = BangShortcuts.promptLineFor("From [MVP+] Baz: !inv")!!
		assertEquals("/p invite Baz", (viaLine.style.clickEvent as ClickEvent.RunCommand).command())
		for (other in listOf("!warp", "!inv Bob", "!coords", "invite me", "!invitee")) assertNull(dm("Foo", other), other)
		assertNull(BangShortcuts.promptLineFor("To Foo: !inv"))
		set("dm-invite-prompt", false); assertNull(dm("Foo", "!invite"))
	}

	@Test fun promptsAreRateLimited() {
		val line = "Party > Foo: !warp"
		assertTrue(BangShortcuts.promptLineFor(line) != null)
		assertNull(BangShortcuts.promptLineFor(line))
		FakeEnv.clock += 3_000
		assertTrue(BangShortcuts.promptLineFor(line) != null)
		FakeEnv.clock += 10_000
		val shown = (1..6).count { BangShortcuts.promptLineFor("Party > P$it: !coords") != null }
		assertEquals(BangShortcuts.PromptLimit.BURST, shown)
		FakeEnv.clock += 10_000
		assertTrue(BangShortcuts.promptLineFor("Party > P9: !coords") != null)
	}
}
