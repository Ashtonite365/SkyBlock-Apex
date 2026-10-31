package dev.ashtonite365.apex.test.gui

import org.joml.Vector2i
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json
import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudPosition

class HudAnchorTest {

	private val big = 960 to 540
	private val small = 427 to 240

	@Test
	fun hotbarBarKeepsOffsetAtBothSizes() {
		val p = HudPosition(-51, -2, 1f, HudAnchor.HOTBAR)
		assertEquals(Vector2i(480 - 51 - 50, 540 - 22 - 2 - 16), p.resolve(100, 16, big.first, big.second))
		assertEquals(Vector2i(113, 240 - 22 - 2 - 16), p.resolve(100, 16, small.first, small.second))
	}

	@Test
	fun cornersKeepDistanceToTheirEdge() {
		val br = HudPosition(0, 0, 1f).apply { place(900, 500, 50, 20, big.first, big.second) }
		assertEquals(HudAnchor.BOTTOM_RIGHT, br.anchor)
		assertEquals(Vector2i(900, 500), br.resolve(50, 20, big.first, big.second))
		assertEquals(Vector2i(427 - 60, 240 - 40), br.resolve(50, 20, small.first, small.second))
		val tl = HudPosition(0, 0, 1f).apply { place(5, 5, 50, 20, big.first, big.second) }
		assertEquals(HudAnchor.TOP_LEFT, tl.anchor)
		assertEquals(Vector2i(5, 5), tl.resolve(50, 20, small.first, small.second))
		val c = HudPosition(0, 0, 1f).apply { place(455, 260, 50, 20, big.first, big.second) }
		assertEquals(HudAnchor.CENTRE, c.anchor)
		assertEquals(Vector2i(189, 110), c.resolve(50, 20, small.first, small.second))
	}

	@Test
	fun bottomCentreSnapsToHotbar() {
		val p = HudPosition(0, 0, 1f).apply { place(430, 500, 100, 16, big.first, big.second) }
		assertEquals(HudAnchor.HOTBAR, p.anchor)
		assertEquals(Vector2i(430, 500), p.resolve(100, 16, big.first, big.second))
	}

	@Test
	fun legacyMigratesOnceKeepingPlacement() {
		val p = Json.decodeFromString(HudPosition.serializer(), """{"x":260,"y":327,"scale":1.0}""")
		assertEquals(null, p.anchor)
		p.migrate(100, 16, big.first, big.second)
		assertEquals(Vector2i(260, 327), p.resolve(100, 16, big.first, big.second))
		val after = p.copy()
		p.migrate(100, 16, small.first, small.second)
		assertEquals(after, p)
		val back = Json.decodeFromString(HudPosition.serializer(), Json.encodeToString(HudPosition.serializer(), p))
		assertEquals(p, back)
	}
}
