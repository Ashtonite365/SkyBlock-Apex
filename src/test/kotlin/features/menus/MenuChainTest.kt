// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.menus.MenuChain.Action
import dev.ashtonite365.apex.features.menus.MenuChain.Label
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.features.menus.MenuSettings

class MenuChainTest {
	private fun chain(delay: Long = 7, finish: MenuChain.Finish = MenuChain.Finish.AfterLastClick) = MenuChain(listOf(
		MenuChain.Step(Regex("^Bazaar ➜ \""), MenuChain.Target("Seeds"), optional = true),
		MenuChain.Step(Regex("➜ Seeds$"), MenuChain.Target("Buy Instantly", 10)),
		MenuChain.Step(Regex("Instant Buy$"), MenuChain.Target("Buy only one!", 10)),
	), finish, timeoutMs = 5_000, startMs = 0, delay = { delay })

	private val product = listOf(Label(10, "Buy Instantly"), Label(11, "Sell Instantly"), Label(49, "Go Back"))
	private val instant = listOf(Label(10, "Buy only one!"), Label(12, "Buy a stack!"))

	private fun send(c: MenuChain, a: Action, openNow: Int, now: Long, sent: MutableList<Triple<Int, Int, Int>>) {
		val click = a as Action.Click
		if (c.fire(openNow, now)) sent += Triple(openNow, click.slot, click.button)
	}

	@Test
	fun happyPathOnePacketPerStepWithButton0() {
		val c = chain(finish = MenuChain.Finish.Confirmed(Regex("^\\[Bazaar\\] Bought"), Regex("Instant Buy$")))
		val sent = mutableListOf<Triple<Int, Int, Int>>()
		c.begin(1, "Bazaar ➜ Farming", 0, requireNewWindow = true)
		Assertions.assertEquals(Action.Wait, c.onContents(1, product, 10))
		Assertions.assertEquals(Action.Wait, c.onOpen(2, "Bazaar ➜ \"Seeds\"", 100))
		send(c, c.onContents(2, listOf(Label(11, "Seeds"), Label(12, "Wheat")), 150), 2, 160, sent)
		Assertions.assertEquals(Action.Wait, c.onOpen(3, "Farming ➜ Seeds", 300))
		send(c, c.onContents(3, product, 350), 3, 360, sent)
		Assertions.assertEquals(Action.Wait, c.onOpen(4, "Seeds ➜ Instant Buy", 500))
		send(c, c.onContents(4, instant, 550), 4, 560, sent)
		Assertions.assertEquals(listOf(Triple(2, 11, 0), Triple(3, 10, 0), Triple(4, 10, 0)), sent)
		Assertions.assertEquals(3, c.clicks)
		Assertions.assertNull(c.finished)
		Assertions.assertEquals(Action.Done, c.onChat("[Bazaar] Bought 1x Seeds for 5 coins!"))
	}

	@Test
	fun optionalSearchSkippedAndReopenConfirms() {
		val c = chain(finish = MenuChain.Finish.Confirmed(Regex("^\\[Bazaar\\] Bought"), Regex("Instant Buy$")))
		val sent = mutableListOf<Triple<Int, Int, Int>>()
		c.begin(null, null, 0)
		c.onOpen(3, "Farming ➜ Seeds", 10)
		send(c, c.onContents(3, product, 20), 3, 30, sent)
		c.onOpen(4, "Seeds ➜ Instant Buy", 40)
		send(c, c.onContents(4, instant, 50), 4, 60, sent)
		Assertions.assertEquals(Action.Wait, c.onOpen(4, "Seeds ➜ Instant Buy", 70))
		Assertions.assertEquals(Action.Done, c.onOpen(5, "Seeds ➜ Instant Buy", 80))
	}

	@Test
	fun staleIdIgnoredAfterDoubleOpen() {
		val c = chain()
		val sent = mutableListOf<Triple<Int, Int, Int>>()
		c.begin(null, null, 0)
		c.onOpen(7, "Farming ➜ Seeds", 10)
		c.onOpen(8, "Farming ➜ Seeds", 12)
		Assertions.assertEquals(Action.Wait, c.onContents(7, product, 20))
		val a = c.onContents(8, product, 400)
		Assertions.assertEquals(8, (a as Action.Click).containerId)

		c.onOpen(9, "Farming ➜ Seeds", 405)
		send(c, a, 9, 410, sent)
		Assertions.assertTrue(sent.isEmpty()); Assertions.assertEquals(0, c.clicks)

		send(c, c.onContents(9, product, 800), 9, 810, sent)
		Assertions.assertEquals(listOf(Triple(9, 10, 0)), sent)
	}

	@Test
	fun waitsForContentsBeforeClicking() {
		val c = chain()
		c.begin(null, null, 0)
		c.onOpen(3, "Farming ➜ Seeds", 10)
		Assertions.assertEquals(Action.Wait, c.onContents(3, listOf(Label(49, "Go Back")), 20))
		Assertions.assertEquals(Action.Wait, c.onTick(1_000))
		Assertions.assertTrue(c.onContents(3, product, 1_100) is Action.Click)

		val d = chain(); d.begin(null, null, 0); d.onOpen(3, "Farming ➜ Seeds", 1)
		Assertions.assertTrue(d.onContents(3, product + Label(12, "Buy Instantly"), 2) is Action.Abort)
	}

