// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText
import net.minecraft.world.item.ItemStack

object StackSizeNumbers {
	@Config
	object TConfig : ManagedConfig("stack-size-numbers", Category.INVENTORY) {
		val stars by toggle("stars") { true }
		val minionTier by toggle("minion-tier") { true }
		val petLevel by toggle("pet-level") { true }
		val potionLevel by toggle("potion-level") { true }

		val masterSkullTier by toggle("master-skull-tier") { false }
		val newYearCakeYear by toggle("new-year-cake-year") { false }
		val kuudraKeyTier by toggle("kuudra-key-tier") { false }
		val skillLevel by toggle("skill-level") { false }
	}

	private val PET = Regex("^\\[Lvl (\\d+)]")
	private val MINION = Regex(" Minion ([IVXL]+)$")
	private val POTION = Regex("Potion ([IVXL]+)$|^.+ ([IVXL]+) Potion$")
	private const val MASTER_STARS = "➊➋➌➍➎"
	private val MASTER_SKULL = Regex("^Master Skull - Tier ([IVX]+|\\d+)$")
	private val CAKE = Regex("^New Year Cake \\(Year (\\d+)\\)$")
	private val KUUDRA_KEYS = listOf("Kuudra Key", "Hot Kuudra Key", "Burning Kuudra Key", "Fiery Kuudra Key", "Infernal Kuudra Key")
	private val SKILLS = setOf("Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting", "Alchemy", "Carpentry", "Runecrafting", "Social", "Taming", "Hunting")
	private val SKILL = Regex("^(\\w+) ([IVXL]+|\\d+)$")

	private fun numeralOrNumber(s: String): Int? = s.toIntOrNull() ?: romanToInt(s)

	private fun romanDigit(c: Char): Int? = when (c) {
		'I' -> 1
		'V' -> 5
		'X' -> 10
		'L' -> 50
		else -> null
	}

	fun romanToInt(roman: String): Int? {
		var sum = 0
		var largest = 0
		for (i in roman.indices.reversed()) {
			val d = romanDigit(roman[i]) ?: return null
			if (d < largest) {
				sum -= d
			} else {
				sum += d
				largest = d
			}
		}
		return if (sum > 0) sum else null
	}

	private fun dungeonStars(name: String): Int {
		var plain = 0
		for (c in name) if (c == '✪') plain++
		var master = 0
		for (c in name) {
			val idx = MASTER_STARS.indexOf(c)
			if (idx >= 0) { master = idx + 1; break }
		}
		return plain + master
	}

	fun numberFor(name: String): String? {
		if (TConfig.petLevel) PET.find(name)?.let { return it.groupValues[1] }
		if (TConfig.minionTier) MINION.find(name)?.let { return romanToInt(it.groupValues[1])?.toString() }
		if (TConfig.potionLevel && "Potion" in name) {
			val m = POTION.find(name)
			if (m != null) {
				val numeral = m.groupValues[1].ifEmpty { m.groupValues[2] }
				return romanToInt(numeral)?.toString()
			}
		}
		if (TConfig.masterSkullTier) MASTER_SKULL.find(name)?.let { return numeralOrNumber(it.groupValues[1])?.toString() }
		if (TConfig.newYearCakeYear) CAKE.find(name)?.let { return it.groupValues[1] }
		if (TConfig.kuudraKeyTier) {
			val tier = KUUDRA_KEYS.indexOf(name)
			if (tier >= 0) return (tier + 1).toString()
		}
		if (TConfig.skillLevel) {
			val m = SKILL.find(name)
			if (m != null && m.groupValues[1] in SKILLS) return numeralOrNumber(m.groupValues[2])?.toString()
		}
		if (TConfig.stars) {
			val total = dungeonStars(name)
			if (total > 0) return total.toString()
		}
		return null
	}

	private fun label(stack: ItemStack): String? {
		if (stack.isEmpty || stack.count > 1) return null
		return numberFor(stack.hoverName.bareText.trim())
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		if (!SkyBlockSession.inSkyBlock) return
		val text = label(event.slot.item) ?: return
		val ctx = event.context
		val pose = ctx.pose()
		pose.pushMatrix()
		pose.translate((event.slot.x + 17 - Client.font.width(text)).toFloat(), (event.slot.y + 9).toFloat())
		ctx.text(Client.font, text, 0, 0, 0xFFFFFFFF.toInt(), true)
		pose.popMatrix()
	}
}
