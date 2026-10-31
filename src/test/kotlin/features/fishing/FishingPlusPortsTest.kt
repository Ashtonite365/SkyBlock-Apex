// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.fishing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.fishing.FishingPlusPorts

class FishingPlusPortsTest {
	@Test
	fun chumTags() {
		assertTrue(FishingPlusPorts.isChumTag("Bob's Chum Bucket", "Ashton", false))
		assertFalse(FishingPlusPorts.isChumTag("Ashton's Chum Bucket", "Ashton", false))
		assertTrue(FishingPlusPorts.isChumTag("Ashton's Chumcap Bucket", "Ashton", true))
		assertTrue(FishingPlusPorts.isChumTag("3/10 Chums", "Ashton", false))
		assertFalse(FishingPlusPorts.isChumTag("Squid", "Ashton", false))
	}

	@Test
	fun banshee() {
		assertTrue(FishingPlusPorts.isBansheePitch(0.2857143f))
		assertFalse(FishingPlusPorts.isBansheePitch(1.0f))
	}
}
