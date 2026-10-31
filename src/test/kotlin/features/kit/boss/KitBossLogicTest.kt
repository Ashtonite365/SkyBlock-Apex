// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.boss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.boss.DragonState
import dev.ashtonite365.apex.features.kit.boss.KingRelic
import dev.ashtonite365.apex.features.kit.boss.SpiritBearState
import dev.ashtonite365.apex.features.kit.boss.TerracottaState
import dev.ashtonite365.apex.features.kit.boss.TickTimerState

class KitBossLogicTest {
	@Test
	fun tickTimers() {
		val s = TickTimerState()
		s.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.")
		assertEquals(20, s.pad); assertEquals(520, s.lightning)
		repeat(21) { s.onServerTick(goldorLoop = false, padLoop = true) }
		assertEquals(19, s.pad)
		s.onChat("[BOSS] Goldor: Who dares trespass into my domain?")
		assertEquals(60, s.goldorTick)
		assertEquals("§7Tick: §a3.00s", TickTimerState.format(60, 60, "§7Tick:", false, true, true))
		assertEquals("§c5t", TickTimerState.format(5, 60, "x", true, true, false))
	}

	@Test
	fun spiritBear() {
		val s = SpiritBearState()
		s.onBlock(-3, 77, 33, "coal_block", "sea_lantern", false)
		assertEquals(1, s.kills); assertEquals("§d1/25", s.display(false))
		s.onBlock(7, 77, 34, "coal_block", "sea_lantern", false)
		assertEquals(68, s.timer)
		repeat(68) { s.onServerTick() }
		assertEquals("§aAlive!", s.display(false))
		s.onBlock(0, 0, 0, "coal_block", "sea_lantern", false); assertEquals(2, s.kills)
	}

	@Test
	fun terracotta() {
		val s = TerracottaState()
		s.onFlowerPot(1, 2, 3, master = true); s.onFlowerPot(1, 2, 3, master = true)
		assertEquals(1, s.spawning.size); assertEquals(12f, s.spawning[0].seconds)
		repeat(240) { s.onServerTick() }
		assertEquals(0, s.spawning.size)
	}

	@Test
	fun dragons() {
		val s = DragonState()
		assertNull(s.onParticle(true, 20, 27.0, 18.0, 59.0, 2f, 3f, 2f))
		assertEquals(DragonState.Dragon.Red, s.onParticle(true, 20, 27.0, 19.0, 59.0, 2f, 3f, 2f))
		assertEquals(DragonState.Dragon.Red, s.priority)
		repeat(100) { s.onServerTick() }
		assertEquals(listOf(DragonState.Dragon.Red), s.onServerTick())
		repeat(40) { s.onServerTick() }
		assertEquals(DragonState.Dragon.Red to 40L, s.onStatueAir(32, 22, 59))
		assertEquals("§a5000ms", DragonState.timer(100, 0, true))
		assertEquals("§e600.0m", DragonState.health(6e8f))
		assertEquals(KingRelic.Blue, KingRelic.of("BLUE_KING_RELIC"))
	}
}
