// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.hud.BarStyle
import dev.ashtonite365.apex.util.hud.BarStyleChoice
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay

class RetiredChoicesTest {
	@Test
	fun removedBarStylesAreRetiredAndGone() {
		val live = BarStyle.entries.map { it.serializedName } + BarStyleChoice.entries.map { it.serializedName }
		for (id in RetiredChoices.BAR_STYLES) {
			assertTrue(RetiredChoices.isRetired(BarStyle::class.java.name, JsonPrimitive(id)), id)
			assertTrue(RetiredChoices.isRetired(BarStyleChoice::class.java.name, JsonPrimitive(id)), id)
			assertFalse(id in live, "$id still selectable")
		}
		for (id in listOf("standard", "classic", "apex", "rounded", "accent"))
			assertFalse(RetiredChoices.isRetired(BarStyle::class.java.name, JsonPrimitive(id)), id)
	}

	@Test
	fun removedStorageStylesAreRetired() {
		val cls = StorageOverlay.StorageTextureStyle::class.java.name
		for (id in RetiredChoices.STORAGE_STYLES) assertTrue(RetiredChoices.isRetired(cls, JsonPrimitive(id)), id)
		assertFalse(RetiredChoices.isRetired(cls, JsonPrimitive("STANDARD")))
		assertTrue(StorageOverlay.StorageTextureStyle.entries.map { it.name } == listOf("STANDARD"))
	}

	@Test
	fun removedTexturesAreOutOfTheResources() {
		for (p in listOf("/assets/apex/textures/styles/bar/plus_1_fill.png", "/assets/apex/textures/styles/bar/flat_rounded_fill.png",
			"/assets/apex/textures/styles/bar/classic_pill_fill.png", "/assets/apex/textures/gui/sprites/storageoverlay/classic_0/storage_row.png"))
			assertTrue(javaClass.getResource(p) == null, p)
		assertTrue(javaClass.getResource("/assets/apex/textures/styles/bar/apex_fill.png") != null)
		assertTrue(javaClass.getResource("/assets/apex/textures/gui/sprites/storageoverlay/storage_row.png") != null)
	}
}
