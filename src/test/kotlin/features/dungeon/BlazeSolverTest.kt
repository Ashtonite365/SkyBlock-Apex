package dev.ashtonite365.apex.test.features.dungeon

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.BlazeOrder
import dev.ashtonite365.apex.features.dungeon.BlazeOrder.Blaze
import dev.ashtonite365.apex.features.dungeon.BlazeOrder.Direction
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers

class BlazeSolverTest {
	private fun b(id: Int, max: Long, y: Double = 70.0, x: Double = 0.0, hp: Long = max) = Blaze(id, hp, max, x, y, 0.0)
	private val room = listOf(b(1, 30_000), b(2, 10_000), b(3, 50_000), b(4, 20_000), b(5, 40_000))

	@Test fun parsesRealNametags() {
		assertEquals(20_000L to 20_000L, BlazeOrder.parseNametag("[Lv15] Blaze 20,000/20,000❤"))
		assertEquals(4_120L to 9_900L, BlazeOrder.parseNametag("§8[§7Lv15§8] §cBlaze §a4,120§f/§a9,900§c❤"))
		assertEquals(1_500_000L to 2_000_000L, BlazeOrder.parseNametag("[Lv15] Blaze 1.5M/2M❤"))
		assertEquals(12_000L to 12_000L, BlazeOrder.parseNametag("✯ Blaze 12k/12k❤"))
		assertEquals(1_200L to 6_499L, BlazeOrder.parseNametag("[Lv15] Blaze 1.2k/6,499❤"))
		assertNull(BlazeOrder.parseNametag("[Lv15] Zombie 100/100❤"))
		assertNull(BlazeOrder.parseNametag("Blaze"))
	}

	@Test fun orderBothDirections() {
		assertEquals(listOf(2, 4, 1, 5, 3), BlazeOrder.order(room, Direction.LOWEST_FIRST).map { it.id })
		assertEquals(listOf(3, 5, 1, 4, 2), BlazeOrder.order(room, Direction.HIGHEST_FIRST).map { it.id })
	}

	@Test fun directionFromRewardChest() {

		val ys = listOf(60.0, 65.0, 80.0)
		assertEquals(Direction.HIGHEST_FIRST, BlazeOrder.detectDirection(listOf(95), ys))
		assertEquals(Direction.LOWEST_FIRST, BlazeOrder.detectDirection(listOf(50), ys))
		assertNull(BlazeOrder.detectDirection(emptyList(), ys))
		assertNull(BlazeOrder.detectDirection(listOf(50, 95), ys))
	}

	@Test fun realRoomLayouts() {

		val higherRoom = (0 until 10).map { 74.0 + it * 4 }
		val lowerRoom = (0 until 10).map { 65.0 - it * 4 }
		assertEquals(Direction.LOWEST_FIRST, BlazeOrder.detectDirection(listOf(70), higherRoom))
		assertEquals(Direction.HIGHEST_FIRST, BlazeOrder.detectDirection(listOf(69), lowerRoom))
	}

	@Test fun chestOnlyReadWhileOnItsStartSide() {
		val ys = listOf(60.0, 65.0, 80.0)
		assertTrue(BlazeOrder.chestReadable(killed = 0, aliveInRoom = 10))
		assertTrue(BlazeOrder.chestReadable(killed = 1, aliveInRoom = 9))

		assertTrue(BlazeOrder.chestReadable(killed = 0, aliveInRoom = 8))

		assertFalse(BlazeOrder.chestReadable(killed = 0, aliveInRoom = 7))
		assertFalse(BlazeOrder.chestReadable(killed = 3, aliveInRoom = 10))
		assertNull(BlazeOrder.detectDirection(listOf(95), ys, killed = 6, aliveInRoom = 4))
		assertNull(BlazeOrder.detectDirection(listOf(50), ys, killed = 0, aliveInRoom = 7))
		assertEquals(Direction.LOWEST_FIRST, BlazeOrder.detectDirection(listOf(50), ys, killed = 0, aliveInRoom = 10))
		assertEquals(Direction.LOWEST_FIRST, BlazeOrder.detectDirection(listOf(50), ys, killed = 0, aliveInRoom = 8))
		assertEquals(Direction.HIGHEST_FIRST, BlazeOrder.detectDirection(listOf(95), ys, killed = 2, aliveInRoom = 8))
	}

	@Test fun directionLabels() {
		assertEquals("Lowest -> highest", BlazeOrder.label(Direction.LOWEST_FIRST))
		assertEquals("Highest -> lowest", BlazeOrder.label(Direction.HIGHEST_FIRST))
	}

	@Test fun tiesAreDeterministic() {
		val tied = listOf(b(9, 100, y = 72.0), b(3, 100, y = 70.0, x = 5.0), b(4, 100, y = 70.0, x = 1.0), b(1, 50))
		val expected = listOf(1, 4, 3, 9)
		assertEquals(expected, BlazeOrder.order(tied, Direction.LOWEST_FIRST).map { it.id })
		assertEquals(expected, BlazeOrder.order(tied.reversed(), Direction.LOWEST_FIRST).map { it.id })
		assertTrue(BlazeOrder.hasTie(tied))
		assertFalse(BlazeOrder.hasTie(room))
	}

