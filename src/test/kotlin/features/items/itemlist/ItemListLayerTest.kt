// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.itemlist

import java.io.File
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.ItemListModNotice
import dev.ashtonite365.apex.features.items.recipes.ItemListMods
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPages
import dev.ashtonite365.apex.features.items.recipes.itemlist.RecordingCanvas
import dev.ashtonite365.apex.features.items.recipes.itemlist.SkyblockEntries

class ItemListLayerTest {
	init { dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft() }

	@Test fun versionGuard() {
		assertEquals(listOf(1, 1, 24), ItemListMods.numericPrefix("1.1.24-SNAPSHOT+26.3+fabric"))
		assertEquals(listOf(26, 3, 824), ItemListMods.numericPrefix("26.3.824+fabric"))
		assertTrue(ItemListMods.REI.supports("26.3.824+fabric"))
		assertTrue(ItemListMods.REI.supports("26.1.10"))
		assertFalse(ItemListMods.REI.supports("21.11.814"))
		assertFalse(ItemListMods.REI.supports("garbage"))
		assertTrue(ItemListMods.EMI.supports("1.1.24-SNAPSHOT+26.3+fabric"))
		assertTrue(ItemListMods.EMI.supports("1.2.0"))
		assertFalse(ItemListMods.EMI.supports("1.0.30+1.20.1"))
		assertFalse(ItemListMods.EMI.supports("2.0.0"))
		assertTrue(ItemListMods.JEI.supports("anything"))
	}

	@Test fun absentModsAreNotUsable() {

		assertFalse(ItemListMods.REI.installed())
		assertFalse(ItemListMods.EMI.installed())
		assertFalse(ItemListMods.REI.usable())
		assertFalse(ItemListMods.EMI.usable())
	}

	@Test fun noticeIsDueOnlyWithoutASupportedMod() {
		assertTrue(ItemListModNotice.isDue(onSkyblock = true, wanted = true, supportedModInstalled = false, alreadyShown = false))
		assertFalse(ItemListModNotice.isDue(true, true, supportedModInstalled = true, alreadyShown = false))
		assertFalse(ItemListModNotice.isDue(true, true, false, alreadyShown = true))
		assertFalse(ItemListModNotice.isDue(false, true, false, false))
		assertFalse(ItemListModNotice.isDue(true, false, false, false))
	}

	@Test fun noticeAndSettingsNameNoMod() {
		val root = listOf(".", "..", "../..").map { File(it).absoluteFile.normalize() }.first { File(it, "translations/en_us.json").isFile }
		val tr = JsonParser.parseString(File(root, "translations/en_us.json").readText()).asJsonObject
		val keys = listOf(
			"apex.reiwarning", "apex.reiwarning.disable", "apex.reiwarning.disabled",
			"apex.config.repo.enable-rei", "apex.config.repo.enable-rei.description",
			"apex.config.repo.enable-super-craft.description",
			"apex.config.repo.warn-for-missing-item-list-mod", "apex.config.repo.warn-for-missing-item-list-mod.description",
			"apex.config.core-inventory-overlays.search-calculator.description",
		)
		val names = Regex("""\b(JEI|REI|EMI|Roughly Enough Items|Just Enough Items|Modrinth|CurseForge)\b""", RegexOption.IGNORE_CASE)
		for (k in keys) {
			val v = tr.get(k)?.asString ?: error("missing $k")
			assertFalse(names.containsMatchIn(v), "$k names a mod: $v")
		}
		val notice = ItemListModNotice.message().string
		assertFalse(names.containsMatchIn(notice), notice)
	}

	@Test fun recordingCanvasSplitsInputsAndOutputs() {
		val page = FakePage(apexId("fake"), listOf("a"))
		val rec = RecordingCanvas.of(page, "a")
		assertEquals(3, rec.slots.size)
		assertEquals(listOf(listOf("IN_a", "IN2_a")), page.inputs("a").map { l -> l.map { it.catalogItemId.skyblockId } })
		assertEquals(listOf(listOf("OUT_a")), page.outputs("a").map { l -> l.map { it.catalogItemId.skyblockId } })
		assertEquals(1, rec.labels.size)
	}

	@Test fun everyPageHasItsOwnId() {
		val ids = ItemListPages.all().map { it.id }
		assertEquals(ids.size, ids.toSet().size, "$ids")
		assertTrue(ItemListPages.MOBS in ids && ItemListPages.HOW_TO in ids && ItemListPages.PRICES in ids)
		assertTrue(ids.all { it.namespace == "apex" }, "$ids")
	}

	@Test fun entryIdentityAndNormalForm() {
		assertEquals(SkyblockEntries.identity(sb("ASPECT_OF_THE_END", 5)), SkyblockEntries.identity(sb("ASPECT_OF_THE_END")))
		assertTrue(SkyblockEntries.identity(sb("A")) != SkyblockEntries.identity(sb("B")))
		assertEquals(1, SkyblockEntries.normalize(sb("A", 40)).getStackSize())
		assertEquals(null, SkyblockEntries.countLabel(1, 64))
	}
}
