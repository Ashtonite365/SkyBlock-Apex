package dev.ashtonite365.apex.test.features.storage

import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageClickLogic
import dev.ashtonite365.apex.test.testutil.RepoFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoragePageClickTest {
	private val rects = listOf(StorageClickLogic.PageRect(10, 10, 166, 60, "EC1"), StorageClickLogic.PageRect(180, 10, 166, 60, "BP1"))
	private val panel = intArrayOf(0, 0, 400, 300)

	@Test fun leftClickOpensThePageUnderTheMouse() {
		assertEquals(1, InputConstants.MOUSE_BUTTON_LEFT)
		assertEquals("BP1", StorageClickLogic.pageToOpen(rects, panel, 200.0, 20.0, InputConstants.MOUSE_BUTTON_LEFT, null))
		assertEquals("EC1", StorageClickLogic.pageToOpen(rects, panel, 20.0, 20.0, InputConstants.MOUSE_BUTTON_LEFT, "BP1"))
	}

	@Test fun packetButtonZeroRightClickAndOpenPageDoNothing() {
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 200.0, 20.0, 0, null))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 200.0, 20.0, InputConstants.MOUSE_BUTTON_RIGHT, null))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 200.0, 20.0, InputConstants.MOUSE_BUTTON_LEFT, "BP1"))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 500.0, 20.0, InputConstants.MOUSE_BUTTON_LEFT, null))
	}

	@Test fun overlayUsesTheSharedHitTestAndTheItemTooltipPath() {
		val src = RepoFile("src/main/kotlin/features/inventory/storageoverlay/StorageOverlayScreen.kt").readText()
		assertTrue("StorageClickLogic.pageToOpen(" in src)
		assertFalse(Regex("""button\(\)\s*==\s*0""").containsMatchIn(src))
		assertTrue("setTooltipForNextFrame(font, stack" in src)
		assertFalse("TODO: open this page" in src)
	}
}