	@Test
	fun mismatchAborts() {
		val c = chain()
		c.begin(null, null, 0)
		Assertions.assertTrue(c.onOpen(3, "Bazaar ➜ Farming", 10) is Action.Abort)
		val d = chain(); d.begin(null, null, 0); d.onOpen(3, "Farming ➜ Seeds", 1)
		Assertions.assertTrue(d.onContents(3, listOf(Label(11, "Buy Instantly")), 2) is Action.Abort)
		Assertions.assertEquals(0, d.clicks)
	}

	@Test
	fun timeoutAborts() {
		val c = chain()
		c.begin(null, null, 0)
		Assertions.assertEquals(Action.Wait, c.onTick(5_000))
		val a = c.onTick(5_001)
		Assertions.assertTrue(a is Action.Abort && a.reason.contains("timed out"))

		val d = chain(finish = MenuChain.Finish.Confirmed(Regex("Bought"))); d.begin(null, null, 0)
		d.onOpen(3, "Farming ➜ Seeds", 0); d.fire(3, 0)
		d.onContents(3, product, 1); d.fire(3, 1)
		d.onOpen(4, "Seeds ➜ Instant Buy", 2); d.onContents(4, instant, 3); d.fire(4, 3)
		Assertions.assertEquals("Not confirmed by Hypixel", (d.onTick(5_004) as Action.Abort).reason)
	}

	@Test
	fun delayWithin100to200() {
		Assertions.assertEquals(200L, (chain(delay = 999).also { it.begin(null, null, 0); it.onOpen(3, "Farming ➜ Seeds", 0) }.onContents(3, product, 1) as Action.Click).delayMs)
		val real = MenuChain(listOf(MenuChain.Step(Regex("x"), MenuChain.Target("a"))))
		repeat(100) {
			val r = MenuChain(real.steps).also { it.begin(1, "x", 0) }
			val d = (r.onContents(1, listOf(Label(0, "a")), 0) as Action.Click).delayMs
			Assertions.assertTrue(d in 100L..200L)
		}
		Assertions.assertEquals(100..200, ApexClickDelay.RANGE_MS)
	}

