// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.ConnectCore
import dev.ashtonite365.apex.features.render.connected.ConnectRules
import dev.ashtonite365.apex.features.render.connected.NeighbourCache
import dev.ashtonite365.apex.features.render.connected.QuadGeom
import dev.ashtonite365.apex.test.features.render.farterrain.surface.GLASS
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class ConnectBenchmarkTest {
	private val faces = Direction.entries.map { f ->
		val up = if (f.axis == Direction.Axis.Y) Direction.NORTH else Direction.UP
		val right = if (f.axis == Direction.Axis.X) (if (f == Direction.EAST) Direction.NORTH else Direction.SOUTH)
		else if (f == Direction.NORTH) Direction.WEST else Direction.EAST
		f to faceQuad(f, up, right)
	}
	private val scratch = QuadGeom()

	private inline fun best(runs: Int, body: () -> Unit): Long {
		var b = Long.MAX_VALUE
		repeat(runs) { val t = System.nanoTime(); body(); b = minOf(b, System.nanoTime() - t) }
		return b
	}

	@Test fun `per block cost`() {
		val w = MapLookup()
		for (x in -1..1) for (y in -1..1) for (z in -1..1) w.set(x, y, z, if (z == 0) GLASS else "minecraft:air")
		val self = st(GLASS)
		val rule = ConnectRules.of(self.block)!!
		val n = NeighbourCache()
		val iters = 200_000
		var sink = 0

		val nearNs = best(7) {
			for (i in 0 until iters) {
				n.begin(w, 0, 0, 0)
				for ((f, q) in faces) sink += ConnectCore.decide(n, rule, self, 0xFF, scratch.set(f, q))
			}
		}
		val near = nearNs.toDouble() / iters
		println("connected textures: per block (6 faces) near ${"%.0f".format(near)} ns, sink $sink")
		assertTrue(near < 20_000, "per block near $near ns")
	}
}
