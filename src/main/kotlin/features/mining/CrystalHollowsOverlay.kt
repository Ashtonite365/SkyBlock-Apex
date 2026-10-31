// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.render.drawHudLines

object CrystalHollowsOverlay {
	@Config
	object TConfig : ManagedConfig("crystal-hollows-overlay", Category.MINING) {
		val enabled by toggle("enabled") { true }
		val showCrystals by toggle("show-crystals") { true }
		val showDivan by toggle("show-divan") { true }
		val showAutomaton by toggle("show-automaton") { true }
		val hud by position("hud", 140, 90) { Vector2i(5, 180) }
	}

	@Serializable
	data class Data(
		val crystals: MutableMap<String, Int> = mutableMapOf(),
		val divanParts: MutableMap<String, Boolean> = mutableMapOf(),
		val automatonParts: MutableMap<String, Boolean> = mutableMapOf(),
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "crystal-hollows-progress", ::Data)

	val crystalNames = listOf("Jade", "Amber", "Amethyst", "Sapphire", "Topaz")
	val divanPartNames = listOf("Scavenged Lapis Sword", "Scavenged Golden Hammer", "Scavenged Diamond Axe", "Scavenged Emerald Hammer")
	val automatonPartNames = listOf("Electron Transmitter", "FTX 3070", "Robotron Reflector", "Superlite Motor", "Control Switch", "Synthetic Heart")

	private const val FOUND = 1
	private const val PLACED = 2

	private fun isLineBreak(c: Char) = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'
	private fun isLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
	private fun isWordChar(c: Char) = isLetter(c) || c in '0'..'9' || c == '_'
	private fun isPartChar(c: Char, digits: Boolean) = isLetter(c) || c == ' ' || (digits && c in '0'..'9')

	private fun between(text: String, prefix: String, suffix: String, digits: Boolean = false): String? {
		if (text.length <= prefix.length + suffix.length || !text.startsWith(prefix) || !text.endsWith(suffix)) return null
		return text.substring(prefix.length, text.length - suffix.length).takeIf { part -> part.all { isPartChar(it, digits) } }
	}

	private fun keeperSays(text: String): String? {
		val head = "[NPC] Keeper of "
		if (!text.startsWith(head)) return null
		var i = head.length
		while (i < text.length && isWordChar(text[i])) i++
		if (i == head.length || !text.startsWith(": ", i)) return null
		return text.substring(i + 2)
	}

	private fun crystalAfter(text: String, lead: String, wholeLead: Boolean, tail: String = " Crystal!"): String? {
		if (!text.endsWith(tail)) return null
		val end = text.length - tail.length
		var start = end
		while (start > 0 && isLetter(text[start - 1])) start--
		if (start == end) return null
		val before = text.substring(0, start)
		val ok = if (wholeLead) before == lead else before.endsWith(lead) && before.none(::isLineBreak)
		return text.substring(start, end).takeIf { ok }
	}

	private fun lootCrystal(text: String): String? {
		if (!text.startsWith("  ") || !text.endsWith(" Crystal")) return null
		val name = text.trimStart(' ').removeSuffix(" Crystal")
		return name.takeIf { it.isNotEmpty() && it.all(::isLetter) }
	}

	private fun robotGotPart(text: String): String? {
		val head = "[NPC] Professor Robot: Thanks for bringing me the "
		if (!text.startsWith(head)) return null
		val partEnd = text.indexOf('!', head.length)
		if (partEnd <= head.length) return null
		val part = text.substring(head.length, partEnd).takeIf { p -> p.all { isPartChar(it, digits = true) } } ?: return null
		val rest = text.substring(partEnd)
		val count = rest.removePrefix("! Bring me ").takeIf { it.length < rest.length }?.substringBefore(" more component") ?: return null
		if (count != "one" && (count.isEmpty() || count.any { it !in '0'..'9' })) return null
		val ending = rest.substring("! Bring me ".length + count.length)
		return part.takeIf { ending == " more components to fix the giant!" || ending == " more component to fix the giant!" }
	}

	fun applyChat(data: Data, text: String): Boolean {
		fun crystal(name: String?, state: Int): Boolean {
			if (name == null || name !in crystalNames) return false
			data.crystals[name] = state
			return true
		}
		if (crystal(crystalAfter(text, "✦ You placed the ", wholeLead = true), PLACED)) return true
		if (crystal(crystalAfter(text, ": You have already placed the ", wholeLead = false), PLACED)) return true
		if (crystal(crystalAfter(text, "✦ You reclaimed the ", wholeLead = true), FOUND)) return true
		if (crystal(crystalAfter(text, ": You haven't placed the ", wholeLead = false, tail = " Crystal yet!"), FOUND)) return true
		val keeper = keeperSays(text)
		if (keeper == "You found all of the items! Behold... the Jade Crystal!") {
			data.divanParts.clear()
			return true
		}
		if (keeper != null) {
			val returned = between(keeper, "Excellent! You have returned the ", " to its rightful place!")
				?: between(keeper, "You have already restored this Dwarf's ", "!")
			if (returned != null) {
				data.divanParts[returned] = true
				return true
			}
			between(keeper, "Talk to me when you have found a ", "!")?.let {
				data.divanParts[it] = false
				return true
			}
		}
		robotGotPart(text)?.let {
			data.automatonParts[it] = true
			return true
		}
		between(text, "[NPC] Professor Robot: That's not the final component! Bring me a ",
			" to gain access to Automaton Prime's storage container!", digits = true)?.let {
			data.automatonParts[it] = false
			return true
		}
		return crystal(lootCrystal(text), FOUND)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.CRYSTAL_HOLLOWS) return
		if (applyChat(DConfig.data, event.bareText)) DConfig.markDirty()
	}

	private fun crystalState(state: Int?) = when (state) {
		PLACED -> "§aPlaced"
		FOUND -> "§eFound"
		else -> "§cMissing"
	}

	fun hudLines(data: Data): List<String> = buildList {
		if (TConfig.showCrystals) {
			add("§dCrystals:")
			crystalNames.forEach { add(" §7$it: " + crystalState(data.crystals[it])) }
		}
		if (TConfig.showDivan) {
			add("§3Mines of Divan:")
			divanPartNames.forEach { add(" §7${it.removePrefix("Scavenged ")}: " + if (data.divanParts[it] == true) "§aDone" else "§cMissing") }
		}
		if (TConfig.showAutomaton) {
			add("§3Precursor City:")
			automatonPartNames.forEach { add(" §7$it: " + if (data.automatonParts[it] == true) "§aDelivered" else "§cMissing") }
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || SkyBlockSession.skyblockIsland != SkyBlockIsland.CRYSTAL_HOLLOWS) return
		event.drawHudLines(TConfig.hud, hudLines(DConfig.data).map(Component::literal))
	}
}
