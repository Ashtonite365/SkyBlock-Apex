// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import me.shedaniel.math.Rectangle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.inventory.buttons.ButtonIcons
import dev.ashtonite365.apex.features.inventory.buttons.ButtonLayout
import dev.ashtonite365.apex.features.inventory.buttons.ButtonPresets
import dev.ashtonite365.apex.features.inventory.buttons.InventoryButton

@ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class ButtonsGoldenTest {
	private fun text(b: InventoryButton) = listOf(b.x, b.y, b.anchorRight, b.anchorBottom, b.icon, b.command, b.isGigantic).joinToString(",")

	private fun button(s: String): InventoryButton {
		val f = s.split(",")
		fun opt(v: String) = v.takeIf { it != "null" }
		return InventoryButton(f[0].toInt(), f[1].toInt(), f[2].toBoolean(), f[3].toBoolean(), opt(f[4]), opt(f[5]), f[6].toBoolean())
	}

	private fun rect(s: String) = s.split(",").map(String::toInt).let { Rectangle(it[0], it[1], it[2], it[3]) }

	private fun layoutText(pair: Pair<List<InventoryButton>, Int>) = pair.first.joinToString(" | ", transform = ::text) + " msgs=${pair.second}"

	private val presets = mapOf(
		"reset" to ButtonPresets.EMPTY, "simple" to ButtonPresets.SIMPLE, "warps" to ButtonPresets.ALL_WARPS,
		"garbage" to "hello", "empty" to "", "wrongprefix" to "Tk9QRS9bXQ==",
	)

	@Test
	fun presetsAndFormats() {
		val n = Golden.check("inventory-buttons") { key ->
			val (kind, arg) = key.substringBefore(' ') to key.substringAfter(' ', "")
			when (kind) {
				"load" -> ButtonPresets.decode(presets.getValue(arg))?.joinToString(" | ", transform = ::text) ?: "null"
				"import" -> layoutText(ButtonLayout.fitImported(ButtonLayout.withoutSlashes(ButtonPresets.decode(presets.getValue(arg))!!)))
				"save" -> ButtonPresets.encode(sample.map(::button))
				"json" -> sample.map(::button).joinToString(" | ") { Apex.json.encodeToString(InventoryButton.serializer(), it) }
				"decode-min" -> text(Apex.json.decodeFromString(InventoryButton.serializer(), """{"x":1,"y":2,"anchorRight":true,"anchorBottom":false}"""))
				"move" -> layoutText(ButtonLayout.fitImported(importSets[arg.toInt()].map(::button)))
				"bounds" -> {
					val (frame, b) = arg.split("|")
					val btn = button(b)
					val a = btn.areaIn(rect(frame))
					"${a.x},${a.y},${a.width},${a.height} valid=${btn.isUsable()}"
				}
				"mouse" -> {
					val (frame, shift, mouse) = arg.split("|")
					val r = rect(frame)
					val (mx, my) = mouse.split(",").map(String::toInt)
					val spot = ButtonLayout.spotAt(mx, my, r, shift.toBoolean())
					if (spot == null) "null " else {
						val dragged = listOf(false, true).joinToString(";") { big ->
							InventoryButton(0, 0, false, false, "a", "b", big).also { ButtonLayout.dragTo(it, spot, r) }.let { "${it.x},${it.y}" }
						}
						"${spot.anchorRight},${spot.anchorBottom},${spot.x},${spot.y} $dragged"
					}
				}
				"give" -> ButtonIcons.itemArgument(arg)
				else -> error("unknown record $key")
			}
		}
		assertEquals(361, n)
	}

	@Test
	fun roundTripAndSlashes() {
		val buttons = sample.map(::button)
		assertEquals(buttons, ButtonPresets.decode(ButtonPresets.encode(buttons)))
		assertEquals(listOf("pets", null, "warp hub"), ButtonLayout.withoutSlashes(listOf(button("0,0,true,false,a,/pets,false"), button("0,0,true,false,a,null,false"), button("0,0,true,false,a,warp hub,false"))).map { it.command })
		assertTrue(ButtonLayout.fitImported(emptyList()).first.isEmpty())
	}

	private val sample = listOf("0,-18,false,false,stone,warp hub,false", "5,5,true,true,skull:abc,/pets,true", "-20,0,false,true,null,null,false")
	private val importSets = listOf(
		listOf("5,5,false,false,a,b,false", "10,20,false,false,c,d,true", "0,-18,false,false,e,f,false", "18,-18,false,false,g,h,false"),
		listOf("1,1,false,false,a,/b,false", "1,1,false,false,a,b,false", "1,1,true,false,a,b,false", "1,1,false,true,a,b,false", "0,1,false,false,a,b,false", "1,0,false,false,a,b,false"),
		(0 until 12).map { "0,${-18 * (it % 3)},false,false,i$it,c$it,${it % 4 == 0}" } + listOf("3,3,false,false,x,y,false", "4,4,false,false,x,y,true"),
	)
}
