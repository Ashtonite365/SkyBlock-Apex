// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.features.dungeon.TerminalSimulatorScreen
import dev.ashtonite365.apex.features.kit.terminals.PickModel
import dev.ashtonite365.apex.features.kit.terminals.RubixModel
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlin.random.Random
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

interface SimAdapter {
	fun boardId(s: TerminalSimulatorScreen): Any
	fun seed(s: TerminalSimulatorScreen, rnd: Random)
	fun mode(s: TerminalSimulatorScreen): TerminalSimulatorScreen.Mode
	fun observe(s: TerminalSimulatorScreen): String

	fun goodCell(s: TerminalSimulatorScreen, rnd: Random): Int
	fun columns(s: TerminalSimulatorScreen): Int
}

object SimDriver {
	val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
	@Suppress("DEPRECATION")
	fun put(o: Any, f: java.lang.reflect.Field, v: Any?) = unsafe.putObject(o, unsafe.objectFieldOffset(f), v)
	fun field(c: Class<*>, n: String) = c.getDeclaredField(n).also { it.isAccessible = true }

	fun seededPick(mode: TerminalSimulatorScreen.Mode, rnd: Random): Pair<PickModel?, String> = when (mode) {
		TerminalSimulatorScreen.Mode.STARTS_WITH -> PickModel.LETTERS.random(rnd).let { PickModel.startsWith(it, 21, rnd) to "What starts with: '$it'?" }
		TerminalSimulatorScreen.Mode.SELECT_ALL -> PickModel.COLOURS.random(rnd).let { PickModel.selectAll(it, 28, rnd) to "Select all the ${it.uppercase()} items!" }
		else -> null to ""
	}
	fun pickState(p: PickModel?) = p?.let { "${it.labels}/${it.picked.joinToString("") { b -> if (b) "1" else "0" }}/${it.failed}/${it.done}" } ?: "-"
	fun goodPick(p: PickModel?, rnd: Random) = p?.let { pk -> pk.labels.indices.filter { pk.matches(pk.labels[it]) && !pk.picked[it] }.randomOrNull(rnd) } ?: rnd.nextInt(30)

	fun run(adapter: SimAdapter, seed: Int, steps: Int): List<Pair<String, String>> {
		val out = mutableListOf<Pair<String, String>>()
		val rnd = Random(seed)
		Harness.load(dev.ashtonite365.apex.features.dungeon.TerminalSimulator.TConfig)
		FakeMinecraft.with("Steve") {
			val s = TerminalSimulatorScreen()
			s.width = 400; s.height = 300
			var board: Any? = null
			for (step in 0 until steps) {
				if (adapter.boardId(s) !== board) { adapter.seed(s, rnd); board = adapter.boardId(s) }
				val cols = adapter.columns(s)
				val left = (400 - cols * 20) / 2
				val top = 300 / 2 - 30
				val kind = rnd.nextInt(10)
				val desc: String
				val res = when {
					kind < 5 || kind == 9 -> {
						val i = if (kind == 9) rnd.nextInt(-2, 40) else adapter.goodCell(s, rnd)
						val x = left + Math.floorMod(i, cols) * 20 + rnd.nextInt(0, 20) + (if (rnd.nextInt(8) == 0) cols * 20 else 0)
						val y = top + Math.floorDiv(i, cols) * 20 + rnd.nextInt(0, 20)
						val right = kind == 4 && rnd.nextBoolean() || adapter.mode(s) == TerminalSimulatorScreen.Mode.RUBIX && rnd.nextInt(3) == 0
						desc = "click $x,$y ${if (right) "R" else "L"}"
						Golden.run { "${s.mouseClicked(MouseButtonEvent(x.toDouble(), y.toDouble(), MouseButtonInfo(if (right) InputConstants.MOUSE_BUTTON_RIGHT else InputConstants.MOUSE_BUTTON_LEFT, 0)), false)}" }
					}
					kind < 7 -> { desc = "right"; Golden.run { "${s.mouseClicked(MouseButtonEvent(5.0, 5.0, MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0)), false)}" } }
					kind == 7 -> { desc = "tab"; Golden.run { "${s.keyPressed(KeyEvent(InputConstants.KEY_TAB, 0, 0))}" } }
					else -> { desc = "outside"; Golden.run { "${s.mouseClicked(MouseButtonEvent(-3.0, 1000.0, MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false)}" } }
				}

				if (adapter.boardId(s) !== board) { adapter.seed(s, rnd); board = adapter.boardId(s) }
				out += "sim|$seed|$step|$desc" to "$res|${adapter.observe(s)}"
			}
		}
		return out
	}
}