	private fun tracker(dir: Direction = Direction.LOWEST_FIRST) = BlazeOrder.Tracker().apply { direction = dir; observe(room) }

	@Test fun liveDeathUpdates() {
		val t = tracker()
		assertEquals(2, t.current()?.id); assertEquals(4, t.next()?.id)
		t.observe(room.filter { it.id != 2 }, died = setOf(2))
		assertEquals(4, t.current()?.id); assertEquals(1, t.next()?.id)
		t.observe(room.map { if (it.id == 4) it.copy(hp = 0) else it }.filter { it.id != 2 })
		assertEquals(1, t.current()?.id)
	}

	@Test fun outOfRangeBlazeStaysAlive() {
		val t = tracker()
		val without2 = room.filter { it.id != 2 }

		repeat(10) { t.observe(without2) }
		assertEquals(2, t.current()?.id); assertEquals(4, t.next()?.id)
		assertFalse(t.isVisible(2))
		assertTrue(2 !in t.dead)
	}

	@Test fun goneEntityNearIsDeathAwayIsUnload() {
		val t = tracker()
		val without2 = room.filter { it.id != 2 }
		t.observe(without2, gone = setOf(2), playerNear = false)
		assertEquals(2, t.current()?.id)
		t.observe(without2, gone = setOf(2)); assertEquals(4, t.current()?.id)
		t.observe(room); assertEquals(4, t.current()?.id)
		t.observe(room, died = setOf(4)); assertEquals(1, t.current()?.id)
		t.observe(room, gone = setOf(77)); assertEquals(setOf(2, 4), t.dead.toSet())
	}

	@Test fun doneState() {
		val t = tracker(Direction.HIGHEST_FIRST)
		assertFalse(t.isDone())
		t.observe(emptyList(), died = room.map { it.id }.toSet())
		assertTrue(t.isDone()); assertNull(t.current())
		assertTrue(t.takeDoneAnnouncement()); assertFalse(t.takeDoneAnnouncement())
		t.reset(); assertFalse(t.isDone())
	}

	@Test fun unknownDirectionTargetsNothing() {
		val t = BlazeOrder.Tracker(); t.observe(room)
		assertNull(t.current())
	}

	@Test fun oldBlazeToggleMigrates() {
		val on = PuzzleSolvers.foldOldBlaze(JsonObject(mapOf("blaze" to JsonPrimitive(true), "blaze-highest-first" to JsonPrimitive(true))))
		assertEquals(JsonObject(mapOf("blaze-solver" to JsonPrimitive(true))), on)
		val off = PuzzleSolvers.foldOldBlaze(JsonObject(mapOf("blaze" to JsonPrimitive(false))))
		assertEquals(JsonObject(emptyMap()), off)
	}

	@Test fun parsesNametagWithoutMax() {
		assertEquals(4_120L to null, BlazeOrder.parseNametag("[Lv15] Blaze 4,120❤"))
		assertEquals(1_200L to null, BlazeOrder.parseNametag("§8[§7Lv15§8] §cBlaze §a1.2k§c❤"))
		assertEquals(0L to null, BlazeOrder.parseNametag("[Lv15] Blaze 0❤"))
		assertNull(BlazeOrder.parseNametag("[Lv15] Zombie 100❤"))
	}

	@Test fun ordersByCurrentHealthWhenMaxMissing() {
		val noMax = listOf(b(1, 3_000).copy(maxHp = null), b(2, 1_000).copy(maxHp = null), b(3, 2_000).copy(maxHp = null))
		assertEquals(listOf(2, 3, 1), BlazeOrder.order(noMax, Direction.LOWEST_FIRST).map { it.id })
		assertEquals(listOf(1, 3, 2), BlazeOrder.order(noMax, Direction.HIGHEST_FIRST).map { it.id })

		val mixed = listOf(b(1, 6_000, hp = 1_500), b(2, 2_000).copy(maxHp = null), b(3, 3_000))
		assertEquals(listOf(1, 2, 3), BlazeOrder.order(mixed, Direction.LOWEST_FIRST).map { it.id })
		val t = BlazeOrder.Tracker().apply { direction = Direction.LOWEST_FIRST; observe(noMax) }
		assertEquals(2, t.current()?.id); assertEquals(3, t.next()?.id)
	}

	private fun tag(id: Int, hp: Long, x: Double, y: Double, z: Double = 0.0) = BlazeOrder.Tag(id, hp, hp, x, y, z)
	private fun body(id: Int, x: Double, y: Double, z: Double = 0.0) = BlazeOrder.Body(id, x, y, z, 1.8)

