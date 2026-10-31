package dev.ashtonite365.apex.test.features.dungeon

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.Boulder
import dev.ashtonite365.apex.features.dungeon.PuzzleRoom

class BoulderTest {
	private val corner = BlockPos(-136, 0, -200)

	@Test
	fun tableLoadsAndKeysAre42() {
		assertEquals(8, Boulder.table.size)
		assertTrue(Boulder.table.keys.all { it.length == 42 })
	}

	@Test
	fun solvesEveryLayoutInEveryRotation() {
		for ((key, steps) in Boulder.table) for (rot in 0..3) {

			val boulders = mutableSetOf<BlockPos>()
			var i = 0
			for (z in 24 downTo 9 step 3) for (x in 24 downTo 6 step 3) { if (key[i++] == '1') boulders += PuzzleRoom.toReal(corner, BlockPos(x, 66, z), rot) }
			val got = Boulder.solve(corner, Boulder.table) { it !in boulders }
			assertEquals(steps.size, got.size)
			assertEquals(PuzzleRoom.toReal(corner, steps[0].render, rot), got[0].render, "$key rot $rot")
		}
	}

	@Test
	fun nothingOutsideTheRoom() {
		assertTrue(Boulder.solve(corner, Boulder.table) { true }.isEmpty())
	}

	private fun board(key: String, rot: Int): Set<BlockPos> {
		val boulders = mutableSetOf<BlockPos>()
		var i = 0
		for (z in 24 downTo 9 step 3) for (x in 24 downTo 6 step 3) { if (key[i++] == '1') boulders += PuzzleRoom.toReal(corner, BlockPos(x, 66, z), rot) }
		return boulders
	}

	private fun scanWith(blocks: Map<BlockPos, String>) = Boulder.scan(corner, Boulder.table,
		{ it !in blocks }, { blocks[it]?.endsWith("_planks") == true })

	@Test
	fun solvableGridGivesOrderedButtons() {
		val (key, steps) = Boulder.table.entries.first { it.value.size >= 4 }
		val woods = listOf("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:acacia_planks")
		val blocks = board(key, 1).withIndex().associate { (i, p) -> p to woods[i % 3] }
		val got = scanWith(blocks)
		assertTrue(got is Boulder.Scan.Solved, "$got")
		got as Boulder.Scan.Solved
		assertEquals(steps.map { PuzzleRoom.toReal(corner, it.click, 1) }, got.steps.map { it.click })
	}

	@Test
	fun deadEndGridWarnsInsteadOfNothing() {

		val key = Boulder.table.keys.first()
		val i = key.indexOf('0')
		val stuck = key.substring(0, i) + '1' + key.substring(i + 1)
		assertTrue(stuck !in Boulder.table)
		val got = scanWith(board(stuck, 2).associateWith { "minecraft:spruce_planks" })
		assertEquals(Boulder.Scan.Blocked::class, got::class)
	}

	@Test
	fun otherRoomsAreNotBoulder() {
		assertEquals(Boulder.Scan.NotBoulder, scanWith(emptyMap()))
		val stone = board(Boulder.table.keys.first(), 0).associateWith { "minecraft:stone_bricks" }
		assertEquals(Boulder.Scan.NotBoulder, scanWith(stone))
	}
}
