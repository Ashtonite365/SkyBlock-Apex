// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import kotlinx.serialization.json.JsonObject
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.inventory.PetFeatures
import dev.ashtonite365.apex.features.inventory.PetHudText
import dev.ashtonite365.apex.features.inventory.PetReadings
import dev.ashtonite365.apex.features.inventory.PetRecord
import dev.ashtonite365.apex.features.inventory.PetTracker
import dev.ashtonite365.apex.util.formattedString
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.skyblock.Rarity

@ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class PetGoldenTest {
	companion object {

		val widgets = listOf(
			listOf(" §7[Lvl 100] §6Golden Dragon", " §6Dragon Claw", " §bMAX LEVEL", " §6+§e1,234,567 XP"),
			listOf("§e§lPet:", " §7[Lvl 87] §5Ender Dragon", " §9Textbook", " §e1.2M§6/§e3.4M XP §6(35.2%)"),
			listOf(" §7[Lvl 5] §fBee", " §e120§6/§e400 XP §6(30%)"),
			listOf(" §7[Lvl 12] §aRock", " §dTier Boost", " §e1,000§6/§e2,500 XP §6(40.0%)", " Kat: 2h"),
			listOf(" §7[Lvl 100] §dBlack Cat", " §bMAX LEVEL"),
			listOf("Nothing here"),
			listOf(),
			listOf(" §7[Lvl 30] §9Griffin", " §6Antique Remedies"),
			listOf(" §7[Lvl 1] §9Griffin", " §7[Lvl 2] §5Griffin"),
			listOf(" §7[Lvl 50] §5Baby Yeti", " §e3k§6/§e10k XP §6(30.1%)", " §e5k§6/§e10k XP §6(50.1%)"),
			listOf(" §7[Lvl 100] §6Bal", " §6+§e12k XP"),
			listOf(" §7[Lvl 100] §6Bal", " §6+§e12,000 XP", " §bMAX LEVEL"),
		).map { set -> set.map { Component.literal(it) } }

		fun row(p: PetRecord?): String = p?.run {
			listOf(name, rarity, level, maxLevel, xpInLevel, xpToNext, xpTotal, xpAtLevelStart, xpForMax, xpOverflow, heldItem, fromMenu)
				.joinToString("|")
		} ?: "null"

		fun record(name: String, rarity: Rarity, level: Int, inLevel: Double, toNext: Double, total: Double, held: String,
		           cap: Int = 100, exact: Boolean = true) =
			PetRecord(name, rarity, level, cap, inLevel, toNext, total, total - inLevel, 1_000_000.0, 0.0, held,
				lazy { KeptStack.empty() }, exact)
	}

	init {
		PetFeatures.TConfig.loadFrom(Unit, JsonObject(mapOf()))
	}

	@Test fun readings() {
		val bee = { held: String, exact: Boolean -> record("Bee", Rarity.RARE, 10, 100.0, 500.0, 2000.0, held, exact = exact) }
		val laterReadings = listOf(
			{ record("Bee", Rarity.RARE, 11, 5.0, 600.0, 2600.0, "Unknown") },
			{ record("Bee", Rarity.RARE, 10, 200.0, 500.0, 2100.0, "§aCrochet") },
			{ record("Bee", Rarity.RARE, 9, 50.0, 400.0, 1500.0, "§aX") },
			{ record("Bee", Rarity.RARE, 10, 100.0, 500.0, 2000.0, "Unknown").also { it.xpOverflow = 5.0; it.xpAtLevelStart = 9999.0 } },
		)
		val n = Golden.check("pets-parse") { key ->
			val (kind, arg) = key.split(" ", limit = 2)
			when (kind) {
				"tab" -> row(PetReadings.fromTab(widgets[arg.toInt()]))
				"chat" -> arg.split(",").let { (name, code, lvl) -> row(PetReadings.fromChat(name, code, lvl.toInt())) }
				"update" -> arg.split(",").map { it.toInt() }.let { (b, o) ->
					val base = if (b == 0) bee("§9Textbook", true) else bee("Unknown", false)
					base.absorb(laterReadings[o]()); row(base)
				}
				else -> error(key)
			}
		}
		assertEquals(132, n)
	}

	private fun replay(script: String, coloured: Boolean): String {
		val t = PetTracker()
		val out = mutableListOf<String>()
		val menuPets = HashMap<String, PetRecord>()
		for (ev in script.split("|")) {
			val a = ev.split(":")
			when (a[0]) {
				"hud" -> out += when (val pick = t.pickForHud(PetReadings.fromTab(widgets[a[1].toInt()]))) {
					PetTracker.HudPick.Hidden -> "hidden"
					PetTracker.HudPick.NothingKnown -> PetHudText.nothingKnown.joinToString(" / ") { Golden.styled(it) }
					is PetTracker.HudPick.Show -> PetHudText.lines(pick.pet, coloured).joinToString(" / ") { Golden.styled(it) }
				}
				"chat" -> t.chatLine(Component.literal(ev.removePrefix("chat:")).formattedString())
				"switch" -> t.forget()
				"menu" -> {
					val p = record(a[2], PetReadings.rarityOfColourCode(a[3])!!, a[4].toInt(), 10.0, 100.0, 1000.0, "None")
					menuPets[a[1]] = p
					t.menuItemShown(a[1], a[5] == "1") { p }
				}
				"click" -> {
					val p = menuPets[a[1]]

					if (a.getOrNull(4) != "inv" && p != null) t.menuItemClicked(a[1], a[2] == "1", a[3].toInt()) { p }
				}
			}
			out += "cur=" + row(t.current())
		}
		return out.joinToString(" ;; ")
	}

	@Test fun tracking() {
		val n = Golden.check("pets-tracking") { key ->
			val (coloured, script) = key.split("|", limit = 2)
			replay(script, coloured.toBoolean())
		}
		assertEquals(34, n)
	}
}