	@Test fun pairsStackedBlazesOneToOne() {

		val bodies = listOf(body(1, 0.0, 70.0), body(2, 0.6, 72.0))
		val tags = listOf(tag(101, 1_000, 0.5, 72.0), tag(102, 2_000, 0.6, 74.1))
		val pairs = BlazeOrder.pair(tags, bodies).associate { it.first.id to it.second.id }
		assertEquals(mapOf(101 to 1, 102 to 2), pairs)

		assertEquals(pairs, BlazeOrder.pair(tags.reversed(), bodies.reversed()).associate { it.first.id to it.second.id })

		assertEquals(listOf(101 to 1), BlazeOrder.pair(listOf(tag(101, 1_000, 0.0, 72.0), tag(103, 3_000, 0.3, 72.6)), listOf(body(1, 0.0, 70.0))).map { it.first.id to it.second.id })
	}

	@Test fun pairingUsesHeight() {

		val bodies = listOf(body(1, 0.0, 70.0), body(2, 0.0, 74.0))
		val pairs = BlazeOrder.pair(listOf(tag(101, 1_000, 0.0, 72.0)), bodies)
		assertEquals(listOf(101 to 1), pairs.map { it.first.id to it.second.id })

		assertTrue(BlazeOrder.pair(listOf(tag(103, 1_000, 5.0, 72.0)), bodies).isEmpty())
	}

	@Test fun zeroHpTagDoesNotKillNeighbour() {

		val owners = mapOf(101 to 1, 102 to 2)
		val bodies = listOf(body(2, 0.5, 70.0))
		assertTrue(BlazeOrder.pair(listOf(tag(101, 0, 0.0, 71.8)), bodies, owners).isEmpty())

		val both = BlazeOrder.pair(listOf(tag(101, 0, 0.5, 71.8), tag(102, 2_000, 0.9, 72.3)), bodies, owners)
		assertEquals(listOf(102 to 2), both.map { it.first.id to it.second.id })

		val own = BlazeOrder.pair(listOf(tag(102, 0, 0.5, 71.8)), bodies, owners)
		assertEquals(listOf(102 to 2), own.map { it.first.id to it.second.id })
		val t = tracker(); t.observe(room.map { if (it.id == 2) it.copy(hp = 0) else it })
		assertTrue(2 in t.dead); assertEquals(1, t.dead.size)
	}

	@Test fun roundedHealthTieWarnsAndBoxesBoth() {
		val tied = listOf(b(1, 1_200, y = 70.0), b(2, 1_200, y = 74.0), b(3, 2_500), b(4, 3_000))
		val t = BlazeOrder.Tracker().apply { direction = Direction.LOWEST_FIRST; observe(tied) }
		assertTrue(BlazeOrder.hasTie(tied))
		assertEquals(listOf(1, 2), t.tiedTargets().map { it.id })
		val plan = t.plan { true }
		assertTrue(plan.tie)
		assertEquals(setOf(1, 2), plan.boxes.toSet())
		assertNull(plan.next)
		assertFalse(plan.targetOutOfView)
		assertTrue(t.plan { it != 2 }.targetOutOfView)

		val later = BlazeOrder.Tracker().apply { direction = Direction.LOWEST_FIRST; observe(listOf(b(1, 1_000), b(2, 2_000), b(3, 2_000))) }
		assertFalse(later.plan { true }.tie)
	}

	@Test fun nextOnlyWhenTargetDrawn() {
		val t = tracker()
		val all = t.plan { true }
		assertEquals(listOf(2), all.boxes); assertEquals(4, all.next); assertFalse(all.targetOutOfView)
		val hidden = t.plan { it != 2 }
		assertTrue(hidden.boxes.isEmpty()); assertNull(hidden.next); assertTrue(hidden.targetOutOfView)
		assertEquals(BlazeOrder.Status.SOLVING, hidden.status)
		val nextHidden = t.plan { it != 4 }
		assertEquals(listOf(2), nextHidden.boxes); assertNull(nextHidden.next); assertFalse(nextHidden.targetOutOfView)
	}

	@Test fun planStates() {
		assertEquals(BlazeOrder.Status.IDLE, BlazeOrder.Tracker().plan { true }.status)
		assertEquals(BlazeOrder.Status.FINDING_DIRECTION, BlazeOrder.Tracker().apply { observe(room) }.plan { true }.status)
		val t = tracker(); t.observe(emptyList(), died = room.map { it.id }.toSet())
		assertEquals(BlazeOrder.Status.DONE, t.plan { true }.status)
	}

	@Test fun failMessageStopsHighlighting() {
		val t = tracker()
		assertFalse(t.onChat("PUZZLE FAIL! Steve was fooled by Ardis! Yikes!"))
		assertFalse(t.failed)
		assertTrue(t.onChat("PUZZLE FAIL! Steve killed a Blaze in the wrong order! Yikes!"))
		assertTrue(t.failed)
		val plan = t.plan { true }
		assertEquals(BlazeOrder.Status.FAILED, plan.status)
		assertTrue(plan.boxes.isEmpty()); assertNull(plan.next)
		t.observe(emptyList(), died = room.map { it.id }.toSet())
		assertFalse(t.takeDoneAnnouncement())
		t.reset(); assertFalse(t.failed)
	}
}
