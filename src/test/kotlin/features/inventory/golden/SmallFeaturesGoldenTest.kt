// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.inventory.CursorRestore
import dev.ashtonite365.apex.features.inventory.ItemHotkeys
import dev.ashtonite365.apex.features.inventory.ItemRarityCosmetics
import dev.ashtonite365.apex.features.inventory.JunkHighlighter
import dev.ashtonite365.apex.features.inventory.WardrobeKeybinds
import dev.ashtonite365.apex.util.skyblock.Rarity

@ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class SmallFeaturesGoldenTest {

	private fun cursor(enabled: Boolean, script: String): String {
		var clock = 0L
		val memory = CursorRestore.Memory { clock }
		val results = mutableListOf<String>()
		for (step in script.trim().split(" ")) {
			val (what, time) = step.split("@")
			clock = time.toLong()
			val (x, y) = what.drop(1).split(",").map(String::toDouble)
			when (what.first()) {
				'o' -> memory.beforeGrab(x, y)
				'm' -> memory.afterCentre(x, y, enabled)
				'l' -> results += memory.onRelease(x, y, enabled, 500).toString()
			}
		}
		return results.joinToString(" ")
	}

	@Test
	fun recordsMatch() {
		val rows = Golden.check("small-features") { key ->
			val (kind, arg) = key.substringBefore(' ') to key.substringAfter(' ')
			when (kind) {
				"rarity" -> if (arg == "fallback") "${ItemRarityCosmetics.UNKNOWN_TINT}" else "${ItemRarityCosmetics.tintFor(Rarity.valueOf(arg))}"
				"cursor" -> cursor(arg.substringBefore('|').toBoolean(), arg.substringAfter('|'))
				"junk" -> "${JunkHighlighter.isJunk(arg.substringAfterLast('|'), arg.substringBeforeLast('|'))}"
				"hotkey" -> arg.split('|').let { ItemHotkeys.lookUpCommand("Name", it[0].toBoolean(), it[1].toBoolean()) ?: "none" }
				"wardrobe" -> {
					val what = arg.substringAfter(' ')
					when (arg.substringBefore(' ')) {
						"title" -> "${WardrobeKeybinds.pageTitle.matches(what)}"
						"slot" -> "${WardrobeKeybinds.setButtonSlot(what.toInt())}"
						"click" -> what.split('|').let { (item, allow) ->
							"${WardrobeKeybinds.maySelect(item == "lime_dye", item == "gray_dye", allow.toBoolean())}"
						}
						"page" -> what.split('|').let { (k, back, fwd) ->
							WardrobeKeybinds.arrowToClick(k != "next", k != "prev", back.toBoolean(), fwd.toBoolean())?.toString() ?: "none"
						}
						else -> error("unknown wardrobe record $key")
					}
				}
				else -> error("unknown record $key")
			}
		}
		assertEquals(111, rows)
	}
}
