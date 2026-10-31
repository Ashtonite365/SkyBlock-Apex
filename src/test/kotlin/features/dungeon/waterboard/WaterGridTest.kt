package dev.ashtonite365.apex.test.features.dungeon.waterboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.waterboard.BoardLayout
import dev.ashtonite365.apex.features.dungeon.waterboard.Door
import dev.ashtonite365.apex.features.dungeon.waterboard.DoorTracker
import dev.ashtonite365.apex.features.dungeon.waterboard.FlowAtlas
import dev.ashtonite365.apex.features.dungeon.waterboard.Gate
import dev.ashtonite365.apex.features.dungeon.waterboard.P
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterBoardPlanner
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterBoardReader
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterGrid
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterPreview

class WaterGridTest {
	private fun res(name: String) = javaClass.getResource("/waterboard/$name")!!.readText()
	private fun mask(vararg g: Gate) = g.fold(0) { m, x -> m or x.bit }

	private val dumped: Pair<BoardLayout, WaterBoardReader.Seen> by lazy {
		val (view, box) = WaterBoardReader.parseDump(res("waterboard-dump-20261030-150813.txt"))
		val why = mutableListOf<String>()
		val lay = WaterBoardReader.locate(view, box!!, why) ?: error("board not found: $why")
		lay to WaterBoardReader.observe(view, lay)
	}

	@Test
	fun dumpGridGolden() {
		val (lay, seen) = dumped
		val grid = lay.board.grid(seen.config ?: 0, water = { seen.water[it] > 0 })
		assertEquals(res("dump-20261030.grid").trimEnd(), grid.render())
		assertEquals(19, grid.width)

		assertEquals(grid.render(), WaterGrid.parse(grid.render()).render())
	}

	@Test
	fun dumpDoorsAreThePits() {
		val (lay, _) = dumped
		val b = lay.board
		val pits = b.doorTriggers.mapValues { (_, c) -> c.map { b.u(it) to b.v(it) } }
		assertEquals(listOf(0 to 1), pits[Door.PURPLE])
		assertEquals(listOf(4 to 1), pits[Door.ORANGE])
		assertEquals(listOf(9 to 1), pits[Door.BLUE])
		assertEquals(listOf(14 to 1), pits[Door.LIME])
		assertEquals(listOf(18 to 1), pits[Door.RED])
		assertTrue(b.sink.none { it }, "the bottom channel must not swallow water")
	}

	@Test
	fun letterToggling() {
		val g = WaterGrid.parse(listOf("1D1d", "0D21", "d000"))
		val t = g.toggled(Gate.DIAMOND)
		assertEquals("1d1D\n0d2D\nD000".replace("0d2D", "0d21"), t.render())
		assertEquals(g.render(), t.toggled(Gate.DIAMOND).render())

		val mixed = WaterGrid.parse(listOf("dDgG"))
		assertTrue(mixed.passable(0)); assertFalse(mixed.passable(1)); assertTrue(mixed.passable(2)); assertFalse(mixed.passable(3))
		assertEquals("DdgG", mixed.toggled(Gate.DIAMOND).render())

		val wet = WaterGrid.parse(listOf("2d", "00")).flow()
		assertTrue(wet.wet(wet.index(1, 1)))
		assertEquals(0, wet.toggled(Gate.DIAMOND).value(1, 1))
	}

	@Test
	fun flowLimitSideways() {

		val g = WaterGrid.parse(listOf("2" + "1".repeat(19), "0".repeat(20))).flow()
		assertEquals(WaterGrid.FLOW_LIMIT, 7)
		for (u in 0..7) assertTrue(g.wet(g.index(u, 1)), "u=$u")
		assertFalse(g.wet(g.index(8, 1)))

		val fall = WaterGrid.parse(listOf("2000000000000", "1000000000000", "1111111111111", "0000000000000")).flow()
		for (u in 0..7) assertTrue(fall.wet(fall.index(u, 1)))
		assertFalse(fall.wet(fall.index(8, 1)))
		assertEquals("2000000000000\n2000000000000\n2222222211111\n0000000000000", fall.render())
	}

	@Test
	fun flowPrefersNearestDropAndFallsFirst() {

		val g = WaterGrid.parse(listOf("111121111", "010000100", "000000000")).flow()
		assertEquals("111122211\n010000200\n000000000", g.render())

		val both = WaterGrid.parse(listOf("002000", "111111", "000000")).flow()
		assertEquals("002000\n222222\n000000", both.render())

		assertEquals("2\n2\n2", WaterGrid.parse(listOf("2", "q", "1")).flow().renderWater())
		assertEquals("2\nQ\n1", WaterGrid.parse(listOf("2", "Q", "1")).flow().renderWater())
	}

