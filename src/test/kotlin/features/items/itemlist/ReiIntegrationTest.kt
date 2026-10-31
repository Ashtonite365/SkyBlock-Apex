// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.itemlist

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.Optional
import java.util.function.Predicate
import com.mojang.serialization.JsonOps
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.category.visibility.CategoryVisibilityPredicate
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.client.view.ViewSearchBuilder
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext
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
import dev.ashtonite365.apex.compat.rei.ApexReiPlugin
import dev.ashtonite365.apex.compat.rei.ReiEntries
import dev.ashtonite365.apex.compat.rei.ReiIds
import dev.ashtonite365.apex.compat.rei.ReiPageCategory
import dev.ashtonite365.apex.compat.rei.ReiPageDisplay
import dev.ashtonite365.apex.compat.rei.SkyblockEntryDefinition
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPages
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.compatloader.CompatSwitch
import dev.ashtonite365.apex.compat.rei.Compat as ReiCompat

class ReiIntegrationTest {
	init { dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft() }

	private val oldStack = ReiEntries.stackFactory
	private val oldIds = ReiIds.factory

	private fun entry(value: Any?): EntryStack<*> =
		Proxy.newProxyInstance(EntryStack::class.java.classLoader, arrayOf(EntryStack::class.java), InvocationHandler { p, m, a ->
			when (m.name) {
				"getValue" -> value
				"isEmpty" -> value == null
				"equals" -> p === a!![0]
				"hashCode" -> System.identityHashCode(p)
				"toString" -> "entry($value)"
				else -> Recorder.zero(m.returnType)
			}
		}) as EntryStack<*>

	private fun categoryId(id: Identifier): CategoryIdentifier<*> =
		Proxy.newProxyInstance(CategoryIdentifier::class.java.classLoader, arrayOf(CategoryIdentifier::class.java), InvocationHandler { p, m, a ->
			when (m.name) {
				"getIdentifier" -> id
				"getNamespace" -> id.namespace
				"getPath" -> id.path
				"equals" -> (a!![0] as? CategoryIdentifier<*>)?.identifier == id
				"hashCode" -> id.hashCode()
				"toString" -> id.toString()
				"cast" -> p
				else -> Recorder.zero(m.returnType)
			}
		}) as CategoryIdentifier<*>

	@BeforeEach fun fakes() {
		ReiEntries.stackFactory = { entry(it) }
		ReiIds.factory = { categoryId(it) }
	}

	@AfterEach fun restore() {
		ReiEntries.stackFactory = oldStack
		ReiIds.factory = oldIds
	}

	private val crafting = FakePage(apexId("fake_crafting"), listOf("a", "b", "c"), craft = {})
	private val mobs = FakePage(ItemListPages.MOBS, listOf("zombie"))
	private fun plugin(ready: Boolean = true, usable: Boolean = true) = ApexReiPlugin(
		pages = { listOf(crafting, mobs) }, entries = { listOf(sb("A"), sb("B")) },
		repoReady = { ready }, usable = { usable })

	@Test fun registersOneCategoryPerPagePlusFocusTwins() {
		val rec = Recorder()
		val plugin = plugin()
		plugin.registerCategories(rec.fake(CategoryRegistry::class.java))
		val added = rec.named("add").map { (it[0] as ReiPageCategory<*>).id }
		assertEquals(listOf(apexId("fake_crafting"), ItemListPages.MOBS, apexId(ItemListPages.MOBS.path + "_focus")), added)
		val predicate = rec.named("registerVisibilityPredicate").single()[0] as CategoryVisibilityPredicate
		assertFalse(predicate.handleCategory(plugin.categories[0]).interruptsFurtherEvaluation(), "Apex categories stay")
		val vanilla = ReiPageCategory(FakePage(Identifier.withDefaultNamespace("crafting"), listOf()))
		assertTrue(predicate.handleCategory(vanilla).interruptsFurtherEvaluation(), "other mods' categories hide")
		assertEquals(false, predicate.handleCategory(vanilla).value())
	}

	@Test fun registersDisplaysAndFocusGenerators() {
		val rec = Recorder()
		val plugin = plugin()
		plugin.registerCategories(Recorder().fake(CategoryRegistry::class.java))
		plugin.registerDisplays(rec.fake(DisplayRegistry::class.java))
		val displays = rec.named("add").map { it[0] as ReiPageDisplay<*> }
		assertEquals(listOf("a", "b", "c", "zombie"), displays.map { it.recipe })
		assertEquals(1, rec.named("registerGlobalDisplayGenerator").size)
		val generators = rec.named("registerDisplayGenerator")
		assertEquals(1, generators.size, "one twin, for the mobs page")

		@Suppress("UNCHECKED_CAST") val generator = generators.single()[1] as DynamicDisplayGenerator<Display>
		val twin = plugin.focus(mobs, "zombie")
		assertNotNull(twin)
		assertNull(plugin.focus(crafting, "a"), "crafting has no twin")
		val wanted = Recorder().fake(ViewSearchBuilder::class.java) { name, _ -> if (name == "getCategories") setOf(twin!!.categoryId) else null }
		val other = Recorder().fake(ViewSearchBuilder::class.java) { name, _ -> if (name == "getCategories") setOf<CategoryIdentifier<*>>() else null }
		assertEquals(listOf("zombie"), generator.generate(wanted).get().map { (it as ReiPageDisplay<*>).recipe })
		assertEquals(Optional.empty<List<Display>>(), generator.generate(other))
		assertSame(plugin.focusedDisplay(twin!!.id), generator.generate(wanted).get().single())
	}

