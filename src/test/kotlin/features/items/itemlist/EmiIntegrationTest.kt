// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.itemlist

import java.util.function.Predicate
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.compat.emi.ApexEmiPlugin
import dev.ashtonite365.apex.compat.emi.EmiEntries
import dev.ashtonite365.apex.compat.emi.EmiPageCategory
import dev.ashtonite365.apex.compat.emi.EmiPageRecipe
import dev.ashtonite365.apex.compat.emi.SkyblockEmiStack
import dev.ashtonite365.apex.util.compatloader.CompatSwitch
import dev.ashtonite365.apex.compat.emi.Compat as EmiCompat

class EmiIntegrationTest {
	init { dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft() }

	private val oldFactory = EmiEntries.ingredientFactory

	private fun listIngredient(stacks: List<EmiStack>): EmiIngredient =
		Recorder().fake(EmiIngredient::class.java) { name, _ -> if (name == "getEmiStacks") stacks else null }

	@BeforeEach fun fakes() { EmiEntries.ingredientFactory = ::listIngredient }
	@AfterEach fun restore() { EmiEntries.ingredientFactory = oldFactory }

	private val crafting = FakePage(apexId("fake_crafting"), listOf("a", "b"), craft = {})
	private val mobs = FakePage(apexId("fake_mobs"), listOf("zombie"))
	private fun plugin(ready: Boolean = true, usable: Boolean = true) = ApexEmiPlugin(
		pages = { listOf(crafting, mobs) }, entries = { listOf(sb("A"), sb("B"), sb("C")) },
		repoReady = { ready }, usable = { usable }, viewerTabs = false)

	@Test fun registersCategoriesRecipesAndStacks() {
		val rec = Recorder()
		val plugin = plugin()
		plugin.register(rec.fake(EmiRegistry::class.java))
		assertEquals(listOf(apexId("fake_crafting"), apexId("fake_mobs")), rec.named("addCategory").map { (it[0] as EmiPageCategory).id })
		val recipes = rec.named("addRecipe").map { it[0] as EmiPageRecipe<*> }
		assertEquals(listOf("a", "b", "zombie"), recipes.map { it.recipe })
		assertEquals(listOf("/fake_crafting/0", "/fake_crafting/1", "/fake_mobs/0"), recipes.map { it.id.path })
		assertTrue(recipes.all { it.id.namespace == "apex" })
		assertEquals(listOf("A", "B", "C"), rec.named("addEmiStack").map { (it[0] as SkyblockEmiStack).stack.catalogItemId.skyblockId })
		assertEquals(1, rec.named("addGenericExclusionArea").size)
		assertEquals(1, rec.named("addGenericStackProvider").size)

		@Suppress("UNCHECKED_CAST") val dropStack = rec.named("removeEmiStacks").single()[0] as Predicate<EmiStack>
		assertFalse(dropStack.test(SkyblockEmiStack(sb("A"))))
		assertTrue(dropStack.test(EmiStack.EMPTY))

		@Suppress("UNCHECKED_CAST") val dropRecipe = rec.named("removeRecipes").single()[0] as Predicate<EmiRecipe>
		assertFalse(dropRecipe.test(recipes[0]), "Apex recipes stay")
		val vanilla = EmiPageRecipe(crafting, "a", EmiPageCategory(FakePage(Identifier.withDefaultNamespace("crafting"), listOf())), 0)
		assertTrue(dropRecipe.test(vanilla), "other recipes go")
	}

	@Test fun noRepoMeansCategoriesOnly() {
		val rec = Recorder()
		plugin(ready = false).register(rec.fake(EmiRegistry::class.java))
		assertEquals(2, rec.named("addCategory").size)
		assertTrue(rec.named("addRecipe").isEmpty())
		assertTrue(rec.named("addEmiStack").isEmpty())
		assertTrue(rec.named("removeEmiStacks").isEmpty(), "keeps the normal list until the repo is in")
	}

	@Test fun recipesCarryInputsAndOutputs() {
		val recipe = EmiPageRecipe(crafting, "a", EmiPageCategory(crafting), 0)
		assertEquals(1, recipe.inputs.size)
		assertEquals(listOf("IN_a", "IN2_a"), recipe.inputs[0].emiStacks.map { (it as SkyblockEmiStack).stack.catalogItemId.skyblockId })
		assertEquals(listOf("OUT_a"), recipe.outputs.map { (it as SkyblockEmiStack).stack.catalogItemId.skyblockId })
		assertEquals(100, recipe.displayWidth)
		assertEquals(50, recipe.displayHeight)
		assertFalse(recipe.supportsRecipeTree())
	}

	@Test fun focusedPagesAreBuiltOnDemand() {
		val plugin = plugin()
		plugin.register(Recorder().fake(EmiRegistry::class.java))
		val focused = plugin.focusedRecipe(mobs, "zombie")
		assertNotNull(focused)
		assertEquals("/fake_mobs/${ApexEmiPlugin.FOCUS_INDEX}", focused!!.id.path)
		assertSame(plugin.categories[1], focused.category)
		assertNull(plugin.focusedRecipe(FakePage(apexId("unregistered"), listOf("x")), "x"))
	}

	@Test fun stackConversion() {
		val s = SkyblockEmiStack(sb("ASPECT_OF_THE_END", 3))
		assertEquals(sb("ASPECT_OF_THE_END").catalogItemId.identifier, s.id)
		assertEquals(SkyblockEmiStack(sb("ASPECT_OF_THE_END")).key, s.key)
		assertTrue(s.key != SkyblockEmiStack(sb("HYPERION")).key)
		assertFalse(s.isEmpty)
		assertTrue(SkyblockEmiStack(sb("X", 0)).isEmpty)
		s.setAmount(7)
		val copy = s.copy() as SkyblockEmiStack
		assertEquals(7L, copy.amount)
		assertSame(s.stack, copy.stack)

		assertSame(EmiStack.EMPTY, EmiEntries.ingredient(listOf()))
		val one = EmiEntries.ingredient(listOf(sb("A")))
		assertEquals("A", EmiEntries.skyblockOf(one)!!.catalogItemId.skyblockId)
		val two = EmiEntries.ingredient(listOf(sb("A"), sb("B")))
		assertEquals(2, two.emiStacks.size)
		assertEquals("A", EmiEntries.skyblockOf(two)!!.catalogItemId.skyblockId)
		assertNull(EmiEntries.skyblockOf(EmiStack.EMPTY))
		assertNull(EmiEntries.skyblockOf(null))
	}

	@Test fun onlyApexCategoriesShow() {
		assertFalse(ApexEmiPlugin.hides(apexId("mobs")))
		assertTrue(ApexEmiPlugin.hides(Identifier.withDefaultNamespace("crafting")))
		assertTrue(ApexEmiPlugin.hides(Identifier.fromNamespaceAndPath("emi", "info")))
	}

	@Test fun absentModGuard() {
		assertFalse(EmiCompat.enabledHere(), "the EMI set must stay off without EMI")
		assertFalse(CompatSwitch.mayLoad("dev.ashtonite365.apex.compat.emi.ApexEmiPlugin"))
		val rec = Recorder()
		val plugin = ApexEmiPlugin(pages = { listOf(crafting) }, entries = { listOf(sb("A")) }, repoReady = { true }, viewerTabs = false)
		plugin.register(rec.fake(EmiRegistry::class.java))
		assertTrue(rec.calls.isEmpty(), "${rec.calls}")
		assertTrue(plugin.categories.isEmpty())
	}
}
