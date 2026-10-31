// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.texengine.apex.ApexArmor
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class ApexArmorTest {
	@BeforeEach
	fun setUp() {
		TexConfig.reset()
		ApexGolden.id
		ApexArmor.use(ApexArmor.Loaded.EMPTY)
		ApexArmor.useResources(null)
	}

	@AfterEach
	fun tearDown() = setUp()

	private fun id(s: String) = Identifier.parse(s)

	private fun pack(name: String, vararg files: Pair<String, String>): Path {
		val root = Path.of("texengine-test-packs").toAbsolutePath().resolve(name)
		if (Files.exists(root)) root.toFile().deleteRecursively()
		Files.createDirectories(root)
		Files.writeString(root.resolve("pack.mcmeta"), """{"pack":{"pack_format":1,"description":""}}""")
		for ((path, content) in files) {
			val f = root.resolve(path)
			Files.createDirectories(f.parent)
			Files.writeString(f, content)
		}
		return root
	}

	@Test
	fun `G-ARM-6 legacy layer texture path used when the pack has it`() {
		val p = pack("legacy-armor", "assets/minecraft/textures/models/armor/diamond_layer_1.png" to "x", "assets/minecraft/textures/models/armor/diamond_layer_2.png" to "x")
		ApexArmor.useResources(FixturePacks.managerAt(p))
		assertEquals(id("minecraft:textures/models/armor/diamond_layer_1.png"), ApexArmor.legacyLayerTexture(id("minecraft:diamond"), leggings = false))
		assertEquals(id("minecraft:textures/models/armor/diamond_layer_2.png"), ApexArmor.legacyLayerTexture(id("minecraft:diamond"), leggings = true))
		assertNull(ApexArmor.legacyLayerTexture(id("minecraft:iron"), leggings = false))
	}

	@Test
	fun `G-ARM-6 toggle off leaves layer textures unchanged`() {
		val p = pack("legacy-armor-off", "assets/minecraft/textures/models/armor/diamond_layer_1.png" to "x")
		ApexArmor.useResources(FixturePacks.managerAt(p))
		TexConfig.set("legacy-minecraft-path-support", false)
		assertNull(ApexArmor.legacyLayerTexture(id("minecraft:diamond"), leggings = false))
	}

	@Test
	fun `new resources clear the legacy lookup cache`() {
		val without = pack("legacy-armor-a")
		ApexArmor.useResources(FixturePacks.managerAt(without))
		assertNull(ApexArmor.legacyLayerTexture(id("minecraft:gold"), leggings = false))
		val with = pack("legacy-armor-b", "assets/minecraft/textures/models/armor/gold_layer_1.png" to "x")
		ApexArmor.useResources(FixturePacks.managerAt(with))
		assertEquals(id("minecraft:textures/models/armor/gold_layer_1.png"), ApexArmor.legacyLayerTexture(id("minecraft:gold"), leggings = false))
	}

	@Test
	fun `equippable is built once per look and slot`() {
		ApexArmor.use(ApexArmor.load(FixturePacks.manager("armor")))
		val helm = Stacks.sb(Items.LEATHER_HELMET, id = "POWER_HELM", name = Component.literal("Plain"))
		val a = ApexArmor.equippableFor(helm, EquipmentSlot.HEAD)
		assertSame(a, ApexArmor.equippableFor(helm.copy(), EquipmentSlot.HEAD))
		assertEquals(EquipmentSlot.CHEST, ApexArmor.equippableFor(helm, EquipmentSlot.CHEST)!!.slot())
	}

	@Test
	fun `each reload makes new asset ids and nothing changes until applied`() {
		ApexArmor.use(ApexArmor.load(FixturePacks.manager("armor")))
		val helm = Stacks.sb(Items.LEATHER_HELMET, id = "POWER_HELM", name = Component.literal("Plain"))
		val first = ApexArmor.equippableFor(helm, EquipmentSlot.HEAD)!!.assetId().get().identifier()
		val pending = ApexArmor.load(FixturePacks.manager("armor"))
		assertEquals(first, ApexArmor.equippableFor(helm, EquipmentSlot.HEAD)!!.assetId().get().identifier())
		ApexArmor.use(pending)
		val second = ApexArmor.equippableFor(helm, EquipmentSlot.HEAD)!!.assetId().get().identifier()
		assertNotEquals(first, second)
		assertNull(ApexArmor.clientInfoFor(first))
	}

	@Test
	fun `armour ids are case-sensitive raw SkyBlock ids`() {
		ApexArmor.use(ApexArmor.load(FixturePacks.manager("armor")))
		assertNull(ApexArmor.equippableFor(Stacks.sb(Items.LEATHER_HELMET, id = "power_helm"), EquipmentSlot.HEAD))
	}
}
