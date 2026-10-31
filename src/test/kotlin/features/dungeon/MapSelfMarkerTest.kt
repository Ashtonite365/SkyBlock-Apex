// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.features.dungeon.MapSelfMarker
import dev.ashtonite365.apex.features.dungeon.MapSelfMarker.Calibration
import dev.ashtonite365.apex.features.dungeon.MapSelfMarker.Source

class MapSelfMarkerTest {
	private fun near(e: Double, a: Double, eps: Double = 1e-6) = assertEquals(e, a, eps)

	private data class Floor(val name: String, val cal: Calibration, val grid: DungeonMap.Grid)
	private val floors = listOf(
		Floor("6x6 (F5-F7)", Calibration(5, 5, 20), DungeonMap.Grid(5, 5, 16)),
		Floor("6x5 (F4)", Calibration(5, 16, 20), DungeonMap.Grid(5, 16, 16)),
		Floor("5x5 (F2/F3)", Calibration(11, 11, 22), DungeonMap.Grid(11, 11, 18)),
		Floor("4x5 (F1)", Calibration(22, 11, 22), DungeonMap.Grid(0, 11, 18)),
		Floor("4x4 (Entrance)", Calibration(22, 22, 22), DungeonMap.Grid(0, 0, 18)),
	)

	private fun marker(f: Floor, x: Double, z: Double) = MapSelfMarker.worldToMap(f.cal, x, z)

	@Test
	fun worldToMapSeveralFloorsAndPositions() {
		val six = Calibration(5, 5, 20)
		near(5.0, MapSelfMarker.worldToMap(six, -200.0, -200.0).first)
		near(14.375, MapSelfMarker.worldToMap(six, -185.0, -185.0).second)
		near(45.0, MapSelfMarker.worldToMap(six, -136.0, -200.0).first)
		near(5.0 + 191 * 0.625, MapSelfMarker.worldToMap(six, -9.0, -9.0).first)
		val e = Calibration(22, 22, 22)
		val (ex, ez) = MapSelfMarker.worldToMap(e, -185.0, -153.0)
		near(22 + 15 * 0.6875, ex); near(22 + 47 * 0.6875, ez)

		for (f in floors) {
			val a = MapSelfMarker.worldToMap(f.cal, -170.0, -120.0)
			val b = MapSelfMarker.worldToMap(f.cal, -138.0, -88.0)
			near(f.cal.step.toDouble(), b.first - a.first, 1e-9); near(f.cal.step.toDouble(), b.second - a.second, 1e-9)
		}
	}

	@Test
	fun worldCellBounds() {
		assertEquals(0 to 0, MapSelfMarker.worldCell(-200.0, -200.0))
		assertEquals(0 to 0, MapSelfMarker.worldCell(-169.5, -170.0))
		assertEquals(1 to 0, MapSelfMarker.worldCell(-168.0, -200.0))
		assertEquals(5 to 5, MapSelfMarker.worldCell(-9.0, -9.0))
		assertNull(MapSelfMarker.worldCell(-200.5, -150.0))
		assertNull(MapSelfMarker.worldCell(-8.0, -150.0))
		assertNull(MapSelfMarker.worldCell(60.0, 60.0))
	}

	@Test
	fun calibrationSnapsToTheFloorsStartCorner() {
		for (f in floors) for ((x, z) in listOf(-185.0 to -185.0, -121.3 to -153.7, -100.0 to -60.0)) {
			if (MapSelfMarker.worldCell(x, z)?.let { it.first * f.cal.step + f.cal.originX < 128 && it.second * f.cal.step + f.cal.originY < 128 } != true) continue
			val (mx, my) = marker(f, x, z)
			assertEquals(f.cal, MapSelfMarker.sample(f.grid, mx, my, x, z), "${f.name} at $x,$z")

			val (lx, ly) = marker(f, x - 5, z + 4)
			assertEquals(f.cal, MapSelfMarker.sample(f.grid, lx, ly, x, z), "${f.name} lagging at $x,$z")
		}
	}

	@Test
	fun calibrationRejectsBadSamples() {
		val f = floors.last()
		val (mx, my) = marker(f, -185.0 - 14, -185.0)
		assertNull(MapSelfMarker.sample(f.grid, mx, my, -185.0, -185.0))
		assertNull(MapSelfMarker.sample(f.grid, 40.0, 40.0, 30.0, 30.0))
	}

