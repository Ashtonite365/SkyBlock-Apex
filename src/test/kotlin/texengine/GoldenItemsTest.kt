// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class GoldenItemsTest {
	private val engine: GoldenEngine = ApexGolden

	@BeforeEach
	fun loadConfigDefaults() = TexConfig.reset()

	@AfterEach
	fun resetConfig() = TexConfig.reset()

	private fun id(s: String) = Identifier.parse(s)

	@Test
	fun `G-ITEM-1 SkyBlock id to model id`() {
		val table = mapOf(
			"HYPERION" to "firmskyblock:hyperion",
			"ORCHID_MANTIS;0" to "firmskyblock:orchid_mantis__0",
			"PET:X" to "firmskyblock:pet___x",
			"A-B" to "firmskyblock:a__002db",
			"A B" to "firmskyblock:a__0020b",
			"ÄX" to "firmskyblock:__00e4x",
		)
		for ((sb, model) in table) assertEquals(id(model), engine.skyBlockModelId(sb), sb)
	}

	private val items by lazy { FixturePacks.manager("items") }

	@Test
	fun `G-ITEM-2 SkyBlock id override is produced regardless of existence`() {
		val stack = Stacks.sb(Items.IRON_SWORD, id = "HYPERION")
		assertEquals(id("firmskyblock:hyperion"), engine.itemOverride(items, stack, null, setOf(id("firmskyblock:hyperion"))))
		assertEquals(id("firmskyblock:hyperion"), engine.itemOverride(items, stack, null, setOf()))
	}

	@Test
	fun `G-ITEM-3 disabled means no SkyBlock-id override`() {
		TexConfig.set("enabled", false)
		assertNull(engine.itemOverride(items, Stacks.sb(Items.IRON_SWORD, id = "HYPERION"), null, setOf()))
	}

	@Test
	fun `G-ITEM-4 screen-scoped overrides`() {
		val paper = Stacks.sb(Items.PAPER, id = "SOME_PAPER")
		val bz = Component.literal("Bazaar ➜ Farming")
		assertEquals(id("firmskyblock:bz_icon"), engine.itemOverride(items, paper, bz, setOf(id("firmskyblock:bz_icon"))))
		assertEquals(id("firmskyblock:some_paper"), engine.itemOverride(items, paper, bz, setOf()))
		assertEquals(id("firmskyblock:some_paper"), engine.itemOverride(items, paper, Component.literal("Auction House"), setOf(id("firmskyblock:bz_icon"))))

		assertEquals(id("firmskyblock:bz_icon"), engine.itemOverride(items, Stacks.sb(Items.PAPER), bz, setOf(id("firmskyblock:bz_icon"))))
		assertNull(engine.itemOverride(items, Stacks.sb(Items.PAPER), null, setOf(id("firmskyblock:bz_icon"))))
	}

	@Test
	fun `G-ITEM-5 first matching screen override wins and is not retried`() {
		val paper = Stacks.sb(Items.PAPER)
		val bz = Component.literal("Bazaar")
		assertEquals(id("firmskyblock:bz_icon"), engine.itemOverride(items, paper, bz, setOf(id("firmskyblock:bz_icon"), id("firmskyblock:bz_icon_b"))))
		assertNull(engine.itemOverride(items, paper, bz, setOf(id("firmskyblock:bz_icon_b"))))

		assertEquals(id("firmskyblock:bz_sword"), engine.itemOverride(items, Stacks.sb(Items.DIAMOND_SWORD), bz, setOf(id("firmskyblock:bz_sword"))))
	}

	@Test
	fun `G-ITEM-6 override with a missing screen filter is dropped`() {
		val result = runCatching {
			engine.itemOverride(FixturePacks.manager("items_missing_filter"), Stacks.sb(Items.PAPER), Component.literal("Anything"), setOf(id("firmskyblock:never")))
		}

		assertTrue(result.isFailure || result.getOrNull() == null)
	}

	private val legacy = """{"parent":"item/generated","overrides":[
		{"predicate":{},"model":"m:a"},
		{"predicate":{"apex:display_name":"X"},"model":"m:b"},
		{"predicate":{"apex:display_name":"X"}},
		{"model":"m:never"}
	]}"""

	@Test
	fun `G-LEG-1 last matching override wins`() {
		assertEquals(id("m:b"), engine.legacySelect(legacy, Stacks.sb(name = Component.literal("X"))))
		assertEquals(id("m:a"), engine.legacySelect(legacy, Stacks.sb(name = Component.literal("Y"))))
	}

	@Test
	fun `G-LEG-2 entries without model skipped, without predicate never match`() {

		assertEquals(id("m:b"), engine.legacySelect(legacy, Stacks.sb(name = Component.literal("X"))))
		assertNull(engine.legacySelect("""{"overrides":[{"model":"m:never"}]}""", Stacks.sb()))
		assertNull(engine.legacySelect("""{"parent":"item/generated"}""", Stacks.sb()))
	}

	@Test
	fun `G-LEG-3 head model key`() {
		assertEquals(id("m:h"), engine.legacyHeadModel("""{"apex:head_model":"m:h"}"""))
		assertNull(engine.legacyHeadModel("""{"parent":"item/generated"}"""))
	}

	@Test
	fun `G-LEG-5 predicate model item definition decodes`() {
		assertTrue(engine.decodesPredicateModelType("""{"type":"apex:predicates/legacy",
			"fallback":{"type":"minecraft:model","model":"m:fallback"},
			"overrides":[{"model":{"type":"minecraft:model","model":"m:a"},"predicate":{"apex:display_name":"X"}}]}"""))
		assertFalse(engine.decodesPredicateModelType("""{"type":"apex:predicates/legacy","overrides":[]}"""))
	}

	private val prefixed = """{"overrides":[
		{"predicate":{"firmament:display_name":"X"},"model":"m:a"},
		{"predicate":{"firmament:display_name":"Y"},"model":"m:b"}
	]}"""

	@Test
	@ExpectedChange("prefix-alias: the legacy pack key prefix is read like apex:")
	fun `G-PFX-1 and 2 legacy-prefixed predicates`() {
		val x = engine.legacySelect(prefixed, Stacks.sb(name = Component.literal("X")))
		val y = engine.legacySelect(prefixed, Stacks.sb(name = Component.literal("Y")))
		val other = engine.legacySelect(prefixed, Stacks.sb(name = Component.literal("Z")))
		assertEquals(id("m:a"), x); assertEquals(id("m:b"), y); assertNull(other)
	}
}
