package dev.ashtonite365.apex.test.features.kit.dungeon

import dev.ashtonite365.apex.features.kit.dungeon.mapDoors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.dungeon.DoorLogic
import dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.Cell
import dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.DoorType
import dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.Probe
import dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.Slot
import dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.SlotState

class DoorLogicTest {

	private class World(
		val slots: MutableMap<Slot, Probe> = mutableMapOf(), val void: MutableSet<Slot> = mutableSetOf(),
		val columns: MutableMap<Slot, (Int) -> Probe> = mutableMapOf(),
	) {
		fun probe(x: Int, y: Int, z: Int): Probe {
			val s = DoorLogic.SLOTS.firstOrNull { it.x == x && it.z == z } ?: return Probe.SOLID
			columns[s]?.let { return it(y) }
			val v = slots[s] ?: return Probe.AIR
			return when {
				y in DoorLogic.DOOR_Y -> v
				y == DoorLogic.FLOOR_Y && s !in void -> Probe.SOLID
				else -> Probe.AIR
			}
		}
		fun scan(prev: Map<Slot, SlotState> = emptyMap(), opened: Set<Slot> = emptySet()) = DoorLogic.scan(::probe, prev, opened)
	}

	private fun east(i: Int, j: Int) = Slot(Cell(i, j), true)
	private fun south(i: Int, j: Int) = Slot(Cell(i, j), false)

	@Test fun gridSlotsSitBetweenRooms() {
		assertEquals(60, DoorLogic.SLOTS.size)
		assertEquals(-185, Cell(0, 0).x)
		assertEquals(-169, east(0, 0).x); assertEquals(-185, east(0, 0).z)
		assertEquals(Cell(0, 0), DoorLogic.cellOf(-185.0, -185.0))
		assertEquals(east(0, 0), DoorLogic.slotNear(-169, -184))
		assertNull(DoorLogic.slotNear(-170, -185))
	}

	@Test fun voidGapBetweenUnconnectedRoomsIsNotADoorway() {
		val w = World(mutableMapOf(east(0, 0) to Probe.AIR, east(1, 0) to Probe.AIR), mutableSetOf(east(1, 0)))
		val states = w.scan()
		assertEquals(SlotState.OPEN, states[east(0, 0)])
		assertEquals(SlotState.WALL, states[east(1, 0)])
		assertEquals(setOf(Cell(0, 0), Cell(1, 0)), DoorLogic.reachable(states, Cell(0, 0)).keys)
	}

	private val run = mutableMapOf(
		east(0, 0) to Probe.AIR, east(1, 0) to Probe.COAL, south(2, 0) to Probe.AIR, east(1, 1) to Probe.AIR,
		east(0, 1) to Probe.AIR, south(0, 1) to Probe.BLOOD,
	)
	private val px = -185.0; private val pz = -171.0

	@Test fun highlightsOnlyTheExposedDoorNotTheCloserBloodDoor() {
		val states = World(run.toMutableMap()).scan()
		assertEquals(2, DoorLogic.lockedDoors(states).size)
		val blood = DoorLogic.lockedDoors(states).single { it.type == DoorType.BLOOD }
		val wither = DoorLogic.lockedDoors(states).single { it.type == DoorType.WITHER }

		fun d2(d: DoorLogic.Door) = (d.slot.x + 0.5 - px).let { it * it } + (d.slot.z + 0.5 - pz).let { it * it }
		assertTrue(d2(blood) < d2(wither))

		assertEquals(listOf(wither), DoorLogic.exposedDoors(states, DoorLogic.reachable(states, Cell(0, 0))))
		assertEquals(wither, DoorLogic.nextDoor(states, px, pz))
		assertEquals(wither, DoorLogic.nextDoor(states, px, pz, DoorType.BLOOD))
	}

