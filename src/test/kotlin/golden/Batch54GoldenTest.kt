// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.SecretRooms
import dev.ashtonite365.apex.features.kit.devices.SimonSaysState
import dev.ashtonite365.apex.features.plus.rift.PlusRiftExtras
import dev.ashtonite365.apex.test.golden.Harness
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlin.random.Random
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch54GoldenTest {
	companion object {
		val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
		@Suppress("UNCHECKED_CAST")
		fun <T> alloc(c: Class<T>): T = unsafe.allocateInstance(c) as T
	}

	@Test
	fun simon() {
		val out = mutableListOf<Pair<String, String>>()
		val grid = (120..123).flatMap { y -> (92..95).map { z -> y to z } }
		fun st(s: SimonSaysState) = "${s.order.map { it.toShortString() }} ${s.clickNeeded} ${s.firstPhase} ${s.ticksSinceLantern} ${s.remaining().map { it.toShortString() }}"
		for (seed in 0 until 400) {
			val r = Random(5400 + seed)
			val s = SimonSaysState()
			if (r.nextBoolean()) s.onStartPressed()
			val seq = grid.shuffled(r).take(5)
			var k = 0
			for (round in 1..5) {

				for (i in 0 until round) {
					if (i < round - 1 && r.nextInt(3) != 0) continue
					val (y, z) = seq[i]
					s.onLanternShown(BlockPos(111, y, z))
					out += "simon|$seed|${k++}|lantern $y,$z" to st(s)
				}
				repeat(r.nextInt(0, 14)) {
					val n = r.nextInt(0, 13)
					s.onTick(n)
					out += "simon|$seed|${k++}|tick $n" to st(s)
				}
				var presses = r.nextInt(1, 7)
				while (presses-- > 0) {
					val rem = s.remaining()
					val p = if (rem.isNotEmpty() && r.nextInt(5) != 0) rem.first()
						else if (r.nextInt(6) == 0) SimonSaysState.START_BUTTON
						else grid.random(r).let { (y, z) -> BlockPos(110, y, z) }
					val desc: String
					if (p == SimonSaysState.START_BUTTON) { s.onStartPressed(); desc = "start" }
					else {
						val c = s.isCorrectClick(p); val f = s.roundFinishedBy(p); s.onButtonPressed(p)
						desc = "press ${p.toShortString()} $c $f"
					}
					out += "simon|$seed|${k++}|$desc" to st(s)
				}
			}
		}
		val n = Golden.verify("batch54-simon", out, minCases = 23728)
		assertTrue(n >= 23728)
	}

	@Test
	fun rotations() {
		val out = mutableListOf<Pair<String, String>>()
		val r = Random(5454)
		val shapes = mutableListOf<Pair<String, List<Pair<Int, Int>>>>()
		for (ox in listOf(-1, 0, 1, 3, 4)) for (oz in listOf(-2, 0, 2, 4)) {
			val box = listOf(ox to oz, ox + 1 to oz, ox to oz + 1, ox + 1 to oz + 1)
			for (miss in box.indices) {
				val l = box.filterIndexed { i, _ -> i != miss }
				shapes += "L" to l; shapes += "L" to l.reversed(); shapes += "L" to l.shuffled(r)
			}
			shapes += "L" to box; shapes += "L" to box.take(2); shapes += "L" to listOf(ox to oz, ox + 1 to oz + 1, ox + 2 to oz)
			for (len in 1..4) {
				val row = (0 until len).map { ox + it to oz }; val col = (0 until len).map { ox to oz + it }
				for (shape in listOf("1x$len", "${len}x1", "2x2")) { shapes += shape to row; shapes += shape to col.shuffled(r) }
			}
			shapes += "2x2" to box.shuffled(r)
		}
		for ((i, sc) in shapes.withIndex()) {
			val (shape, cells) = sc
			val rot = Golden.run { SecretRooms.geometryRotation(shape, cells).toString() }
			val clay = SecretRooms.Rotation.entries.joinToString(";") { rr -> Golden.run { SecretRooms.clayPos(shape, cells, rr).toString() } }
			out += "rot|$i|$shape|$cells" to "$rot|$clay"
		}
		for (rr in SecretRooms.Rotation.entries) repeat(60) {
			val x = r.nextInt(-40, 41); val z = r.nextInt(-40, 41)
			out += "rn|$rr|$x|$z" to "${SecretRooms.rotateAroundNorth(x, z, rr)} ${SecretRooms.rotateToNorth(x, z, rr)} ${SecretRooms.rotateToNorth(SecretRooms.rotateAroundNorth(x, z, rr).first, SecretRooms.rotateAroundNorth(x, z, rr).second, rr)}"
		}
		val n = Golden.verify("batch54-rotations", out, minCases = 1030)
		assertTrue(n >= 1030)
	}

	@Test
	fun riftExtras() {
		val out = mutableListOf<Pair<String, String>>()
		val r = Random(5455)
		Harness.load(PlusRiftExtras.TConfig)
		if (runCatching { PlusRiftExtras.DConfig.data }.isFailure) PlusRiftExtras.DConfig.explicitDefaultLoad()
		val fCombo = PlusRiftExtras::class.java.getDeclaredField("combo").also { it.isAccessible = true }
		val fComboAt = PlusRiftExtras::class.java.getDeclaredField("comboAt").also { it.isAccessible = true }
		val hud = alloc(HudRenderEvent::class.java)
		val render = alloc(WorldRenderLastEvent::class.java)
		val chats = listOf("SPLIT! You need to wait 1h 2m 3s before you can play again.", "SPLIT! You need to wait 30s before you can play again.",
			"SPLIT! You need to wait 2d before you can play again.", "SPLIT! You need to wait soon before you can play again.",
			"  SPLIT! You need to wait 4m before you can play again.  ", "STEAL! You need to wait 1m before you can play again.", "hello")
		val bars = listOf("§6[§e||||§6] x12 §6[§e|||§6]", "[ab] x3 [cd]", "[ab] x3 cd", "x5", "§c❤ 100/100 [|||] x0 [||]", "[a] x99999 [b] extra]", "")
		val locs = listOf(null, Harness.skyblock("rift"), Harness.skyblock("hub"))
		fun state(): String {
			val d = PlusRiftExtras.DConfig.data
			val now = System.currentTimeMillis()
			val ready = if (d.ubikReadyAt == 0L) "0" else ((d.ubikReadyAt - now + 500) / 1000).toString()
			return "ready=$ready reminded=${d.reminded} combo=${fCombo.get(PlusRiftExtras)} recent=${(fComboAt.get(PlusRiftExtras) as Long).let { it != 0L && now - it < 2000 }}"
		}
		for (case in 0 until 900) {
			val loc = locs.random(r)
			val opts = mapOf("ubik-reminder" to r.nextBoolean(), "sun-gecko-combo" to r.nextBoolean(), "bacte-tentacles" to r.nextBoolean())
			Harness.withOpts(PlusRiftExtras.TConfig, opts) { Harness.at(loc) {
				val kind = r.nextInt(7)
				val desc: String
				val res: String
				when (kind) {
					0 -> { val c = chats.random(r); desc = "chat $c"; res = Golden.run { PlusRiftExtras.onChat(ProcessChatEvent(Component.literal(c), false)); "ok" } }
					1 -> { val t = r.nextInt(0, 81); desc = "tick $t"; res = Harness.chat { Golden.run { PlusRiftExtras.onTick(TickEvent(t)); "" } }.toString() }
					2 -> { val b = bars.random(r); desc = "bar $b"; res = Golden.run { PlusRiftExtras.onActionBar(ActionBarEvent(Component.literal(b))); "ok" } }
					3 -> {
						val past = listOf(0L, -1L, -60_000L, 30_000L, 125_000L).random(r)
						val d = PlusRiftExtras.DConfig.data
						d.ubikReadyAt = if (past == 0L) 0L else System.currentTimeMillis() + past; d.reminded = r.nextBoolean()
						desc = "set $past ${d.reminded}"; res = "ok"
					}
					4 -> {
						val age = listOf(0L, 1000L, 4000L, 6000L).random(r)
						fCombo.set(PlusRiftExtras, if (r.nextInt(4) == 0) null else r.nextInt(0, 30)); fComboAt.setLong(PlusRiftExtras, System.currentTimeMillis() - age)
						desc = "combo-age $age"; res = "ok"
					}
					5 -> { desc = "hud"; res = Golden.run { PlusRiftExtras.onHud(hud); "nodraw" } }
					else -> { desc = "render"; res = Golden.run { PlusRiftExtras.onRender(render); "nodraw" } }
				}
				out += "rx|$case|${loc?.mode}|$opts|$desc" to "$res|${state()}"
			} }
		}
		val n = Golden.verify("batch54-riftextras", out, minCases = 890)
		assertTrue(n >= 890)
	}
}
