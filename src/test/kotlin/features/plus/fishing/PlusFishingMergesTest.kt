package dev.ashtonite365.apex.test.features.plus.fishing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.fishing.PlusFishingMerges

class PlusFishingMergesTest {
	@Test
	fun merges() {
		assertTrue(PlusFishingMerges.timeAlert(3, 300, 300))
		assertFalse(PlusFishingMerges.timeAlert(3, 300, 0))
		assertFalse(PlusFishingMerges.timeAlert(0, 999, 60))
		assertTrue(PlusFishingMerges.baitChanged("Fish Bait", "Dark Bait"))
		assertTrue(PlusFishingMerges.baitChanged("Fish Bait", null))
		assertFalse(PlusFishingMerges.baitChanged(null, "Fish Bait"))
		assertTrue(PlusFishingMerges.hideTrophy("BRONZE", 10, 5, 0))
		assertFalse(PlusFishingMerges.hideTrophy("SILVER", 10, 5, 0))
		assertFalse(PlusFishingMerges.hideTrophy("DIAMOND", 99, 1, 1))
	}
}

class PlusLavaReplacementTest {
	@Test
	fun islands() {
		val f = dev.ashtonite365.apex.features.plus.fishing.PlusLavaReplacement
		val crimson = dev.ashtonite365.apex.util.SkyBlockIsland.CRIMSON_ISLE
		val kuudra = dev.ashtonite365.apex.util.SkyBlockIsland.KUUDRA
		assertTrue(f.shouldReplace(true, true, crimson, false, false, false))
		assertFalse(f.shouldReplace(false, true, crimson, true, true, true))
		assertFalse(f.shouldReplace(true, true, kuudra, false, false, false))
		assertTrue(f.shouldReplace(true, true, kuudra, false, true, false))
	}
}
