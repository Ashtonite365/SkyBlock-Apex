// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.plus.chat.CompactExperiments
import dev.ashtonite365.apex.features.plus.chat.PlusChatCompact
import dev.ashtonite365.apex.test.golden.Harness
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch48GoldenTest {
	companion object {
		private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
		private val titleField = Screen::class.java.getDeclaredField("title").also { it.isAccessible = true }

		fun Titled(t: String): Screen = (unsafe.allocateInstance(net.minecraft.client.gui.screens.inventory.ContainerScreen::class.java) as Screen).also { titleField.set(it, Component.literal(t)) }
	}

	@Test
	fun chat() {
		val S = Batch48Chat
		val P = PlusChatCompact
		val E = CompactExperiments
		val cfg = PlusChatCompact.TConfig
		val out = mutableListOf<Pair<String, String>>()

		Harness.withAll(cfg, true) {
			Harness.at(Harness.skyblock("hub")) {
				Harness.chat {
					repeat(17) { P.onAllow(AllowChatEvent(Component.literal("x"))) }
					E.onJacob(AllowChatEvent(Component.literal("▬".repeat(21)))); E.onStash(AllowChatEvent(Component.literal("x")))
					E.onScreen(ScreenChangeEvent(null, Titled("Experimentation Table"))); E.onScreen(ScreenChangeEvent(null, null))
					E.onAllow(AllowChatEvent(Component.literal("You claimed the X rewards!"))); E.onAllow(AllowChatEvent(Component.literal("+x")))
					E.onTick(TickEvent(0)); E.onTick(TickEvent(100)); E.onScreen(ScreenChangeEvent(null, null))
				}
			}
		}
		for (n in S.numbers) out += "num|$n" to Golden.run { P.shortenNumbers(n) }
		for (p in S.potions) out += "potion|$p" to Golden.run { "${P.compactPotion(p)}" }
		P.darkAuction("[NPC] Sirius: No one made any bids!")
		for ((i, s) in S.daSeqs.withIndex()) out += "da|$i" to s.map { P.darkAuction(it) }.toString()
		P.darkAuction("[NPC] Sirius: No one made any bids!")
		val sb = Harness.skyblock("hub")
		for (loc in listOf(sb, null)) for (mask in 0 until 8) {
			Harness.withOpts(cfg, mapOf("dark-auction-item" to (mask and 1 != 0), "compact-potions" to (mask and 2 != 0), "shorten-coins" to (mask and 4 != 0))) {
				Harness.at(loc) {
					for ((i, parts) in S.styled.withIndex()) {
						val msg = Component.empty()
						for ((t, c) in parts) msg.append(Component.literal(t).let { if (c == null) it else it.withStyle(ChatFormatting.getByCode(c)!!) })
						val ev = ModifyChatEvent(msg)
						val res = Golden.run { P.onModify(ev); "ok" }
						out += "modify|${loc != null}|$mask|$i" to "$res ${ev.replaceWith === msg} ${Harness.dump(ev.replaceWith)} ${ev.bareText}"
					}
				}
			}
		}
		P.darkAuction("[NPC] Sirius: No one made any bids!")
		for ((i, s) in S.bestiarySeqs.withIndex()) {
			val c = PlusChatCompact.BestiaryCompactor()
			out += "best|$i" to s.joinToString(" ; ") { "${c.feed(it)} ${c.active} ${c.milestone}" }
		}
		for (on in listOf(true, false)) for (loc in listOf(sb, null)) Harness.withOpts(cfg, mapOf("compact-bestiary" to on)) {
			Harness.at(loc) {
				for ((i, s) in S.bestiarySeqs.withIndex()) {
					val evs = mutableListOf<Boolean>()
					val chat = Harness.chat { for (l in s) evs += AllowChatEvent(Component.literal(l)).also { P.onAllow(it) }.cancelled }
					out += "bestEv|$on|${loc != null}|$i" to "$evs $chat"
				}
			}
		}
		for ((i, s) in S.jacobSeqs.withIndex()) {
			val c = CompactExperiments.JacobCompactor()
			out += "jacob|$i" to s.joinToString(" ; ") { "${c.feed(it)}" }
		}
		for ((i, s) in S.stashSeqs.withIndex()) {
			val c = CompactExperiments.StashCompactor()
			out += "stash|$i" to s.joinToString(" ; ") { "${c.feed(it)}" }
		}
		for (on in listOf(true, false)) Harness.withOpts(cfg, mapOf("compact-jacob" to on, "compact-stash" to on)) {
			for ((i, s) in (S.jacobSeqs + S.stashSeqs).withIndex()) {
				val evs = mutableListOf<String>()
				val chat = Harness.chat {
					for (l in s) {
						val a = AllowChatEvent(Component.literal(l)); E.onJacob(a)
						val b = AllowChatEvent(Component.literal(l)); E.onStash(b)
						evs += "${a.cancelled}/${b.cancelled}"
					}
				}
				out += "jsEv|$on|$i" to "$evs $chat"
			}
		}
		for (t in S.titles) out += "title|$t" to "${t?.let { E.isExperimentInventory(it) }}"
		for (l in S.expPool) out += "exp|$l" to "${E.isClaim(l)} ${E.reward(l)}"
		for (on in listOf(true, false)) Harness.withOpts(cfg, mapOf("compact-experiments" to on)) {
			var tick = 0
			var screen: Screen? = null
			for ((i, step) in S.expSteps.withIndex()) {
				val (title, lines, adv) = step
				if (title != "-") {
					val next = title?.let { Titled(it) }
					E.onScreen(ScreenChangeEvent(screen, next)); screen = next
				}
				val evs = mutableListOf<Boolean>()
				val chat = Harness.chat {
					for (l in lines) evs += AllowChatEvent(Component.literal(l)).also { E.onAllow(it) }.cancelled
					repeat(adv) { E.onTick(TickEvent(++tick)) }
				}
				out += "expEv|$on|$i" to "$evs $chat"
			}
			repeat(10) { E.onTick(TickEvent(++tick)) }
			E.onScreen(ScreenChangeEvent(screen, null)); E.onScreen(ScreenChangeEvent(null, null))
		}
		val n = Golden.verify("batch48-chat", out, minCases = 7919)
		assertTrue(n >= 7919)
	}
}
