package dev.ashtonite365.apex.test.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.UnbindWardrobeKeysMigration
import dev.ashtonite365.apex.features.inventory.WardrobeKeybinds
import dev.ashtonite365.apex.features.kit.skyblock.LoadoutKeybinds
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

class UnbindWardrobeKeysTest {
	private val unbound = SavedKeyBinding.unbound()
	private fun key(code: Int) = SavedKeyBinding.keyWithoutMods(code)
	private fun json(kb: SavedKeyBinding) = Json.encodeToJsonElement(kb)
	private val wardrobe = UnbindWardrobeKeysMigration.OLD_DEFAULTS.getValue("wardrobe-keybinds")

	@AfterEach
	fun restore() {
		WardrobeKeybinds.TConfig.explicitDefaultLoad()
		LoadoutKeybinds.TConfig.explicitDefaultLoad()
	}

	@Test
	fun defaultsAreUnbound() {
		WardrobeKeybinds.TConfig.explicitDefaultLoad()
		LoadoutKeybinds.TConfig.explicitDefaultLoad()
		val w = WardrobeKeybinds.TConfig
		val l = LoadoutKeybinds.TConfig
		(listOf(w.changePageKeybind, w.nextPage, w.previousPage, l.nextPage, l.previousPage) +
			w.slotKeybinds.map { it.get() } + l.slotKeys.map { it.get() }).forEach { assertEquals(unbound, it) }

		for ((cfg, ids) in UnbindWardrobeKeysMigration.OLD_DEFAULTS) {
			val c = if (cfg == "wardrobe-keybinds") w else l
			assertEquals(cfg, c.name)
			ids.keys.forEach { assertEquals(unbound, c.allOptions.getValue(it).get(), "$cfg.$it") }
		}
		assertEquals(12, wardrobe.size)
	}

	@Test
	fun migrationUnbindsOldDefaults() {
		val saved = JsonObject(wardrobe.mapValues { (_, code) -> json(key(code)) })
		val out = UnbindWardrobeKeysMigration.apply(saved, wardrobe)
		wardrobe.keys.forEach { assertEquals(json(unbound), out[it], it) }
		assertEquals(JsonPrimitive(UnbindWardrobeKeysMigration.VERSION), out[UnbindWardrobeKeysMigration.FLAG])
	}

	@Test
	fun migrationKeepsCustomBinds() {
		val ctrlA = SavedKeyBinding.keyWithMods(InputConstants.KEY_A, InputModifiers.ofKeyCodes(InputConstants.KEY_LCONTROL))
		val saved = buildJsonObject {
			put("next-page", json(key(InputConstants.KEY_RIGHT)))
			put("previous-page", json(ctrlA))
			put("slot-1", json(key(InputConstants.KEY_F1)))
			put("slot-2", json(key(InputConstants.KEY_2)))
			put("allow-unequipping", false)
		}
		val out = UnbindWardrobeKeysMigration.apply(saved, wardrobe)
		assertEquals(saved["next-page"], out["next-page"])
		assertEquals(saved["previous-page"], out["previous-page"])
		assertEquals(saved["slot-1"], out["slot-1"])
		assertEquals(json(unbound), out["slot-2"])
		assertEquals(saved["allow-unequipping"], out["allow-unequipping"])
		assertEquals(null, out["change-page"], "absent options stay absent and load the new unbound default")
	}

	@Test
	fun migrationRunsOnce() {
		val first = UnbindWardrobeKeysMigration.apply(JsonObject(mapOf("slot-3" to json(key(InputConstants.KEY_3)))), wardrobe)
		assertEquals(json(unbound), first["slot-3"])

		val rebound = JsonObject(first + ("slot-3" to json(key(InputConstants.KEY_3))))
		assertSame(rebound, UnbindWardrobeKeysMigration.apply(rebound, wardrobe))
	}

	@Test
	fun slotDefaultsAreTheDigitKeys() {
		assertEquals(InputConstants.KEY_1, wardrobe["slot-1"])
		assertEquals(InputConstants.KEY_9, wardrobe["slot-9"])
		assertEquals(InputConstants.KEY_2, wardrobe["slot-2"])
	}

	@Test
	fun portedSlotDefaultsUnbound() {
		val ported = UnbindWardrobeKeysMigration.PORTED_DEFAULTS.getValue("wardrobe-keybinds")
		val shift = InputModifiers.ofKeyCodes(InputConstants.KEY_LSHIFT)
		val saved = buildJsonObject {
			put("slot-1", json(key(InputConstants.KEY_0 + 1)))
			put("slot-5", json(key(InputConstants.KEY_0 + 5)))
			put("slot-6", json(SavedKeyBinding.keyWithMods(InputConstants.KEY_0 + 6, shift)))
			put("slot-7", json(key(InputConstants.KEY_0 + 5)))
			put("change-page", json(key(InputConstants.KEY_RETURN)))
			put("wardrobe-keybinds", true)
		}
		val out = UnbindWardrobeKeysMigration.apply(saved, wardrobe, ported)
		assertEquals(json(unbound), out["slot-1"])
		assertEquals(json(unbound), out["slot-5"])
		assertEquals(saved["slot-6"], out["slot-6"])
		assertEquals(saved["slot-7"], out["slot-7"])
		assertEquals(json(unbound), out["change-page"])
		assertEquals(saved["wardrobe-keybinds"], out["wardrobe-keybinds"])
		assertEquals(UnbindWardrobeKeysMigration.migrate("wardrobe-keybinds", saved), out)
	}

	@Test
	fun wiredIntoConfigLoadAndSave() {
		val c = LoadoutKeybinds.TConfig
		c.loadFrom(Unit, buildJsonObject {
			put(c.name, buildJsonObject {
				put("next-page", json(key(InputConstants.KEY_RIGHT)))
				put("previous-page", json(key(InputConstants.KEY_P)))
			})
		})
		assertEquals(unbound, c.nextPage)
		assertEquals(key(InputConstants.KEY_P), c.previousPage)
		val saved = c.saveTo(Unit)[c.name] as JsonObject
		assertEquals(JsonPrimitive(UnbindWardrobeKeysMigration.VERSION), saved[UnbindWardrobeKeysMigration.FLAG])

		c.loadFrom(Unit, JsonObject(mapOf(c.name to JsonObject(saved + ("next-page" to json(key(InputConstants.KEY_RIGHT)))))))
		assertEquals(key(InputConstants.KEY_RIGHT), c.nextPage)
	}
}
