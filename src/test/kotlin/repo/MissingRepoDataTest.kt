// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import io.github.moulberry.repo.data.Rarity
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.rift.RiftRepo
import dev.ashtonite365.apex.repo.ExpLadders
import dev.ashtonite365.apex.repo.SafeRepoConstants

class MissingRepoDataTest {
	@Test
	fun emptyLadderGivesUnknownLevel() {
		val level = ExpLadders.ExpLadder(listOf()).getPetLevel(123456.0)
		assertFalse(level.known)
		assertEquals("?", level.levelText)
		assertEquals(0L, ExpLadders.ExpLadder(listOf()).getPetExpForLevel(50))
	}

	@Test
	fun knownLadderStillShowsTheNumber() {
		val level = ExpLadders.ExpLadder(listOf(100L, 200L, 300L)).getPetLevel(150.0)
		assertTrue(level.known)
		assertEquals(2, level.currentLevel)
		assertEquals("2", level.levelText)
	}

	@Test
	fun missingRepoGivesAnEmptyLadderNotACrash() {

		val ladder = ExpLadders.getExpLadder("NO_SUCH_PET", Rarity.LEGENDARY)
		val text = ladder.getPetLevel(1_000_000.0).levelText
		assertTrue(text == "?" || text.toIntOrNull() != null, text)
		assertNull(ExpLadders.maxLevelOverride("NO_SUCH_PET"))
	}

	@Test
	fun missingConstantsAreNullOrEmpty() {
		assertNull(SafeRepoConstants.leveling(null))
		assertNull(SafeRepoConstants.enchants(null))
		assertTrue(SafeRepoConstants.petNumbers(null).isEmpty())
		assertTrue(SafeRepoConstants.essenceCosts(null).isEmpty())
	}

	@Test
	fun riftRepoDataWithMissingFieldsIsSkipped() {
		val j = { s: String -> Json.parseToJsonElement(s) }
		assertTrue(RiftRepo.enigma(j("{}")).isEmpty())
		assertTrue(RiftRepo.buttons(j("{}")).isEmpty())
		assertEquals(mapOf("a" to emptyMap<String, Any>()), RiftRepo.enigma(j("""{"areas":{"a":[{"name":"x"},{"position":"1:2:3"}]}}""")))
		assertTrue(RiftRepo.berberis(j("""{"field_centers":[{"position":"1:2:3"},{"count":2}]}""")).isEmpty())
		assertEquals(listOf<Pair<Int, Int>>(), RiftRepo.parkour(j("""{"locations":[],"shortCuts":[{"from":1}]}""")).shortCuts)
	}
}
