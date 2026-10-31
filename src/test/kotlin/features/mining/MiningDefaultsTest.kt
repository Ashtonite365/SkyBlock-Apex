// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.mining

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.mining.CrystalHollowsOverlay
import dev.ashtonite365.apex.features.mining.MiningOverlay
import dev.ashtonite365.apex.features.mining.PickaxeAbility
import dev.ashtonite365.apex.features.mining.PowderGrindingOverlay
import dev.ashtonite365.apex.features.mining.PristineProfitTracker
import dev.ashtonite365.apex.util.data.ManagedConfig

class MiningDefaultsTest {
	private fun default(c: ManagedConfig, key: String): Any? {
		c.explicitDefaultLoad()
		return c.allOptions.getValue(key).default()
	}

	@Test
	fun mainHudsDefaultOn() {
		assertEquals(true, default(MiningOverlay.TConfig, "enabled"))
		assertEquals(true, default(PowderGrindingOverlay.TConfig, "enabled"))
		assertEquals(true, default(CrystalHollowsOverlay.TConfig, "enabled"))
		assertEquals(true, default(PickaxeAbility.TConfig, "ability-cooldown"))
		assertEquals(true, default(PristineProfitTracker.TConfig, "enabled"))
		for (c in listOf(MiningOverlay.TConfig, PowderGrindingOverlay.TConfig, CrystalHollowsOverlay.TConfig, PickaxeAbility.TConfig, PristineProfitTracker.TConfig))
			for (o in c.sortedOptions) if (o.riskLevel.isRisky) assertFalse(o.default() as Boolean, "${c.name}.${o.propertyName} ⚠ stays off")
	}
}