	@Test
	fun silentOptionRemovedChainsAlwaysCovered() {
		MenuSettings.Global.explicitDefaultLoad()

		Assertions.assertNull(MenuSettings.Global.allOptions["automation"])

		Assertions.assertNull(MenuSettings.Global.allOptions["silent-auto-click"])

		Assertions.assertFalse(dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/menus/SilentClick.kt").exists())
	}

	private fun textChain() = MenuChain(listOf(
		MenuChain.Step(Regex("^How many do you want\\?$"), MenuChain.Target("Custom Amount", 16)),
		MenuChain.Step.text("500"),
		MenuChain.Step(Regex("^How much do you want to pay\\?$"), MenuChain.Target("Same as Top Order", 10)),
	), MenuChain.Finish.AfterLastClick, timeoutMs = 5_000, startMs = 0, delay = { 9 })

	@Test
	fun signStepWritesValueOnTheLatestEntryOnly() {
		val c = textChain(); c.begin(null, null, 0)
		c.onOpen(4, "How many do you want?", 10)
		val click = c.onContents(4, listOf(Label(16, "Custom Amount")), 20) as Action.Click
		Assertions.assertTrue(c.fire(4, 30))

		val type = c.onOpen(-2, "Sign", 40, textEntry = true) as Action.Type
		Assertions.assertEquals("500", type.value); Assertions.assertEquals(100L, type.delayMs)
		Assertions.assertEquals(16, click.slot)

		val again = c.onOpen(-3, "Sign", 45, textEntry = true) as Action.Type
		Assertions.assertFalse(c.fire(-2, 50))
		Assertions.assertTrue(c.fire(-3, 55))
		Assertions.assertEquals(again.value, "500")
		Assertions.assertEquals(2, c.clicks)

		c.onOpen(5, "How much do you want to pay?", 60)
		Assertions.assertTrue(c.onContents(5, listOf(Label(10, "Same as Top Order")), 70) is Action.Click)
		Assertions.assertTrue(c.fire(5, 80)); Assertions.assertEquals(Action.Done, c.finished)
	}

	@Test
	fun anvilCountsAsTextEntry() {
		val c = textChain(); c.begin(null, null, 0)
		c.onOpen(4, "How many do you want?", 10); c.onContents(4, listOf(Label(16, "Custom Amount")), 20); c.fire(4, 30)
		Assertions.assertTrue(c.onOpen(7, "Enter amount", 40, textEntry = true) is Action.Type)
		Assertions.assertTrue(c.fire(7, 50))
	}

	@Test
	fun signStepAborts() {

		val c = textChain(); c.begin(null, null, 0)
		c.onOpen(4, "How many do you want?", 10); c.onContents(4, listOf(Label(16, "Custom Amount")), 20); c.fire(4, 30)
		Assertions.assertTrue(c.onOpen(5, "How many do you want?", 40) is Action.Abort)

		val u = textChain(); u.begin(null, null, 0)
		Assertions.assertTrue(u.onOpen(-2, "Sign", 1, textEntry = true) is Action.Abort)

		val b = MenuChain(listOf(MenuChain.Step.text(" ")), delay = { 0 }); b.begin(null, null, 0)
		Assertions.assertTrue(b.onOpen(-2, "Sign", 1, textEntry = true) is Action.Abort)

		val t = textChain(); t.begin(null, null, 0)
		t.onOpen(4, "How many do you want?", 10); t.onContents(4, listOf(Label(16, "Custom Amount")), 20); t.fire(4, 30)
		Assertions.assertTrue((t.onTick(5_031) as Action.Abort).reason.contains("text entry"))
	}

	@Test
	fun everyChainKeepsApexInFront() {

		val start = dev.ashtonite365.apex.features.menus.MenuChainRunner::class.java.methods.filter { it.name == "start" }
		Assertions.assertTrue(start.isNotEmpty())
		for (m in start) Assertions.assertTrue(m.parameterTypes.none { it == java.lang.Boolean.TYPE }, "start has a mode flag: $m")
		Assertions.assertFalse(dev.ashtonite365.apex.features.menus.MenuChainRunner.covering)
	}

	@Test
	fun doubleReopenSettlesBeforeClicking() {
		val c = MenuChain(listOf(MenuChain.Step(Regex("➜ Seeds$"), MenuChain.Target("Buy Instantly", 10))), delay = { 0 })
		c.begin(null, null, 0)
		c.onOpen(7, "Farming ➜ Seeds", 1_000)
		c.onOpen(8, "Farming ➜ Seeds", 1_050)
		Assertions.assertEquals(Action.Wait, c.onContents(8, product, 1_100))
		c.onOpen(9, "Farming ➜ Seeds", 1_200)
		Assertions.assertEquals(Action.Wait, c.onContents(9, product, 1_450))
		Assertions.assertEquals(Action.Wait, c.onContents(8, product, 1_600))
		val a = c.onContents(9, product, 1_500) as Action.Click
		Assertions.assertEquals(9, a.containerId)

		val s = MenuChain(listOf(MenuChain.Step(Regex("➜ Seeds$"), MenuChain.Target("Buy Instantly", 10))), delay = { 0 })
		s.begin(null, null, 0); s.onOpen(3, "Farming ➜ Seeds", 10)
		Assertions.assertTrue(s.onContents(3, product, 11) is Action.Click)
	}

	@Test
	fun stepCompletesOnChatWhenThePageUpdatesInPlace() {
		val chain = { MenuChain(listOf(
			MenuChain.Step(Regex("^Your Bazaar Orders$"), MenuChain.Target("Go Back", 31), doneChat = Regex("^\\[Bazaar\\] Claimed")),
			MenuChain.Step(Regex("^Your Bazaar Orders$"), MenuChain.Target("Close", 49)),
		), timeoutMs = 5_000, delay = { 0 }) }
		val c = chain(); c.begin(4, "Your Bazaar Orders", 0)
		Assertions.assertTrue(c.onContents(4, listOf(Label(31, "Go Back"), Label(49, "Close")), 10) is Action.Click)
		Assertions.assertTrue(c.fire(4, 20))
		Assertions.assertEquals(Action.Wait, c.onContents(4, listOf(Label(49, "Close")), 30))
		Assertions.assertEquals(Action.Wait, c.onChat("[Bazaar] Something else", 40))
		c.onChat("[Bazaar] Claimed 64x Seeds", 50)
		Assertions.assertTrue(c.onContents(4, listOf(Label(49, "Close")), 60) is Action.Click)
		Assertions.assertTrue(c.fire(4, 70)); Assertions.assertEquals(Action.Done, c.finished)

		val w = chain(); w.begin(4, "Your Bazaar Orders", 0)
		w.onContents(4, listOf(Label(31, "Go Back")), 10); w.fire(4, 20)
		w.onOpen(5, "Your Bazaar Orders", 30)
		Assertions.assertTrue(w.onContents(5, listOf(Label(49, "Close")), 40) is Action.Click)

		val t = chain(); t.begin(4, "Your Bazaar Orders", 0)
		t.onContents(4, listOf(Label(31, "Go Back")), 10); t.fire(4, 20)
		Assertions.assertTrue(t.onTick(5_021) is Action.Abort)

		val m = MenuChain(listOf(MenuChain.Step(Regex(""), MenuChain.Target("", match = Regex("^Buy \\d+x")))), delay = { 0 })
		m.begin(1, "x", 0)
		Assertions.assertEquals(13, (m.onContents(1, listOf(Label(11, "Seeds"), Label(13, "Buy 48x Seeds")), 1) as Action.Click).slot)
	}
}
