// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.features.inventory.GlobalSlotLocks
import dev.ashtonite365.apex.features.inventory.SlotLockStore
import dev.ashtonite365.apex.features.inventory.SlotLocking
import dev.ashtonite365.apex.features.inventory.SlotBinding
import dev.ashtonite365.apex.features.inventory.SlotBindings
import dev.ashtonite365.apex.features.inventory.LegacyIslandLocks
import dev.ashtonite365.apex.features.inventory.LegacyProfileLocks
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass

class SlotLockStoreTest {
	@TempDir
	lateinit var tmp: Path

	private fun dim(locked: Set<Int>, binds: Set<Pair<Int, Int>> = setOf()) = LegacyIslandLocks(
		locked.toMutableSet(),
		SlotBindings(binds.map { SlotBinding(it.first, it.second) }.toMutableSet()),
	)

	@Test
	fun locksAreKeyedGloballyNotByProfileOrIsland() {
		val holder = SlotLocking.GlobalConfig
		assertEquals(ConfigStorageClass.STORAGE, holder.storageClass, "global store, not a per-profile holder")
		holder.explicitDefaultLoad()
		assertEquals(listOf(Unit), holder.keys().toList(), "exactly one key: no profile / island split")
		holder.data.lockedSlots.add(12)

		assertTrue(12 in holder.data.lockedSlots)
		val fields = GlobalSlotLocks::class.java.declaredFields.map { it.name }.toSet()
		assertFalse("rift" in fields || "overworld" in fields, "no per-dimension buckets: $fields")
		holder.explicitDefaultLoad()
	}

	@Test
	fun saveWritesImmediatelyAndRoundTrips() {
		val file = tmp.resolve("storage").resolve("global-slot-locks.json")
		val uuid = UUID.fromString("0a1b2c3d-0000-4000-8000-000000000001")
		val data = GlobalSlotLocks()
		data.lockedSlots.add(8)
		SlotLockStore.save(file, data)
		assertTrue(Files.exists(file), "written at once, no debounce")
		assertEquals(setOf(8), SlotLockStore.load(file)!!.lockedSlots)

		data.lockedSlots.remove(8)
		data.lockedSlots.add(35)
		data.boundSlots.pairs.add(SlotBinding(0, 27))
		data.lockedUUIDs.add(uuid)
		data.migratedFromPerIsland = true
		SlotLockStore.save(file, data)
		assertFalse(Files.exists(file.resolveSibling("global-slot-locks.json.tmp")), "temp file moved into place")

		val reloaded = SlotLockStore.load(file)!!
		assertEquals(data, reloaded)
		assertEquals(setOf(35), reloaded.lockedSlots)
		assertEquals(setOf(SlotBinding(0, 27)), reloaded.boundSlots.pairs)
		assertEquals(setOf(uuid), reloaded.lockedUUIDs)
		assertTrue(reloaded.migratedFromPerIsland)
		assertNull(SlotLockStore.load(tmp.resolve("missing.json")))
	}

	@Test
	fun mergeUnionsEveryIslandAndProfile() {
		val u1 = UUID.randomUUID()
		val u2 = UUID.randomUUID()
		val profileA = LegacyProfileLocks(mutableSetOf(u1), rift = dim(setOf(3), setOf(1 to 20)), overworld = dim(setOf(8, 9), setOf(0 to 27, 1 to 28)))
		val profileB = LegacyProfileLocks(mutableSetOf(u2), rift = dim(setOf()), overworld = dim(setOf(9, 17), setOf(2 to 29)))
		val (merged, report) = SlotLockStore.mergeLegacy(listOf(profileA, profileB))
		assertEquals(setOf(3, 8, 9, 17), merged.lockedSlots, "locked on any island -> locked")
		assertEquals(setOf(u1, u2), merged.lockedUUIDs)
		assertEquals(
			setOf(SlotBinding(0, 27), SlotBinding(1, 28), SlotBinding(1, 20), SlotBinding(2, 29)),
			merged.boundSlots.pairs,
		)
		assertEquals(0, report.droppedConflictingBinds)
		assertTrue(merged.migratedFromPerIsland)
	}

	@Test
	fun mergeKeepsOneBindPerInventorySlotOverworldFirst() {
		val p = LegacyProfileLocks(rift = dim(setOf(), setOf(5 to 27)), overworld = dim(setOf(), setOf(0 to 27)))
		val (merged, report) = SlotLockStore.mergeLegacy(listOf(p))
		assertEquals(setOf(SlotBinding(0, 27)), merged.boundSlots.pairs)
		assertEquals(1, report.droppedConflictingBinds)
	}

	@Test
	fun migrationRunsOnceFromDiskAndKeepsOldFiles() {
		val profiles = tmp.resolve("config/apex/profiles")
		val pa = profiles.resolve("11111111-1111-1111-1111-111111111111")
		val pNil = profiles.resolve("00000000-0000-0000-0000-000000000000")
		Files.createDirectories(pa); Files.createDirectories(pNil)
		val oldA = """{"lockedUUIDs":[],"rift":{"lockedSlots":[4],"boundSlots":[]},"overworld":{"lockedSlots":[8],"boundSlots":[{"hotbar":0,"inventory":27}]}}"""

		val oldNil = """{"lockedUUIDs":[],"rift":{"lockedSlots":[],"boundSlots":{}},"overworld":{"lockedSlots":[30],"boundSlots":{"2":29}}}"""
		Files.writeString(pa.resolve("locked-slots.json"), oldA)
		Files.writeString(pNil.resolve("locked-slots.json"), oldNil)
		Files.writeString(pa.resolve("other.json"), "{}")
		val file = tmp.resolve("config/apex/storage/global-slot-locks.json")
		val backup = tmp.resolve("backups/apex/slot-locks-per-island-1")

		val current = GlobalSlotLocks()
		val report = SlotLockStore.migrateOnce(current, file, profiles, backup)
		assertNotNull(report)
		assertEquals(setOf(4, 8, 30), current.lockedSlots)
		assertEquals(setOf(SlotBinding(0, 27), SlotBinding(2, 29)), current.boundSlots.pairs)
		assertEquals(current, SlotLockStore.load(file), "merged set saved at once")

		assertEquals(oldA, Files.readString(pa.resolve("locked-slots.json")))
		assertEquals(oldA, Files.readString(backup.resolve(pa.fileName.toString()).resolve("locked-slots.json")))
		assertEquals(oldNil, Files.readString(backup.resolve(pNil.fileName.toString()).resolve("locked-slots.json")))

		val reloaded = SlotLockStore.load(file)!!
		reloaded.lockedSlots.remove(4)
		SlotLockStore.save(file, reloaded)
		val again = SlotLockStore.load(file)!!
		assertNull(SlotLockStore.migrateOnce(again, file, profiles, tmp.resolve("backups/apex/slot-locks-per-island-2")))
		assertEquals(setOf(8, 30), again.lockedSlots)
		assertFalse(Files.exists(tmp.resolve("backups/apex/slot-locks-per-island-2")))
	}

	@Test
	fun migrationWithNoOldDataJustSetsTheFlag() {
		val current = GlobalSlotLocks()
		val file = tmp.resolve("g.json")
		val report = SlotLockStore.migrateOnce(current, file, tmp.resolve("nope"), tmp.resolve("bk"))
		assertEquals(0, report!!.lockedSlots)
		assertTrue(SlotLockStore.load(file)!!.migratedFromPerIsland)
	}
}
