// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.textures

import java.util.UUID
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.repo.EssenceSkinExport
import dev.ashtonite365.apex.repo.LearnedSkullTextures
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.mc.applyEncodedSkin

@ExtendWith(AutoBootstrapExtension::class)
class EssenceSkinExportTest {
	@Test fun nameToId() {
		assertEquals("ESSENCE_WITHER", EssenceSkinExport.idFor("Wither Essence"))
		assertEquals("ESSENCE_DIAMOND", EssenceSkinExport.idFor("§bDiamond Essence"))
		assertEquals("ESSENCE_UNDEAD", EssenceSkinExport.idFor("  Undead Essence "))
		assertNull(EssenceSkinExport.idFor("Essence"))
		assertNull(EssenceSkinExport.idFor("Wither Catalyst"))

		for (n in listOf("Forest Essence", "Fossil Essence", "Wither Essence", "Gold Essence"))
			assertTrue(EssenceSkinExport.accepted(EssenceSkinExport.idFor(n)!!), n)
		assertFalse(EssenceSkinExport.accepted("ESSENCE_"))
		assertTrue(EssenceSkinExport.isEssenceEntry("Forest Essence", listOf("You currently own 12 Forest", "Essence.")))
		assertTrue(EssenceSkinExport.isEssenceEntry("Fossil Essence", listOf("You currently own 0 Fossil Essence.")))
	}

	@Test fun eachIdLoggedOncePerChange() {
		val a = mapOf("ESSENCE_GOLD" to "g")
		val b = a + ("ESSENCE_WITHER" to "w")
		assertEquals(mapOf("ESSENCE_WITHER" to "w"), EssenceSkinExport.changes(a, b))
		assertEquals(emptyMap<String, String>(), EssenceSkinExport.changes(b, b))
		assertEquals(mapOf("ESSENCE_GOLD" to "g2"), EssenceSkinExport.changes(b, b + ("ESSENCE_GOLD" to "g2")))
	}

	@Test fun exportFileIsOutsideTheManagedConfigFolder() {

		val cfg = java.nio.file.Path.of("config/apex").toAbsolutePath().normalize()
		val f = EssenceSkinExport.exportPath(java.nio.file.Path.of(".apex")).toAbsolutePath().normalize()
		assertFalse(f.startsWith(cfg), "$f must not be under $cfg")
		assertEquals("essence-skins.json", f.fileName.toString())
	}

	@Test fun detectsTheEssencePageByLore() {

		assertTrue(EssenceSkinExport.isEssenceEntry("Wither Essence", listOf("You currently own 119 Wither", "Essence.", "", "Why are we still here? Just to", "suffer...")))
		assertFalse(EssenceSkinExport.isEssenceEntry("Your Essence", listOf("Collect Essence in Dungeons which", "can be used to upgrade gear and")))
		assertFalse(EssenceSkinExport.isEssenceEntry("Gold Essence", listOf("§7Essence (currency)")))
	}

	@Test fun textureFromARealShapedProfileComponent() {

		val value = java.util.Base64.getEncoder().encodeToString(
			"{\"timestamp\":1559580362553,\"profileId\":\"e76f0d9af7f9454b8e5f6fd2b1d2ec3a\",\"profileName\":\"Wither\",\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/c4db4adfa9bf48ff5d41707ae34ea78bd2371659fcd8cd8934749af4cce9b\"}}}".toByteArray())
		val uuid = UUID.fromString("e0f2a0c2-6c42-3d35-9c5f-3a1d58d0e0a1")
		val stack = ItemStack(Items.PLAYER_HEAD).apply { applyEncodedSkin(uuid, value) }
		assertEquals(LearnedSkullTextures.Learned(uuid.toString(), value), LearnedSkullTextures.liveTexture(stack))
		assertNull(LearnedSkullTextures.liveTexture(ItemStack(Items.PLAYER_HEAD)))
	}

	@Test fun exportFormat() {
		val skins = mapOf("ESSENCE_WITHER" to "dGV4dHVyZQ==", "ESSENCE_GOLD" to "Z29sZA==")
		assertEquals(listOf("[ApexEssenceSkins] ESSENCE_GOLD=Z29sZA==", "[ApexEssenceSkins] ESSENCE_WITHER=dGV4dHVyZQ=="), EssenceSkinExport.logLines(skins))
		val parsed = kotlinx.serialization.json.Json.parseToJsonElement(EssenceSkinExport.json(skins)).toString()
		assertEquals("{\"ESSENCE_GOLD\":\"Z29sZA==\",\"ESSENCE_WITHER\":\"dGV4dHVyZQ==\"}", parsed)
	}
}