	@Test fun bloodDoorBecomesNextOnceTheWitherDoorIsOpened() {
		val w = World(run.toMutableMap())
		val first = w.scan()
		w.slots[east(1, 0)] = Probe.AIR
		val states = w.scan(first)
		assertEquals(SlotState.OPEN, states[east(1, 0)])
		val next = DoorLogic.nextDoor(states, px, pz)!!
		assertEquals(DoorType.BLOOD, next.type); assertEquals(south(0, 1), next.slot)
	}

	@Test fun openedDoorIsSkippedEvenWhileAnimationBlocksAreInIt() {
		val w = World(run.toMutableMap())

		val states = w.scan(opened = setOf(east(1, 0)))
		assertEquals(SlotState.OPEN, states[east(1, 0)])
		assertTrue(DoorLogic.lockedDoors(states).none { it.slot == east(1, 0) })
		assertEquals(south(0, 1), DoorLogic.nextDoor(states, px, pz)!!.slot)

		assertEquals(SlotState.OPEN, w.scan(states, setOf(east(1, 0)))[east(1, 0)])
	}

	@Test fun nothingRatherThanAWrongDoor() {

		val states = World(mutableMapOf(east(3, 3) to Probe.COAL, south(5, 4) to Probe.BLOOD)).scan()
		assertNull(DoorLogic.nextDoor(states, px, pz))

		assertNull(DoorLogic.nextDoor(World(run.toMutableMap()).scan(), 100.0, 100.0))
	}

	@Test fun heldKeyTypeWinsAmongExposedDoors() {

		val states = World(mutableMapOf(east(0, 0) to Probe.COAL, south(0, 0) to Probe.BLOOD)).scan()
		assertEquals(DoorType.WITHER, DoorLogic.nextDoor(states, px, pz)!!.type)
		assertEquals(DoorType.BLOOD, DoorLogic.nextDoor(states, px, pz, DoorType.BLOOD)!!.type)
	}

	@Test fun unloadedSlotKeepsLastState() {
		val w = World(mutableMapOf(south(5, 4) to Probe.BLOOD))
		val first = w.scan()
		w.slots[south(5, 4)] = Probe.UNLOADED
		assertEquals(SlotState.BLOOD, w.scan(first)[south(5, 4)])
	}

