// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
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
class GoldenArmorTest {
	private val engine: GoldenEngine = ApexGolden

	@BeforeEach
	fun load() {
		engine.loadArmor(FixturePacks.manager("armor"))
	}

	@BeforeEach
	fun loadConfigDefaults() = TexConfig.reset()

	@AfterEach
	fun resetConfig() = TexConfig.reset()

	private fun id(s: String) = Identifier.parse(s)
	private fun helm(name: String) = Stacks.sb(Items.LEATHER_HELMET, id = "POWER_HELM", name = Component.literal(name))

	@Test
	fun `G-ARM-1 matching sub-override model`() {
		assertEquals(GoldenArmor(id("firmskyblock:shiny"), EquipmentSlot.HEAD), engine.armor(helm("Shiny"), EquipmentSlot.HEAD))
	}

	@Test
	fun `G-ARM-2 layers become a synthesised equipment asset`() {
		val asset = engine.armor(helm("Plain"), EquipmentSlot.HEAD).assetId
		assertNotNull(asset)
		assertEquals("apex", asset!!.namespace)
		assertTrue(asset.path.startsWith("sentinel/armor/"), asset.toString())
		val layers = engine.armorAssetLayers(asset)
		assertEquals(listOf(
			Triple("HUMANOID", id("firmskyblock:power"), 0xFFA06540.toInt()),
			Triple("HUMANOID_LEGGINGS", id("firmskyblock:power"), 0xFFA06540.toInt()),
		), layers)
	}

	@Test
	fun `G-ARM-3 toggle off`() {
		val asset = engine.armor(helm("Plain"), EquipmentSlot.HEAD).assetId!!
		TexConfig.set("armor-overrides", false)
		assertEquals(GoldenArmor(null), engine.armor(helm("Shiny"), EquipmentSlot.HEAD))
		assertNull(engine.armorAssetLayers(asset))
	}

	@Test
	fun `G-ARM-4 invalid files skipped, others load`() {
		assertEquals(GoldenArmor(null), engine.armor(Stacks.sb(Items.LEATHER_HELMET, id = "BAD_BOTH"), EquipmentSlot.HEAD))
		assertEquals(GoldenArmor(null), engine.armor(Stacks.sb(Items.LEATHER_HELMET, id = "BAD_NONE"), EquipmentSlot.HEAD))
		assertNotNull(engine.armor(helm("Plain"), EquipmentSlot.HEAD).assetId)
	}

	@Test
	fun `G-ARM-5 later file wins for a duplicate id`() {
		assertEquals(GoldenArmor(id("firmskyblock:dup_b"), EquipmentSlot.CHEST), engine.armor(Stacks.sb(Items.LEATHER_CHESTPLATE, id = "DUP"), EquipmentSlot.CHEST))
	}

	@Test
	fun `G-ARM-0 non-SkyBlock stack`() {
		assertEquals(GoldenArmor(null), engine.armor(Stacks.sb(Items.LEATHER_HELMET), EquipmentSlot.HEAD))
	}
}