	@Test fun displaysCarryInputsOutputsAndCraftAction() {
		val display = ReiPageCategory(crafting).display("a")
		assertEquals(setOf("IN_a", "IN2_a"), display.inputIds)
		assertEquals(setOf("OUT_a"), display.outputIds)
		assertNotNull(ApexReiPlugin.CraftTransfer.craftActionOf(display))
		assertNull(ApexReiPlugin.CraftTransfer.craftActionOf(ReiPageCategory(mobs).display("zombie")))
		assertNull(ApexReiPlugin.CraftTransfer.craftActionOf(null))
	}

	@Test fun noRepoMeansNoDisplaysButTwinsStillWork() {
		val rec = Recorder()
		val plugin = plugin(ready = false)
		plugin.registerCategories(Recorder().fake(CategoryRegistry::class.java))
		plugin.registerDisplays(rec.fake(DisplayRegistry::class.java))
		assertTrue(rec.named("add").isEmpty())
		assertEquals(1, rec.named("registerDisplayGenerator").size)
	}

	@Test fun entryListKeepsOnlySkyblockItems() {
		val rec = Recorder()
		plugin().registerEntries(rec.fake(EntryRegistry::class.java))
		@Suppress("UNCHECKED_CAST") val remove = rec.named("removeEntryIf").single()[0] as Predicate<EntryStack<*>>
		assertFalse(remove.test(entry(sb("A"))))
		assertTrue(remove.test(entry("vanilla stone")))
		assertTrue(remove.test(entry(null)))
		val added = rec.named("addEntries").single()[0] as Collection<*>
		assertEquals(listOf("A", "B"), added.map { ReiEntries.skyblockOf(it as EntryStack<*>)!!.catalogItemId.skyblockId })
	}

	@Test fun stackConversion() {
		val a = sb("ASPECT_OF_THE_END", 3)
		assertEquals(a.catalogItemId.identifier, SkyblockEntryDefinition.getIdentifier(null, a))
		assertEquals("skyblock", SkyblockEntryDefinition.getContainingNamespace(null, a))
		assertTrue(SkyblockEntryDefinition.equals(a, sb("ASPECT_OF_THE_END"), ComparisonContext.EXACT))
		assertFalse(SkyblockEntryDefinition.equals(a, sb("HYPERION"), ComparisonContext.EXACT))
		assertEquals(SkyblockEntryDefinition.hash(null, a, ComparisonContext.FUZZY), SkyblockEntryDefinition.hash(null, sb("ASPECT_OF_THE_END"), ComparisonContext.FUZZY))
		assertEquals(1, SkyblockEntryDefinition.normalize(null, a).getStackSize())
		assertFalse(SkyblockEntryDefinition.isEmpty(null, a))
		assertTrue(SkyblockEntryDefinition.isEmpty(null, sb("ASPECT_OF_THE_END", 0)))
		assertSame(SBItemStack::class.java, SkyblockEntryDefinition.valueType)
		val codec = SkyblockEntryDefinition.serializer.codec()
		val json = codec.encodeStart(JsonOps.INSTANCE, a).getOrThrow()
		assertEquals("ASPECT_OF_THE_END", codec.parse(JsonOps.INSTANCE, json).getOrThrow().catalogItemId.skyblockId)
		assertSame(a, ReiEntries.skyblockOf(entry(a)))
		assertNull(ReiEntries.skyblockOf(entry("x")))
		assertNull(ReiEntries.skyblockOf(null))
	}

	@Test fun onlyApexCategoriesShow() {
		assertFalse(ApexReiPlugin.hides(apexId("mobs")))
		assertTrue(ApexReiPlugin.hides(Identifier.withDefaultNamespace("crafting")))
		assertTrue(ApexReiPlugin.hides(Identifier.fromNamespaceAndPath("roughlyenoughitems", "plugins/information")))
	}

	@Test fun absentModGuard() {
		assertFalse(ReiCompat.enabledHere(), "the REI set must stay off without REI")
		assertFalse(CompatSwitch.mayLoad("dev.ashtonite365.apex.compat.rei.ApexReiPlugin"))
		val rec = Recorder()
		val plugin = ApexReiPlugin(pages = { listOf(crafting) }, entries = { listOf(sb("A")) }, repoReady = { true })
		plugin.registerCategories(rec.fake(CategoryRegistry::class.java))
		plugin.registerDisplays(rec.fake(DisplayRegistry::class.java))
		plugin.registerEntries(rec.fake(EntryRegistry::class.java))
		assertTrue(rec.calls.isEmpty(), "${rec.calls}")
		assertTrue(plugin.categories.isEmpty())
	}
}