object NewSimAdapter : SimAdapter {
	private val c = TerminalSimulatorScreen::class.java
	private val rc = Class.forName("dev.ashtonite365.apex.features.dungeon.TerminalSimulatorScreen\$Round")
	private val fRound = SimDriver.field(c, "round"); private val fBest = SimDriver.field(c, "personalBest")
	private val fLit = SimDriver.field(rc, "lit"); private val fNumbers = SimDriver.field(rc, "numbers"); private val fWanted = SimDriver.field(rc, "wanted")
	private val fCube = SimDriver.field(rc, "cube"); private val fPicker = SimDriver.field(rc, "picker"); private val fPrompt = SimDriver.field(rc, "prompt")
	private val fTook = SimDriver.field(rc, "tookMs"); private val fMode = SimDriver.field(rc, "mode")
	private fun round(s: TerminalSimulatorScreen): Any = fRound.get(s)
	override fun boardId(s: TerminalSimulatorScreen): Any = round(s)
	override fun mode(s: TerminalSimulatorScreen) = fMode.get(round(s)) as TerminalSimulatorScreen.Mode
	override fun columns(s: TerminalSimulatorScreen) = when (mode(s)) { TerminalSimulatorScreen.Mode.PANES -> 5; TerminalSimulatorScreen.Mode.RUBIX -> 3; else -> 7 }
	override fun seed(s: TerminalSimulatorScreen, rnd: Random) {
		val r = round(s)
		val g = fLit.get(r) as BooleanArray
		for (i in g.indices) g[i] = rnd.nextDouble() < 0.3
		SimDriver.put(r, fNumbers, (1..14).shuffled(rnd))
		SimDriver.put(r, fCube, RubixModel(9, rnd))
		val (p, t) = SimDriver.seededPick(mode(s), rnd)
		SimDriver.put(r, fPicker, p); if (p != null) SimDriver.put(r, fPrompt, t)
	}
	@Suppress("UNCHECKED_CAST")
	override fun observe(s: TerminalSimulatorScreen): String {
		val r = round(s); val m = mode(s)
		val heading = if (m == TerminalSimulatorScreen.Mode.STARTS_WITH || m == TerminalSimulatorScreen.Mode.SELECT_ALL) fPrompt.get(r) else m.label
		return "$m|$heading|${(fLit.get(r) as BooleanArray).joinToString("") { if (it) "1" else "0" }}|${fNumbers.get(r)}|${fWanted.get(r)}|" +
			"${(fCube.get(r) as RubixModel).cells.toList()}|${SimDriver.pickState(fPicker.get(r) as PickModel?)}|${fTook.get(r) != null}|${(fBest.get(s) as Map<*, *>).keys.sortedBy { it.toString() }}"
	}
	@Suppress("UNCHECKED_CAST")
	override fun goodCell(s: TerminalSimulatorScreen, rnd: Random): Int {
		val r = round(s)
		return when (mode(s)) {
			TerminalSimulatorScreen.Mode.PANES -> (fLit.get(r) as BooleanArray).withIndex().filter { !it.value }.map { it.index }.randomOrNull(rnd) ?: 0
			TerminalSimulatorScreen.Mode.ORDER -> (fNumbers.get(r) as List<Int>).indexOf(fWanted.get(r) as Int).let { if (it < 0) 0 else it }
			TerminalSimulatorScreen.Mode.RUBIX -> rnd.nextInt(9)
			else -> SimDriver.goodPick(fPicker.get(r) as PickModel?, rnd)
		}
	}
}

@ExtendWith(AutoBootstrapExtension::class)
class Batch53bGoldenTest {
	@Test
	fun simulator() {
		val out = (0 until 60).flatMap { SimDriver.run(NewSimAdapter, 5300 + it, 120) }
		val n = Golden.verify("batch53-sim", out, minCases = 7190)
		assertTrue(n >= 7190)
	}
}
