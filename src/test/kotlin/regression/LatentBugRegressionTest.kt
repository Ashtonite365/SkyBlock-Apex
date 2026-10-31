package dev.ashtonite365.apex.test.regression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.mining.PristineProfitTracker
import dev.ashtonite365.apex.features.mining.PristineProfitTracker.GemstoneKind
import dev.ashtonite365.apex.repo.MiningRepoData
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.isEnchantedBook
import dev.ashtonite365.apex.util.mc.view

@ExtendWith(AutoBootstrapExtension::class)
class LatentBugRegressionTest {
	@Test fun enchantedBookIsRecognisedThroughItsHolder() {

		assertTrue(ItemStack(Items.ENCHANTED_BOOK).view().isEnchantedBook)
		assertFalse(ItemStack(Items.BOOK).view().isEnchantedBook)
		assertFalse(ItemStack(Items.STONE).view().isEnchantedBook)
	}

	@Test fun blockItemsResolveToTheirBlock() {

		assertEquals(Blocks.STONE, MiningRepoData.Block189.blockOf(ItemStack(Items.STONE).view()))
		assertEquals(Blocks.COBBLESTONE, MiningRepoData.Block189.blockOf(ItemStack(Items.COBBLESTONE).view()))
		assertNull(MiningRepoData.Block189.blockOf(ItemStack(Items.DIAMOND).view()))
		assertNull(MiningRepoData.Block189.blockOf(null))
	}

	@Test fun onyxIsSpelledCorrectlyAndTheOldSpellingStillLoads() {
		assertEquals("Onyx", GemstoneKind.ONYX.label)
		assertEquals("FLAWED_ONYX_GEM", GemstoneKind.ONYX.flawedId.toString())
		assertEquals("FINE_ONYX_GEM", GemstoneKind.ONYX.fineId.toString())
		assertEquals(GemstoneKind.ONYX, GemstoneKind.byName("Onyx"))
		assertEquals(GemstoneKind.ONYX, GemstoneKind.byName("ONXY"))
		assertEquals(GemstoneKind.ONYX, GemstoneKind.byName("onxy"))
		assertEquals(GemstoneKind.RUBY, GemstoneKind.byName("Ruby"))
		assertNull(GemstoneKind.byName("Diamond"))
		for (k in GemstoneKind.entries) assertEquals(k, GemstoneKind.byName(k.label), k.name)
	}

	@Test fun onyxPristineLineMatchesAndResolves() {

		val m = PristineProfitTracker.pristineRegex.matcher("PRISTINE! You found \u2748 Flawed Onyx Gemstone x1,234!")
		assertTrue(m.matches())
		assertEquals(GemstoneKind.ONYX, GemstoneKind.byName(m.group("kind")))
	}
}
