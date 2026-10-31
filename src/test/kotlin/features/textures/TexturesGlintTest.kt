package dev.ashtonite365.apex.test.features.textures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemCache
import dev.ashtonite365.apex.util.mc.ItemModelFallback
import dev.ashtonite365.apex.util.mc.loadItemFromNbt

@ExtendWith(AutoBootstrapExtension::class)
class TexturesGlintTest {
	@Test fun legacyEnchFlagMeansGlint() {
		assertTrue(ItemCache.legacyWantsGlint(CompoundTag().apply { put("ench", ListTag()) }))
		assertFalse(ItemCache.legacyWantsGlint(CompoundTag().apply { putInt("HideFlags", 254) }))
	}

	@OptIn(ExpensiveItemCacheApi::class, dev.ashtonite365.apex.util.mc.NeedsComponents::class)
	@Test fun glintFromRepoData() {
		val legacy = CompoundTag().apply {
			put("tag", CompoundTag().apply { put("ench", ListTag()) })
			putString("id", "minecraft:paper"); putByte("Count", 1); putShort("Damage", 0)
		}
		val modern = ItemCache.convert189ToModern(legacy)!!
		val stack = loadItemFromNbt(modern)!!.rebuildLive()
		assertEquals(true, stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE), "ench:[] from repo data must glint")
	}

	@Test fun hypixelModelFallsBackWhenPackAbsent() {
		val has = setOf("minecraft:paper", "hypixel_skyblock:item/island_relevant/garden/box_of_seeds")
		assertEquals("hypixel_skyblock:item/island_relevant/garden/box_of_seeds",
			ItemModelFallback.choose("hypixel_skyblock:item/island_relevant/garden/box_of_seeds", "minecraft:paper") { it in has })
		assertEquals("minecraft:paper", ItemModelFallback.choose("hypixel_skyblock:item/x", "minecraft:paper") { it in has })
		assertEquals("minecraft:paper", ItemModelFallback.choose("minecraft:paper", "minecraft:paper") { false })
		assertNull(ItemModelFallback.choose<String>(null, "minecraft:paper") { true })
	}
}