	@Test fun routeAreaCoversTheRoomsOnTheWalk() {
		val w = World(run.toMutableMap())
		val states = w.scan(opened = setOf(east(1, 0)))
		val blood = DoorLogic.lockedDoors(states).single()
		assertEquals(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1), Cell(1, 1), Cell(0, 1)), DoorLogic.route(states, Cell(0, 0), blood))
		val a = DoorLogic.routeArea(states, px, pz, blood)!!
		assertEquals(-201, a.minX); assertEquals(-201, a.minZ)
		assertEquals(-200 + 3 * 32, a.maxX); assertEquals(-200 + 2 * 32, a.maxZ)
		assertTrue(blood.slot.x in a.minX..a.maxX && blood.slot.z in a.minZ..a.maxZ)

		assertNull(DoorLogic.routeArea(World(mutableMapOf(east(2, 1) to Probe.BLOOD)).scan(), px, pz, Door(east(2, 1))))
	}

	private fun Door(slot: Slot) = DoorLogic.Door(slot, DoorType.BLOOD)

	@Test fun keyStateAndOpenedChat() {
		val k = DoorLogic.KeyState()
		assertNull(k.held())
		k.onChat("[MVP+] Ashton has obtained Blood Key!")
		assertEquals(DoorType.BLOOD, k.held())
		k.onChat("The BLOOD DOOR has been opened!")
		assertNull(k.held())
		k.onChat("A Wither Key was picked up!")
		assertEquals(DoorType.WITHER, k.held())
		k.onChat("Ashton opened a WITHER door!")
		assertNull(k.held())
		assertEquals(DoorType.WITHER, DoorLogic.openedByChat("Ashtonite365 opened a WITHER door!"))
		assertEquals(DoorType.BLOOD, DoorLogic.openedByChat("The BLOOD DOOR has been opened!"))
		assertNull(DoorLogic.openedByChat("RIGHT CLICK on the BLOOD DOOR to open it. This key can only be used to open 1 door!"))

		val w = World(mutableMapOf(east(0, 0) to Probe.AIR))
		assertNotNull(w.scan()); assertTrue(DoorLogic.lockedDoors(w.scan()).isEmpty())
		assertFalse(DoorLogic.SLOTS.isEmpty())
	}

	private val oneByTwo = mutableMapOf(east(0, 0) to Probe.SOLID, east(1, 0) to Probe.COAL)

	@Test fun mergedRoomSeparatorWithSomethingInItIsOpen() {
		val states = World(oneByTwo.toMutableMap()).scan()
		assertEquals(SlotState.OPEN, states[east(0, 0)])
		val next = DoorLogic.nextDoor(states, px, pz, DoorType.WITHER)
		assertEquals(east(1, 0), next?.slot)
		assertNotNull(DoorLogic.routeArea(states, px, pz, next!!))
		assertEquals("ok", DoorLogic.diagnose(states, px, pz, DoorType.WITHER).reason)
	}

	@Test fun mergedRoomSeparatorOverAPitAndTheEntranceGateAreOpen() {

		val pit = World(columns = mutableMapOf(east(0, 0) to { y: Int -> if (y == 60) Probe.SOLID else Probe.AIR }))
		assertEquals(SlotState.OPEN, pit.scan()[east(0, 0)])

		val gate = World(columns = mutableMapOf(south(0, 0) to { y: Int -> if (y <= 69) Probe.SOLID else Probe.AIR }))
		assertEquals(SlotState.OPEN, gate.scan()[south(0, 0)])

		assertEquals(SlotState.WALL, pit.scan()[east(2, 0)])
		val half = World(columns = mutableMapOf(east(2, 0) to { y: Int -> if (y < 40) Probe.UNLOADED else Probe.AIR }))
		assertEquals(SlotState.UNKNOWN, DoorLogic.classify(east(2, 0), half::probe))
	}

	@Test fun diagnoseExplainsAMissingDoor() {
		val cutOff = World(mutableMapOf(east(1, 0) to Probe.COAL)).scan()
		val d = DoorLogic.diagnose(cutOff, px, pz, DoorType.WITHER)
		assertNull(d.door)
		assertEquals(1, d.locked); assertEquals(1, d.reach); assertEquals(0, d.exposed)
		assertEquals("no exposed door (reach=1 rooms)", d.reason)
		assertEquals("player outside the room grid", DoorLogic.diagnose(cutOff, 100.0, 100.0).reason)
		assertTrue(d.toString().contains("locked=1"))
	}

	@Test fun openDoorwayWithCoalIsNotWither() {

		val lintel = World(columns = mutableMapOf(east(0, 0) to { y: Int ->
			when { y == 72 -> Probe.COAL; y == DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } }))
		assertEquals(SlotState.OPEN, lintel.scan()[east(0, 0)])
		assertTrue(DoorLogic.lockedDoors(lintel.scan()).isEmpty())

		val low = World(columns = mutableMapOf(east(0, 0) to { y: Int ->
			when (y) { 69 -> Probe.COAL; 70 -> Probe.PASSABLE; 71 -> Probe.AIR; 72 -> Probe.COAL; DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } }))
		assertEquals(SlotState.OPEN, low.scan()[east(0, 0)])

		assertEquals(setOf(Cell(0, 0), Cell(1, 0)), DoorLogic.reachable(lintel.scan(), Cell(0, 0)).keys)
	}

	@Test fun filledCoalDoorwayIsStillWither() {

		assertEquals(SlotState.WITHER, World(mutableMapOf(east(0, 0) to Probe.COAL)).scan()[east(0, 0)])

		val oneHigh = World(columns = mutableMapOf(east(0, 0) to { y: Int ->
			when (y) { 69, 71 -> Probe.COAL; 70, 72 -> Probe.AIR; DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } }))
		assertEquals(SlotState.WITHER, oneHigh.scan()[east(0, 0)])

		val mixed = World(columns = mutableMapOf(east(0, 0) to { y: Int ->
			when (y) { 69, 70 -> Probe.COAL; 71 -> Probe.SOLID; 72 -> Probe.AIR; DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } }))
		assertEquals(SlotState.WITHER, mixed.scan()[east(0, 0)])

		val bloodOpen = World(columns = mutableMapOf(east(0, 0) to { y: Int ->
			when (y) { 72 -> Probe.BLOOD; DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } }))
		assertEquals(SlotState.BLOOD, bloodOpen.scan()[east(0, 0)])
	}

	@Test fun closedCoalDoorIntoFairyRoomIsWither() {

		val w = World(mutableMapOf(east(0, 0) to Probe.COAL))
		val fairy = DoorLogic.scan(w::probe, context = { s -> DoorLogic.SlotContext(fairyBeyond = s == east(0, 0)) })
		assertEquals(SlotState.WITHER, fairy[east(0, 0)])
		assertEquals(listOf(DoorLogic.Door(east(0, 0), DoorLogic.DoorType.WITHER)), DoorLogic.lockedDoors(fairy))
	}

	@Test fun blocksDecideWitherNotTheMapPixel() {
		val w = World(mutableMapOf(east(0, 0) to Probe.COAL))
		fun at(pixel: Int?) = DoorLogic.scan(w::probe, context = { DoorLogic.SlotContext(mapPixel = pixel) })[east(0, 0)]
		assertEquals(SlotState.WITHER, at(DoorLogic.MAP_WITHER))
		assertEquals(SlotState.WITHER, at(null))
		assertEquals(SlotState.WITHER, at(82))
		assertEquals(SlotState.WITHER, at(63))

		val open = World(columns = mutableMapOf(east(0, 0) to { y: Int ->
			when { y == 72 -> Probe.COAL; y == DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } }))
		assertEquals(SlotState.OPEN, DoorLogic.scan(open::probe, context = { DoorLogic.SlotContext(mapPixel = DoorLogic.MAP_WITHER) })[east(0, 0)])

		assertEquals(false, DoorLogic.isWitherDoor(coal = false, open = false) { error("asked") })
		assertEquals(false, DoorLogic.isWitherDoor(coal = true, open = true) { error("asked") })
		assertTrue(DoorLogic.isWitherDoor(coal = true, open = false) { DoorLogic.SlotContext(fairyBeyond = true) })
	}

	@Test fun keyPointerTargetsFairyRoomWitherDoor() {

		val states = DoorLogic.SLOTS.associateWith { SlotState.WALL }.toMutableMap()
		states[DoorLogic.Slot(DoorLogic.Cell(0, 2), false)] = SlotState.OPEN
		states[DoorLogic.Slot(DoorLogic.Cell(0, 3), false)] = SlotState.WITHER
		states[DoorLogic.Slot(DoorLogic.Cell(0, 2), true)] = SlotState.OPEN
		states[DoorLogic.Slot(DoorLogic.Cell(1, 2), true)] = SlotState.OPEN
		states[DoorLogic.Slot(DoorLogic.Cell(2, 2), false)] = SlotState.WITHER
		val c = DoorLogic.Cell(0, 2)
		val door = DoorLogic.nextDoor(states, c.x + 0.5, c.z + 0.5, DoorLogic.DoorType.WITHER)
		assertEquals(DoorLogic.Door(DoorLogic.Slot(DoorLogic.Cell(0, 3), false), DoorLogic.DoorType.WITHER), door)

		states[DoorLogic.Slot(DoorLogic.Cell(0, 3), false)] = SlotState.OPEN
		assertEquals(DoorLogic.Slot(DoorLogic.Cell(2, 2), false), DoorLogic.nextDoor(states, c.x + 0.5, c.z + 0.5, DoorLogic.DoorType.WITHER)?.slot)

		states[DoorLogic.Slot(DoorLogic.Cell(2, 2), false)] = SlotState.OPEN
		assertEquals(null, DoorLogic.nextDoor(states, c.x + 0.5, c.z + 0.5, DoorLogic.DoorType.WITHER))
	}

	@Test fun singleDecorativeCoalIsNotAWitherDoor() {

		val w = World(columns = mutableMapOf(east(0, 0) to { y: Int ->
			when (y) { 70 -> Probe.COAL; in DoorLogic.DOOR_Y -> Probe.SOLID; DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } }))
		assertEquals(SlotState.OPEN, w.scan()[east(0, 0)])
		assertTrue(DoorLogic.mapDoors(w.scan()).isEmpty())
	}

	@Test fun postFairyWitherDoorIsShownTargetedAndRouted() {

		val fairyEntry = DoorLogic.Slot(DoorLogic.Cell(0, 0), false)
		val fairyExit = DoorLogic.Slot(DoorLogic.Cell(0, 1), false)
		val w = World(columns = mutableMapOf(
			fairyEntry to { y: Int -> when { y == 72 -> Probe.COAL; y == DoorLogic.FLOOR_Y -> Probe.SOLID; else -> Probe.AIR } },
			fairyExit to { y: Int -> if (y in DoorLogic.DOOR_Y) Probe.COAL else if (y == DoorLogic.FLOOR_Y) Probe.SOLID else Probe.AIR },
		))
		val states = DoorLogic.scan(w::probe, context = { s -> DoorLogic.SlotContext(fairyBeyond = true, mapPixel = 82) })
		assertEquals(SlotState.OPEN, states[fairyEntry])
		assertEquals(SlotState.WITHER, states[fairyExit])
		assertEquals(119, DoorLogic.mapDoors(states)[dev.ashtonite365.apex.features.dungeon.DungeonMap.DoorKey(0, 1, false)])
		val p = DoorLogic.Cell(0, 0)
		val door = DoorLogic.nextDoor(states, p.x + 0.5, p.z + 0.5, DoorType.WITHER)
		assertEquals(DoorLogic.Door(fairyExit, DoorType.WITHER), door)
		assertEquals(listOf(DoorLogic.Cell(0, 0), DoorLogic.Cell(0, 1)), DoorLogic.route(states, p, door!!))
		assertNotNull(DoorLogic.routeArea(states, p.x + 0.5, p.z + 0.5, door))
	}

	@Test fun mapDoorsOverlayOnlyRealLockedDoors() {
		val states = mapOf(
			DoorLogic.Slot(DoorLogic.Cell(0, 3), false) to SlotState.WITHER,
			DoorLogic.Slot(DoorLogic.Cell(1, 1), true) to SlotState.BLOOD,
			DoorLogic.Slot(DoorLogic.Cell(2, 2), true) to SlotState.OPEN,
			DoorLogic.Slot(DoorLogic.Cell(3, 3), true) to SlotState.UNKNOWN,
		)
		assertEquals(mapOf(
			dev.ashtonite365.apex.features.dungeon.DungeonMap.DoorKey(0, 3, false) to 119,
			dev.ashtonite365.apex.features.dungeon.DungeonMap.DoorKey(1, 1, true) to 18,
		), DoorLogic.mapDoors(states))
	}

	@Test fun openDoorwayDefinition() {
		assertTrue(DoorLogic.isOpenDoorway { it == 70 || it == 71 })
		assertTrue(DoorLogic.isOpenDoorway { it >= 71 })
		assertFalse(DoorLogic.isOpenDoorway { it == 69 || it == 71 })
		assertFalse(DoorLogic.isOpenDoorway { false })
		assertFalse(DoorLogic.isOpenDoorway { it == 72 || it == 73 })
		assertTrue(DoorLogic.isWalkable(Probe.PASSABLE)); assertTrue(DoorLogic.isWalkable(Probe.AIR))
		assertFalse(DoorLogic.isWalkable(Probe.COAL)); assertFalse(DoorLogic.isWalkable(Probe.SOLID))
	}
}
