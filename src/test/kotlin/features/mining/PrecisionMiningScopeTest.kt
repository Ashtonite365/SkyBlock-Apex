// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.mining

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.features.mining.MiningLoot
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.ManagedConfig

class PrecisionMiningScopeTest {
	private val mining = listOf(SkyBlockIsland.DWARVEN_MINES, SkyBlockIsland.CRYSTAL_HOLLOWS, SkyBlockIsland.MINESHAFT)
	private val notMining = listOf(
		SkyBlockIsland.HUB, SkyBlockIsland.PRIVATE_ISLAND, SkyBlockIsland.DUNGEON, SkyBlockIsland.GARDEN,
		SkyBlockIsland.END, SkyBlockIsland.CRIMSON_ISLE, SkyBlockIsland.SPIDER,
		SkyBlockIsland.GOLD_MINE, SkyBlockIsland.DEEP_CAVERNS, SkyBlockIsland.NIL,
	)

	@Test
	fun islandGate() {
		for (i in mining) assertTrue(MiningLoot.isMiningIsland(i), i.serverMode)
		for (i in notMining) assertFalse(MiningLoot.isMiningIsland(i), i.serverMode)
		assertFalse(MiningLoot.isMiningIsland(null))
		for (i in mining) assertTrue(MiningLoot.acceptsPrecisionHit(i, HitResult.Type.BLOCK, false), i.serverMode)
		for (i in notMining) assertFalse(MiningLoot.acceptsPrecisionHit(i, HitResult.Type.BLOCK, false), i.serverMode)
		assertFalse(MiningLoot.acceptsPrecisionHit(null, HitResult.Type.BLOCK, false))
	}

	@Test
	fun missHitIgnored() {
		val miss = BlockHitResult.miss(Vec3(0.5, 64.5, 0.5), Direction.UP, BlockPos(0, 64, 0))
		assertEquals(HitResult.Type.MISS, miss.type)
		for (i in mining) {
			assertFalse(MiningLoot.acceptsPrecisionHit(i, miss.type, false), i.serverMode)
			assertFalse(MiningLoot.acceptsPrecisionHit(i, HitResult.Type.ENTITY, false), i.serverMode)
			assertFalse(MiningLoot.acceptsPrecisionHit(i, null, false), i.serverMode)
		}
	}

	@Test
	fun airBlockIgnored() {
		for (i in mining) assertFalse(MiningLoot.acceptsPrecisionHit(i, HitResult.Type.BLOCK, true), i.serverMode)
	}

	@Test
	fun settingKeyAndDefaultUnchanged() {
		val c: ManagedConfig = MiningLoot.TConfig
		c.explicitDefaultLoad()
		assertEquals(true, c.allOptions.getValue("precision-mining").default())
	}
}
