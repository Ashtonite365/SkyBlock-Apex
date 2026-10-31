// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.items.BlockZapperOverlay
import dev.ashtonite365.apex.features.items.BonemerangOverlay
import dev.ashtonite365.apex.features.items.EtherwarpOverlay

@ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class ItemOverlaysGoldenTest {

	private class Filled(val state: BlockState) : BlockGetter {
		override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
		override fun getBlockState(pos: BlockPos): BlockState = state
		override fun getFluidState(pos: BlockPos): FluidState = Fluids.EMPTY.defaultFluidState()
		override fun getHeight(): Int = 384
		override fun getMinY(): Int = -64
	}

	private val worlds = listOf(
		listOf("sss|sss|sss"),
		listOf("sssss|s...s|sssss", "....w|.....|....."),
		listOf("sdsds|dsdsd|sdsds"),
		listOf(List(10) { "s".repeat(20) }.joinToString("|")),
		listOf("sss|sss|sss", "sss|sws|sss", "sss|sss|sss"),
		listOf("s.s.s|sssss|s.s.s", "w....|.....|....c"),
		listOf("ffff|ffff", "...."),
		listOf("s|s|s|s", ".|w|.|."),
	)

	private val legend by lazy { mapOf('s' to Blocks.STONE, 'd' to Blocks.DIRT, 'w' to Blocks.WHEAT, 'f' to Blocks.FARMLAND, 'c' to Blocks.CACTUS, '.' to Blocks.AIR) }

	private fun blockIn(world: List<String>, p: BlockPos): Block {
		if (p.x < 0 || p.y < 0 || p.z < 0) return Blocks.AIR
		val ch = world.getOrNull(p.y)?.split("|")?.getOrNull(p.z)?.getOrNull(p.x) ?: '.'
		return legend.getValue(ch)
	}

	private fun pos(s: String) = s.split(",").map(String::toInt).let { BlockPos(it[0], it[1], it[2]) }

	@Test
	fun recordsMatch() {
		val n = Golden.check("item-overlays") { key ->
			val arg = key.substringAfter(' ')
			when (key.substringBefore(' ')) {
				"block" -> {
					val block = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(arg))
					val state = block.defaultBlockState()
					"ib=${block in EtherwarpOverlay.interactables.blocks} hall=${block in EtherwarpOverlay.passable.blocks} fat=${block in EtherwarpOverlay.solidAnyway.blocks}" +
						" empty=${state.getCollisionShape(Filled(state), BlockPos.ZERO).isEmpty} zapban=${block in BlockZapperOverlay.unzappable}"
				}
				"tags" -> when (arg) {
					"ib" -> EtherwarpOverlay.interactables
					"hall" -> EtherwarpOverlay.passable
					else -> EtherwarpOverlay.solidAnyway
				}.tags.map { it.location.toString() }.sorted().joinToString(",")
				"zapban" -> BlockZapperOverlay.unzappable.joinToString(",") { BuiltInRegistries.BLOCK.getKey(it).path }
				"zap" -> {
					val (world, start) = arg.split("|")
					BlockZapperOverlay.zapArea(pos(start)) { blockIn(worlds[world.toInt()], it) }
						.sortedWith(compareBy({ it.y }, { it.z }, { it.x }))
						.joinToString(" ") { "${it.x},${it.y},${it.z}" }
				}
				"bonemerang" -> if (arg == "weapons") BonemerangOverlay.throwableWeapons.joinToString(",") { it.skyblockId }
				else BonemerangOverlay.countLine(arg.substringAfter("hud ").toInt(), "Bonemerang Targets: %s")
				else -> error("unknown record $key")
			}
		}
		assertEquals(1350, n)
	}

	@Test
	fun passThroughRules() {

		val heads = EtherwarpOverlay.BlockGroup(setOf(Blocks.PLAYER_HEAD), emptySet())
		assertTrue(Blocks.PLAYER_HEAD.defaultBlockState() in heads)
		assertFalse(Blocks.STONE.defaultBlockState() in heads)
		assertTrue(BlockZapperOverlay.zapArea(BlockPos.ZERO) { Blocks.WHEAT }.isEmpty())
	}
}