	@Test
	fun dumpSolve() {
		val (lay, seen) = dumped
		val atlas = FlowAtlas(lay.board)
		assertEquals(Door.PURPLE.bit, atlas.doors(0), "levers as dumped send the water to purple")
		val tracker = DoorTracker(lay.board.doors, seen.gateWoolCount, seen.rowWoolCount)
		assertEquals(Door.ORANGE.bit or Door.BLUE.bit or Door.RED.bit, tracker.wanted)
		val plan = WaterBoardPlanner.plan(atlas, seen.config ?: 0, tracker.wanted)!!
		assertEquals(
			listOf(
				mask(Gate.QUARTZ, Gate.COAL, Gate.EMERALD) to Door.RED.bit,
				mask(Gate.QUARTZ, Gate.COAL, Gate.DIAMOND, Gate.EMERALD) to Door.BLUE.bit,
				mask(Gate.QUARTZ, Gate.GOLD, Gate.EMERALD, Gate.TERRACOTTA) to Door.ORANGE.bit,
			),
			plan.pulses.map { it.config to it.doors },
		)
		assertEquals(8, plan.flips)

		for (p in plan.pulses) assertEquals(p.doors, atlas.doors(p.config))
	}

	@Test
	fun previewFromTargetedBoardBlock() {
		val (lay, _) = dumped
		val b = lay.board
		fun look(target: P, eye: Triple<Double, Double, Double> = Triple(-150.5, 70.0, -184.0)): Int? {
			val dx = target.x + 0.5 - eye.first; val dy = target.y + 0.5 - eye.second; val dz = -195.5 - eye.third
			val n = Math.sqrt(dx * dx + dy * dy + dz * dz)
			return WaterPreview.targetCell(lay.frame, b.width, b.height, eye, Triple(dx / n, dy / n, dz / n))
		}

		assertEquals(Gate.DIAMOND, WaterPreview.gateFor(b, look(P(-151, 77, -196))))

		assertEquals(Gate.DIAMOND, WaterPreview.gateFor(b, look(P(-155, 63, -196))))
		assertEquals(Gate.GOLD, WaterPreview.gateFor(b, look(P(-148, 71, -196))))

		assertNull(WaterPreview.gateFor(b, look(P(-160, 70, -196))))
		assertNull(WaterPreview.targetCell(lay.frame, b.width, b.height, Triple(-150.5, 70.0, -184.0), Triple(0.0, 0.0, 1.0)))

		assertEquals(Gate.DIAMOND.bit, WaterPreview.previewConfig(0, Gate.DIAMOND, 5))
		assertEquals(5, WaterPreview.previewConfig(0, null, 5))
		val atlas = FlowAtlas(b)
		assertEquals(atlas.doors(Gate.DIAMOND.bit), atlas.doors(WaterPreview.previewConfig(0, Gate.DIAMOND, null)!!))
	}
}

class WaterBoardScanReachTest {
	private val view by lazy {
		WaterBoardReader.parseDump(javaClass.getResource("/waterboard/waterboard-dump-20261030-150813.txt")!!.readText()).first
	}

	private fun found(p: P): Boolean {
		val why = mutableListOf<String>()
		return WaterBoardReader.locate(view, WaterBoardReader.scanBoxFor(p), why) != null
	}

	@Test fun foundFromTheLeverFloor() = assertTrue(found(P(-152, 60, -176)))
	@Test fun foundFromTheTopEntrance() = assertTrue(found(P(-153, 84, -180)))
	@Test fun foundFromHalfWayDown() = assertTrue(found(P(-153, 72, -178)))
}

class WaterBoardOldReachTest {
	private val view by lazy {
		WaterBoardReader.parseDump(javaClass.getResource("/waterboard/waterboard-dump-20261030-150813.txt")!!.readText()).first
	}
	@Test fun oldReachMissedTheBoardFromTheTop() {
		val old = { p: P -> WaterBoardReader.Box.around(p, 24, 12, 36) }
		assertTrue(WaterBoardReader.locate(view, old(P(-152, 60, -176))) != null)
		assertNull(WaterBoardReader.locate(view, old(P(-153, 84, -180))))
	}
}
