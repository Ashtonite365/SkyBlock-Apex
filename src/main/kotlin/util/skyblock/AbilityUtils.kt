// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import kotlin.time.Duration
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.ownLiteralText
import dev.ashtonite365.apex.util.parseShortNumberOrNull
import dev.ashtonite365.apex.util.parseTimePattern

object AbilityUtils {
	@JvmInline
	value class AbilityActivation(val label: String) {
		companion object Known {
			val EMPTY = AbilityActivation("")
			val SNEAK = AbilityActivation("SNEAK")
			val RIGHT_CLICK = AbilityActivation("RIGHT CLICK")
			val SNEAK_RIGHT_CLICK = AbilityActivation("SNEAK RIGHT CLICK")

			fun fromHeader(printed: String?): AbilityActivation {
				val label = printed?.trim().orEmpty()
				return if (label.isEmpty()) EMPTY else AbilityActivation(label)
			}
		}
	}

	data class ItemAbility(
		val name: String,
		val activation: AbilityActivation,
		val hasPowerScroll: Boolean,
		val descriptionLines: List<Component>,
		val manaCost: Int?,
		val cooldown: Duration?,
	)

	private const val SCROLL_MARK = "⦾ "
	private const val MANA_LABEL = "Mana Cost: "
	private const val COOLDOWN_LABEL = "Cooldown: "
	private val NAME_PART = Regex("Ability: (.*?) *")

	private class HeaderParts(val name: String, val activation: AbilityActivation, val scroll: Boolean)

	private fun header(line: Component): HeaderParts? {
		val parts = line.siblings.map { it.ownLiteralText.orEmpty() }
		var i = 0
		val scroll = parts.getOrNull(i) == SCROLL_MARK
		if (scroll) i++
		val name = parts.getOrNull(i)?.let { NAME_PART.matchEntire(it) }?.groupValues?.get(1) ?: return null
		i++
		val activation = AbilityActivation.fromHeader(parts.getOrNull(i))
		if (i + 1 < parts.size) {
			Faults.reportFault("Found abilityName $name without finding but encountered unprocessable element in: ${line.bareText}")
			return null
		}
		return HeaderParts(name, activation, scroll)
	}

	fun abilitiesIn(lore: List<Component>): List<ItemAbility> {
		val found = mutableListOf<ItemAbility>()
		var i = 0
		while (i < lore.size) {
			val head = header(lore[i++]) ?: continue
			val description = mutableListOf<Component>()
			var mana: Int? = null
			var cooldown: Duration? = null
			while (i < lore.size && lore[i].bareText.isNotEmpty()) {
				val line = lore[i++]
				val parts = line.siblings.map { it.ownLiteralText.orEmpty() }
				var labelled = false
				for ((k, text) in parts.withIndex()) {
					val value = parts.getOrNull(k + 1) ?: continue
					when (text) {
						MANA_LABEL -> { mana = parseShortNumberOrNull(value)?.toInt(); labelled = true }
						COOLDOWN_LABEL -> { cooldown = parseTimePattern(value); labelled = true }
					}
				}
				if (!labelled) description += line
			}
			found += ItemAbility(head.name, head.activation, head.scroll, description, mana, cooldown)
		}
		return found
	}

	fun abilitiesIn(itemStack: ItemView): List<ItemAbility> = abilitiesIn(itemStack.loreAccordingToNbt)
}
