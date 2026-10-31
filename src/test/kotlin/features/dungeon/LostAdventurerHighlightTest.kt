// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.kit.puzzles.StarredMobs
import dev.ashtonite365.apex.features.kit.puzzles.StarredMobs.Kind
import dev.ashtonite365.apex.gui.config.KeyBindingHandler
import dev.ashtonite365.apex.gui.config.RiskLevel

class LostAdventurerHighlightTest {
	private val tag = "✯ Lost Adventurer 1.5M❤"

	@Test
	fun lostAdventurerNames() {
		assertTrue(StarredMobs.isLostAdventurer(tag))
		assertTrue(StarredMobs.isLostAdventurer("Lost Adventurer"))
		assertTrue(StarredMobs.isLostAdventurer("[Lv80] Lost Adventurer 400k❤"))
		assertFalse(StarredMobs.isLostAdventurer("✯ Frozen Adventurer 2M❤"))
		assertFalse(StarredMobs.isLostAdventurer("✯ Angry Archaeologist 3M❤"))
		assertFalse(StarredMobs.isLostAdventurer("✯ Zombie 50k❤"))
	}

	@Test
	fun optionWinsAndEachWorksAlone() {

		assertEquals(Kind.LOST_ADVENTURER, StarredMobs.highlightKind(tag, starred = false, strict = false, lostAdventurer = true))
		assertNull(StarredMobs.highlightKind("✯ Zombie 50k❤", starred = false, strict = false, lostAdventurer = true))

		assertEquals(Kind.LOST_ADVENTURER, StarredMobs.highlightKind(tag, starred = true, strict = true, lostAdventurer = true))
		assertEquals(Kind.STARRED, StarredMobs.highlightKind("✯ Zombie 50k❤", starred = true, strict = true, lostAdventurer = true))

		assertEquals(Kind.STARRED, StarredMobs.highlightKind(tag, starred = true, strict = true, lostAdventurer = false))
		assertNull(StarredMobs.highlightKind(tag, starred = false, strict = false, lostAdventurer = false))

		assertEquals(Kind.LOST_ADVENTURER, StarredMobs.highlightKind("Lost Adventurer 900k❤", starred = true, strict = true, lostAdventurer = true))
		assertNull(StarredMobs.highlightKind("Lost Adventurer 900k❤", starred = true, strict = true, lostAdventurer = false))
	}

	@Test
	fun riskyOffByDefaultInThePuzzleSolversConfig() {
		val c = PuzzleSolvers.TConfig
		c.explicitDefaultLoad()
		val opt = c.allOptions.getValue("lost-adventurer")
		assertEquals(RiskLevel.RISKY, opt.riskLevel)
		assertEquals(false, opt.default())
		assertFalse(c.lostAdventurer)
		assertFalse(c.allOptions.getValue("lost-adventurer-colour").riskLevel.isRisky)
		assertTrue(c.sortedOptions.filter { it.propertyName.startsWith("lost-adventurer") }.none { it.handler is KeyBindingHandler })

		assertEquals("puzzle-solvers", c.name)
	}

	@Test
	fun settingsRows() {
		DungeonExtrasMeta.assertRows(PuzzleSolvers.TConfig, "lost-adventurer", "riskyToggle", risky = true)
		DungeonExtrasMeta.assertRows(PuzzleSolvers.TConfig, "lost-adventurer-colour", "colour", risky = false)
	}
}
