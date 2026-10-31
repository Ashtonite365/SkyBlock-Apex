
package dev.ashtonite365.apex.test.gui.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.hud.HudContentTransform
import dev.ashtonite365.apex.gui.hud.MoulConfigHud

class MoulConfigHudNameTest {
	@Test fun names() {
		for (ok in listOf("anniversary_pig", "pristine_profit", "a/b_c", "_", "/")) assertEquals(true, MoulConfigHud.isValidName(ok), ok)
		for (bad in listOf("", "Pig", "pig1", "pig-cooldown", "pig cooldown", "pig\n", "pig.xml")) assertEquals(false, MoulConfigHud.isValidName(bad), bad)
	}

	@Test fun mouseMapping() {
		val t = HudContentTransform(10, 20, 2f, 2f)
		assertEquals(5f, t.localX(20f)); assertEquals(-5f, t.localY(10f)); assertEquals(20f, t.screenX(5f))
	}
}
