// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.events

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.events.EventPlusPorts

class EventPlusPortsTest {
	@Test
	fun buggedSpade() {
		assertTrue(EventPlusPorts.isBuggedSpadeSound("music_disc.cat", 0.5f, 1f, 3.0, 70.0, 5.0))
		assertFalse(EventPlusPorts.isBuggedSpadeSound("music.game", 1f, 1f, 0.0, 0.0, 0.0))
		assertFalse(EventPlusPorts.isBuggedSpadeSound("block.note_block.bit", 0.5f, 1f, 3.0, 70.0, 5.0))
	}

	@Test
	fun mythicRabbit() {
		assertFalse(EventPlusPorts.needsMythicRabbit("Rabbit", "MYTHIC"))
		assertTrue(EventPlusPorts.needsMythicRabbit("Rabbit", "LEGENDARY"))
		assertTrue(EventPlusPorts.needsMythicRabbit("Griffin", "MYTHIC"))
	}
}
