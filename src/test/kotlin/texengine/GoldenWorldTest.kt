// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class GoldenWorldTest {
	private val engine: GoldenEngine = ApexGolden

	@BeforeEach
	fun load() {
		engine.loadBlocks(FixturePacks.manager("blocks"))
	}

	@BeforeEach
	fun loadConfigDefaults() = TexConfig.reset()

	@AfterEach
	fun resetConfig() = TexConfig.reset()

	private fun id(s: String) = Identifier.parse(s)

	private val stone = Blocks.STONE.defaultBlockState()
	private val dirt = Blocks.DIRT.defaultBlockState()

	@Test
	fun `G-BLK-1 unconditional replacement`() {
		assertEquals(id("firmskyblock:fancy_stone"), engine.blockReplacement("hub", stone, BlockPos(100, 64, -30)))
		assertEquals(id("firmskyblock:fancy_stone"), engine.blockReplacement("hub", stone, null))
		assertEquals(id("firmskyblock:fancy_stone"), engine.blockReplacement("dynamic", stone, BlockPos.ZERO))
	}

	@Test
	fun `G-BLK-2 area check, inclusive, corners swapped`() {
		assertEquals(id("firmskyblock:fancy_dirt"), engine.blockReplacement("hub", dirt, BlockPos(5, 5, 5)))
		assertEquals(id("firmskyblock:fancy_dirt"), engine.blockReplacement("hub", dirt, BlockPos(10, 0, 10)))
		assertNull(engine.blockReplacement("hub", dirt, BlockPos(11, 0, 0)))
		assertEquals(id("firmskyblock:fancy_dirt"), engine.blockReplacement("hub", dirt, null))
	}

	@Test
	fun `G-BLK-3 fallback scope`() {
		assertNull(engine.blockReplacement("hub", stone, null, inFallback = true))
		assertEquals(id("firmskyblock:fancy_stone"), engine.blockReplacement("hub", stone, BlockPos.ZERO, inFallback = true))
	}

	@Test
	fun `G-BLK-4 other island`() {
		assertNull(engine.blockReplacement("mining_3", stone, BlockPos.ZERO))
		assertNull(engine.blockReplacement("dynamic", dirt, BlockPos(5, 5, 5)))
	}

	@Test
	fun `G-BLK-5 first file wins`() {
		assertEquals(id("firmskyblock:fancy_stone"), engine.blockReplacement("hub", stone, BlockPos.ZERO))
	}

	@Test
	fun `G-BLK-6 three or more areas`() {
		val gravel = Blocks.GRAVEL.defaultBlockState()
		val inside = listOf(BlockPos(0, 0, 0), BlockPos(1, 1, 1), BlockPos(10, 0, 0), BlockPos(11, 1, 1), BlockPos(0, 0, 10),
			BlockPos(1, 1, 11), BlockPos(0, 1, 0), BlockPos(11, 0, 1), BlockPos(1, 0, 11), BlockPos(10, 1, 0))
		val outside = listOf(BlockPos(5, 0, 5), BlockPos(5, 1, 0), BlockPos(0, 0, 5), BlockPos(11, 1, 11), BlockPos(2, 0, 0),
			BlockPos(12, 0, 0), BlockPos(0, 2, 0), BlockPos(-1, 0, 0), BlockPos(9, 0, 0), BlockPos(0, 0, 12))
		for (p in inside) assertEquals(id("firmskyblock:fancy_gravel"), engine.blockReplacement("hub", gravel, p), p.toString())
		for (p in outside) assertNull(engine.blockReplacement("hub", gravel, p), p.toString())
	}

	@Test
	fun `G-BLK-7 unknown block skipped, rest of file loads`() {
		assertEquals(id("firmskyblock:fancy_sand"), engine.blockReplacement("hub", Blocks.SAND.defaultBlockState(), BlockPos.ZERO))
	}
}
