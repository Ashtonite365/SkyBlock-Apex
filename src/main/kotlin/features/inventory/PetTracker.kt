package dev.ashtonite365.apex.features.inventory

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.ApexFormatters.percentText
import dev.ashtonite365.apex.util.ApexFormatters.compactNumber
import dev.ashtonite365.apex.util.skyblock.Rarity
import dev.ashtonite365.apex.util.withColor

class PetTracker {
	private sealed interface Equipped {
		data object NotKnown : Equipped
		data object Nothing : Equipped
		data class Menu(val uuid: String) : Equipped
	}

	private val seen = HashMap<String, PetRecord>()
	private var equipped: Equipped = Equipped.NotKnown

	private var tabFallback: PetRecord? = null

	private var chatFallback: PetRecord? = null

	private fun equippedFromMenu(): PetRecord? = (equipped as? Equipped.Menu)?.let { seen[it.uuid] }

	fun current(): PetRecord? = equippedFromMenu() ?: tabFallback ?: chatFallback

	fun forget() {
		seen.clear()
		equipped = Equipped.NotKnown
		tabFallback = null
		chatFallback = null
	}

	private fun remember(uuid: String, read: () -> PetRecord?) {
		if (seen[uuid]?.fromMenu == true) return
		seen[uuid] = read() ?: return
	}

	fun menuItemShown(uuid: String?, active: Boolean, read: () -> PetRecord?) {
		if (uuid != null) remember(uuid, read)
		if (active && equipped == Equipped.NotKnown) equipped = Equipped.Menu(uuid.toString())
	}

	fun menuItemClicked(uuid: String?, active: Boolean, button: Int, read: () -> PetRecord?) {
		when {
			button != 0 && button != 1 -> return
			active -> equipped = Equipped.Nothing
			button == 0 -> {
				if (uuid != null && uuid !in seen) remember(uuid, read)
				equipped = Equipped.Menu(uuid.toString())
			}
		}
	}

	private fun firstSeen(test: (PetRecord) -> Boolean): Map.Entry<String, PetRecord>? = seen.entries.firstOrNull { test(it.value) }

	fun chatLine(text: String) {
		AUTOPET.matchEntire(text)?.let { m ->
			val (lvlText, code, name) = m.destructured
			val level = lvlText.toInt()
			val rarity = PetReadings.rarityOfColourCode(code)
			val known = firstSeen { it.name == name && it.rarity == rarity && it.level <= level }
			if (known != null) {
				equipped = Equipped.Menu(known.key)
			} else {
				chatFallback = PetReadings.fromChat(name, code, level)
				equipped = Equipped.NotKnown
			}
			tabFallback = null
			return
		}
		HOLDING.matchEntire(text)?.let { m ->
			val item = m.groupValues[1]
			listOfNotNull(equippedFromMenu(), tabFallback, chatFallback).forEach { it.heldItem = item }
		}
		LEVEL_UP.matchEntire(text)?.let { m ->
			val (code, name, lvl) = m.destructured
			val reading = PetReadings.fromChat(name, code, lvl.toInt()) ?: return
			firstSeen { it.isSamePetAs(reading) && it.level <= reading.level }?.value?.absorb(reading)
			tabFallback?.takeIf { it.isSamePetAs(reading) }?.absorb(reading)
			chatFallback?.takeIf { it.isSamePetAs(reading) }?.absorb(reading)
		}
	}

	sealed interface HudPick {
		data object Hidden : HudPick
		data object NothingKnown : HudPick
		class Show(val pet: PetRecord) : HudPick
	}

	fun pickForHud(tab: PetRecord?): HudPick {
		if (equipped == Equipped.Nothing) return HudPick.Hidden
		var shown = equippedFromMenu()
		if (shown == null) {
			if (tab == null && tabFallback == null && chatFallback == null) return HudPick.NothingKnown
			val kept = tabFallback
			if (kept == null) tabFallback = tab else if (tab != null) kept.absorb(tab)
			shown = tabFallback ?: tab ?: chatFallback ?: return HudPick.NothingKnown
		}
		if (tab != null) shown.advanceFromTab(tab)
		return HudPick.Show(shown)
	}

	companion object {
		private val AUTOPET = Regex("""§cAutopet §eequipped your §7\[Lvl (\d{1,3})\] §([fa956d])([\w\s]+)§e! §aVIEW RULE""")
		private val HOLDING = Regex("""§aYour pet is now holding (§[fa956d][\w\s]+)§a.""")
		private val LEVEL_UP = Regex("""§aYour §([fa956d])([\w\s]+) §aleveled up to level §9(\d+)§a!""")
	}
}

object PetHudText {
	val nothingKnown: List<Component>
		get() = listOf(Component.literal("${ChatFormatting.WHITE}Unknown Pet"), Component.literal("Open Pets Menu To Fix"))

	private fun progress(label: String, have: Double, need: Double, coloured: Boolean): String {
		val pct = percentText(have / need)
		return if (coloured) "§e$label: ${compactNumber(have)}§6/§e${compactNumber(need)} §6($pct)"
		else "$label: ${compactNumber(have)}/${compactNumber(need)} ($pct)"
	}

	fun lines(pet: PetRecord, coloured: Boolean): List<Component> {
		val title = Component.literal("[Lvl ${pet.level}] ")
			.append(Component.literal(pet.name).withColor(Rarity.colourMap[pet.rarity] ?: ChatFormatting.WHITE))
		val placeholder = pet.heldItem == PetRecord.NO_ITEM || pet.heldItem == PetRecord.UNKNOWN_ITEM
		val item = if (coloured || placeholder) pet.heldItem else pet.heldItem.drop(2)
		val detail = if (pet.level == pet.maxLevel) {
			if (coloured) listOf("§b§lMAX LEVEL", "§6+§e${compactNumber(pet.xpOverflow)} XP")
			else listOf("MAX LEVEL", "+${compactNumber(pet.xpOverflow)} XP")
		} else listOf(
			progress("Required L${pet.level + 1}", pet.xpInLevel, pet.xpToNext, coloured),
			progress("Required L100", pet.xpTotal, pet.xpForMax, coloured),
		)
		return listOf(title, Component.literal(item)) + detail.map(Component::literal)
	}
}
