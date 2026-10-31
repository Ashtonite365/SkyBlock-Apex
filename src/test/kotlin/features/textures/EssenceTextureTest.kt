// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.textures

import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.repo.ItemCache
import dev.ashtonite365.apex.repo.LearnedSkullTextures
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class EssenceTextureTest {
	private val essenceNbt = "{display:{Lore:[0:\"§7Essence (currency)\"],Name:\"§6Gold Essence\"},ExtraAttributes:{id:\"ESSENCE_GOLD\"},HideFlags:254}"
	private val texturedNbt = "{ExtraAttributes:{id:\"X\"},SkullOwner:{Id:\"5144d635-41a3-5652-893a-948f9a31aa59\",Properties:{textures:[{Value:\"abc\"}]}}}"

	@Test fun repoEssenceHasNoTexture() {
		assertTrue(ItemCache.legacyHeadWithoutTexture("minecraft:skull", essenceNbt))
		assertFalse(ItemCache.legacyHeadWithoutTexture("minecraft:skull", texturedNbt))
		assertFalse(ItemCache.legacyHeadWithoutTexture("minecraft:paper", essenceNbt))
	}

	@Test fun learnedTextureIsAppliedToTheStackComponents() {
		val live = ItemStack(Items.PLAYER_HEAD)
		val learned = LearnedSkullTextures.Learned("5144d635-41a3-5652-893a-948f9a31aa59", "dGV4dHVyZQ==")
		assertTrue(LearnedSkullTextures.applyTo(live, learned))
		assertEquals(learned, LearnedSkullTextures.liveTexture(live), "round trip through DataComponents.PROFILE")

		val repo = ItemStack(Items.PLAYER_HEAD).apply { set(DataComponents.ITEM_MODEL, Identifier.parse("minecraft:player_head")) }
		assertTrue(LearnedSkullTextures.needsTexture(repo))
		assertTrue(LearnedSkullTextures.applyTo(repo, learned))
		assertFalse(LearnedSkullTextures.needsTexture(repo))
		assertEquals("dGV4dHVyZQ==", repo.get(DataComponents.PROFILE)!!.partialProfile().properties()["textures"].first().value())
		assertEquals(Identifier.parse("minecraft:player_head"), repo.get(DataComponents.ITEM_MODEL), "other components kept")
		assertFalse(LearnedSkullTextures.applyTo(repo, learned), "never overwrites a texture")
		assertFalse(LearnedSkullTextures.applyTo(ItemStack(Items.PAPER), learned))
		assertNull(LearnedSkullTextures.liveTexture(ItemStack(Items.PLAYER_HEAD)))
	}
}
