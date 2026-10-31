// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.ConnectFamily
import dev.ashtonite365.apex.features.render.connected.ConnectRules
import dev.ashtonite365.apex.features.render.connected.ConnectTiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class ConnectRulesTest {
	private fun rule(s: String) = ConnectRules.of(st(s).block) ?: error("no rule for $s")
	private fun joins(a: String, b: String) = ConnectRules.matches(rule(a), st(a), st(b))

	@Test fun `every glass block and pane is covered, in the glass family`() {
		val glass = listOf("minecraft:glass", "minecraft:tinted_glass", "minecraft:glass_pane") +
			ConnectRules.COLOURS.flatMap { listOf("minecraft:${it}_stained_glass", "minecraft:${it}_stained_glass_pane") }
		assertEquals(35, glass.size)
		for (g in glass) assertEquals(ConnectFamily.GLASS, rule(g).family, g)
		assertEquals(16, ConnectRules.COLOURS.size)
	}

	@Test fun `each colour joins only itself`() {
		for (a in ConnectRules.COLOURS) for (b in ConnectRules.COLOURS) {
			assertEquals(a == b, joins("minecraft:${a}_stained_glass", "minecraft:${b}_stained_glass"), "$a/$b")
			assertEquals(a == b, joins("minecraft:${a}_stained_glass_pane", "minecraft:${b}_stained_glass_pane"), "$a/$b pane")
		}
		assertFalse(joins("minecraft:glass", "minecraft:white_stained_glass"))
		assertFalse(joins("minecraft:glass", "minecraft:tinted_glass"))
		assertFalse(joins("minecraft:glass", "minecraft:glass_pane"))
		assertFalse(joins("minecraft:red_stained_glass", "minecraft:red_stained_glass_pane"))
		assertTrue(joins("minecraft:tinted_glass", "minecraft:tinted_glass"))
		assertFalse(joins("minecraft:glass", "minecraft:air"))
		assertFalse(joins("minecraft:glass", "minecraft:stone"))
	}

	@Test fun `pane states join whatever their arms`() {
		assertTrue(joins("minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]", "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]"))
		assertTrue(rule("minecraft:glass_pane").pane)
		assertFalse(rule("minecraft:glass").pane)
	}

	@Test fun `connected blocks family is separate and groups waxed copper with its stage`() {
		assertEquals(ConnectFamily.BLOCKS, rule("minecraft:iron_block").family)
		assertTrue(joins("minecraft:copper_block", "minecraft:waxed_copper_block"))
		assertTrue(joins("minecraft:waxed_oxidized_copper", "minecraft:oxidized_copper"))
		assertFalse(joins("minecraft:copper_block", "minecraft:exposed_copper"))
		assertFalse(joins("minecraft:iron_block", "minecraft:gold_block"))
		assertFalse(joins("minecraft:polished_andesite", "minecraft:polished_diorite"))
	}

	@Test fun `pillars join only along the same axis`() {
		assertTrue(joins("minecraft:purpur_pillar[axis=y]", "minecraft:purpur_pillar[axis=y]"))
		assertFalse(joins("minecraft:purpur_pillar[axis=y]", "minecraft:purpur_pillar[axis=x]"))
		val r = rule("minecraft:purpur_pillar")
		assertTrue(r.sameAxis)
		assertEquals(ConnectTiles.PLANE_ACROSS, r.textures["minecraft:block/purpur_pillar_side"])
		assertEquals(ConnectTiles.PLANE_ALL, r.textures["minecraft:block/purpur_pillar_top"])
	}

	@Test fun `textures with a one-sided band are row-only and unframed blocks are left out`() {
		assertEquals(ConnectTiles.PLANE_ACROSS, rule("minecraft:bookshelf").textures["minecraft:block/bookshelf"])
		assertEquals(ConnectTiles.PLANE_ACROSS, rule("minecraft:cut_sandstone").textures["minecraft:block/cut_sandstone"])
		for (s in listOf("minecraft:sandstone", "minecraft:red_sandstone", "minecraft:stone", "minecraft:oak_planks", "minecraft:oak_slab", "minecraft:quartz_pillar"))
			assertNull(ConnectRules.of(st(s).block), s)
	}

	@Test fun `every listed block exists in this game version`() {
		val ids = ConnectRules.blockIds.keys
		assertEquals(35 + 8 + 6 + 1 + 8 + 2 + 1 + 1, ids.size, ids.toString())
		assertTrue(ConnectRules.textures.keys.all { it.startsWith("minecraft:block/") })
		assertEquals(ConnectTiles.PLANE_ALL, ConnectRules.textures["minecraft:block/glass"])
	}
}
