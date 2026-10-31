// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.ConnectCore
import dev.ashtonite365.apex.features.render.connected.ConnectRules
import dev.ashtonite365.apex.features.render.connected.ConnectTiles
import dev.ashtonite365.apex.features.render.connected.PaneParts
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class PaneConnectTest {
	private fun pane(e: Boolean, w: Boolean, n: Boolean, s: Boolean, colour: String = "") =
		"minecraft:${colour}glass_pane[east=$e,north=$n,south=$s,waterlogged=false,west=$w]"
	private val row = pane(e = true, w = true, n = false, s = false)
	private val cross = pane(e = false, w = false, n = true, s = true)

	private fun decide(w: MapLookup, x: Int, y: Int, z: Int, face: Direction, q: FloatArray): Int {
		val self = w.state(x, y, z)
		val rule = ConnectRules.of(self.block)!!
		return ConnectCore.decide(cacheAt(w, x, y, z), rule, self, face, rule.textures.values.first(), q)
	}

	private fun cap(face: Direction, cx: Float, cz: Float) =
		faceQuad(face, Direction.NORTH, Direction.EAST, centre = floatArrayOf(cx, if (face == Direction.UP) 1f else 0f, cz), half = 0.0625f)

	@Test fun `arm detection from the quad centre`() {
		assertNull(PaneParts.armOf(0.5f, 0.5f))
		assertEquals(Direction.EAST, PaneParts.armOf(0.78f, 0.5f))
		assertEquals(Direction.NORTH, PaneParts.armOf(0.5f, 0.2f))
	}

	@Test fun `stacked panes lose the caps between them`() {
		val w = MapLookup()
		w.set(0, 0, 0, row); w.set(0, 1, 0, row)
		assertEquals(ConnectCore.DROP, decide(w, 0, 0, 0, Direction.UP, cap(Direction.UP, 0.78f, 0.5f)))
		assertEquals(ConnectCore.DROP, decide(w, 0, 0, 0, Direction.UP, cap(Direction.UP, 0.5f, 0.5f)))
		assertEquals(ConnectCore.DROP, decide(w, 0, 1, 0, Direction.DOWN, cap(Direction.DOWN, 0.22f, 0.5f)))

		assertEquals(ConnectCore.KEEP, decide(w, 0, 1, 0, Direction.UP, cap(Direction.UP, 0.78f, 0.5f)))
		assertEquals(ConnectCore.KEEP, decide(w, 0, 0, 0, Direction.DOWN, cap(Direction.DOWN, 0.78f, 0.5f)))
	}

	@Test fun `a cap stays where the pane above has no matching arm or another colour`() {
		val w = MapLookup()
		w.set(0, 0, 0, row); w.set(0, 1, 0, cross)
		assertEquals(ConnectCore.KEEP, decide(w, 0, 0, 0, Direction.UP, cap(Direction.UP, 0.78f, 0.5f)))
		assertEquals(ConnectCore.DROP, decide(w, 0, 0, 0, Direction.UP, cap(Direction.UP, 0.5f, 0.5f)))
		w.set(0, 1, 0, pane(e = true, w = true, n = false, s = false, colour = "red_stained_"))
		assertEquals(ConnectCore.KEEP, decide(w, 0, 0, 0, Direction.UP, cap(Direction.UP, 0.5f, 0.5f)))
		w.set(0, 1, 0, "minecraft:glass")
		assertEquals(ConnectCore.KEEP, decide(w, 0, 0, 0, Direction.UP, cap(Direction.UP, 0.5f, 0.5f)))
	}

	@Test fun `pane sides join along the row and upward only where the arm continues`() {
		val w = MapLookup()
		for (x in -1..1) w.set(x, 0, 0, row)
		w.set(0, 1, 0, cross)
		w.set(1, 0, -1, "minecraft:stone")

		val q = faceQuad(Direction.NORTH, Direction.UP, Direction.WEST, centre = floatArrayOf(0.78f, 0.5f, 7f / 16f), half = 0.2f)
		val tile = decide(w, 0, 0, 0, Direction.NORTH, q)
		assertEquals(ConnectTiles.tileOf(ConnectTiles.LEFT or ConnectTiles.RIGHT), tile)
		w.set(0, 1, 0, row)
		w.set(-1, 1, 0, row); w.set(1, 1, 0, row)
		val tile2 = decide(w, 0, 0, 0, Direction.NORTH, q)
		assertEquals(ConnectTiles.tileOf(ConnectTiles.LEFT or ConnectTiles.RIGHT or ConnectTiles.UP or ConnectTiles.UP_LEFT or ConnectTiles.UP_RIGHT), tile2)
	}
}