	@Test
	fun calibratorNeedsAgreeingSamplesAndResetsOnNewGrid() {
		val f = floors[0]
		val c = MapSelfMarker.Calibrator()
		val (mx, my) = marker(f, -150.0, -150.0)
		assertNull(c.update(null, mx, my, -150.0, -150.0))
		assertNull(c.update(f.grid, null, null, -150.0, -150.0))
		assertNull(c.update(f.grid, mx, my, -150.0, -150.0))
		assertNull(c.update(f.grid, mx, my, -150.0, -150.0))
		assertEquals(f.cal, c.update(f.grid, mx, my, -150.0, -150.0))
		assertEquals(f.cal, c.update(f.grid, 0.0, 0.0, -150.0, -150.0))
		assertNull(c.update(floors[2].grid, mx, my, -150.0, -150.0))
		c.reset(); assertNull(c.calibration)
	}

	private val packet = Triple(40.0, 50.0, 90F)

	@Test
	fun fallbackBeforeCalibration() {
		val p = MapSelfMarker.place(true, null, false, -150.0, -150.0, 10F, packet)!!
		assertEquals(Source.PACKET, p.source); near(40.0, p.x); near(50.0, p.y); assertEquals(90F, p.rotation)
		assertNull(MapSelfMarker.place(true, null, false, -150.0, -150.0, 10F, null))
	}

	@Test
	fun liveWhenCalibrated() {
		val cal = floors[0].cal
		val (mx, my) = MapSelfMarker.worldToMap(cal, -150.0, -150.0)
		val lagging = Triple(mx - 3, my, 0F)
		val p = MapSelfMarker.place(true, cal, false, -150.0, -150.0, -45F, lagging)!!
		assertEquals(Source.LIVE, p.source); near(mx, p.x); near(my, p.y); assertEquals(315F, p.rotation)
	}

	@Test
	fun fallbackCases() {
		val cal = floors[0].cal
		val (mx, my) = MapSelfMarker.worldToMap(cal, -150.0, -150.0)
		val pk = Triple(mx, my, 0F)
		fun src(enabled: Boolean = true, c: Calibration? = cal, boss: Boolean = false, x: Double = -150.0, z: Double = -150.0, p: Triple<Double, Double, Float> = pk) =
			MapSelfMarker.place(enabled, c, boss, x, z, 0F, p)!!.source
		assertEquals(Source.LIVE, src())
		assertEquals(Source.PACKET, src(enabled = false))
		assertEquals(Source.PACKET, src(boss = true))
		assertEquals(Source.PACKET, src(x = 20.0, z = 20.0))
		assertEquals(Source.PACKET, src(p = Triple(mx + 40, my, 0F)))

		val e = Calibration(22, 22, 22)
		assertEquals(Source.PACKET, MapSelfMarker.place(true, e, false, -20.0, -150.0, 0F, Triple(127.0, 70.0, 0F))!!.source)
	}

	@Test
	fun yawToMarkerRotation() {
		assertEquals(0F, MapSelfMarker.markerRotation(0F))
		assertEquals(90F, MapSelfMarker.markerRotation(90F))
		assertEquals(180F, MapSelfMarker.markerRotation(-180F))
		assertEquals(270F, MapSelfMarker.markerRotation(-90F))
		assertEquals(90F, MapSelfMarker.markerRotation(450F))

		for (yaw in listOf(-170F, -33F, 0F, 12F, 101F, 359F)) {
			val rot = Math.floorMod(Math.round(yaw * 16F / 360F), 16)
			val d = Math.abs(MapSelfMarker.markerRotation(yaw) - DungeonMap.decorationYaw(rot))
			assertTrue(minOf(d, 360F - d) <= 11.25F + 1e-3F, "yaw $yaw")
		}

		assertEquals(0F, DungeonMap.headAngle(MapSelfMarker.markerRotation(180F)))
	}

	@Test
	fun partialTickInterpolation() {
		near(1.0, MapSelfMarker.lerp(0.25F, 0.0, 4.0))
		near(-150.5, MapSelfMarker.lerp(0.5F, -151.0, -150.0))
		assertEquals(0F, MapSelfMarker.markerRotation(MapSelfMarker.lerpYaw(0.5F, 350F, 10F)))
		assertEquals(170F, MapSelfMarker.lerpYaw(0.5F, 160F, 180F))
		assertEquals(-175F, MapSelfMarker.lerpYaw(0.5F, -170F, 540F))
	}

	@Test
	fun otherPlayersMarkersStayOnPacketData() {
		data class Deco(val self: Boolean, val x: Int, val y: Int, val rot: Int)
		val me = Deco(true, -20, 10, 3)
		val mates = listOf(Deco(false, 30, -40, 7), Deco(false, -90, 66, 0), Deco(false, 5, 5, 12))
		val all = listOf(mates[0], me, mates[1], mates[2])
		val live = DungeonMap.packetMarkers(all, { it.self }, selfLive = true)
		assertEquals(mates, live)
		for (i in mates.indices) assertSame(mates[i], live[i])
		assertEquals(all, DungeonMap.packetMarkers(all, { it.self }, selfLive = false))
	}
}
