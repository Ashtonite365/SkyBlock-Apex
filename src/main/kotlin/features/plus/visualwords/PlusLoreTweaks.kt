// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.visualwords

import java.time.LocalDate
import java.util.Optional
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object PlusLoreTweaks {
	@Config
	object TConfig : ManagedConfig("plus-lore", Category.MISC) {
		val romanNumerals by toggle("roman-numerals") { false }
		val hexColours by toggle("hex-colours") { false }
		val statIcons by toggle("stat-icons") { false }
		val wizardPortalLore by toggle("wizard-portal-lore") { false }
		val statIconsStripUnits by toggle("stat-icons-strip-units") { false }
		val chimeraFix by toggle("chimera-fix") { false }
	}

	val statIcons: Map<String, String> = linkedMapOf(
		"Damage" to "§c\uE050",
		"Health" to "§c\uE010",
		"Defense" to "§a\uE008",
		"True Defense" to "§f\uE027",
		"Strength" to "§c\uE00D",
		"Crit Chance" to "§1\uE02C",
		"Crit Damage" to "§1\uE007",
		"Attack Speed" to "§e\uE001",
		"Ferocity" to "§c\uE00B",
		"Swing Range" to "§e\uE024",
		"Intelligence" to "§b\uE003",
		"Ability Damage" to "§c\uE002",
		"Health Regen" to "§c\uE011",
		"Vitality" to "§4\uE028",
		"Mending" to "§a\uE014",
		"Breaking Power" to "§2\uE005",
		"Mining Speed" to "§6\uE015",
		"Mining Spread" to "§e\uE016",
		"Gemstone Spread" to "§e\uE00F",
		"Pristine" to "§5\uE01C",
		"Mining Fortune" to "§6\uE053",
		"Ore Fortune" to "§6\uE053",
		"Block Fortune" to "§6\uE053",
		"Dwarven Metal Fortune" to "§6\uE053",
		"Gemstone Fortune" to "§6\uE053",
		"Bonus Pest Chance" to "§2\uE019",
		"Overbloom" to "§e\uE02B",
		"Farming Fortune" to "§6\uE051",
		"Wheat Fortune" to "§6\uE051",
		"Carrot Fortune" to "§6\uE051",
		"Potato Fortune" to "§6\uE051",
		"Pumpkin Fortune" to "§6\uE051",
		"Sugar Cane Fortune" to "§6\uE051",
		"Melon Slice Fortune" to "§6\uE051",
		"Cactus Fortune" to "§6\uE051",
		"Cocoa Beans Fortune" to "§6\uE051",
		"Mushroom Fortune" to "§6\uE051",
		"Nether Wart Fortune" to "§6\uE051",
		"Sunflower Fortune" to "§6\uE051",
		"Moonflower Fortune" to "§6\uE051",
		"Wild Rose Fortune" to "§6\uE051",
		"Sweep" to "§2\uE023",
		"Foraging Fortune" to "§6\uE054",
		"Fig Fortune" to "§6\uE054",
		"Mangrove Fortune" to "§6\uE054",
		"Helix Fortune" to "§6\uE054",
		"Timber" to "§4\uE02E",
		"Fishing Speed" to "§b\uE00C",
		"Sea Creature Chance" to "§3\uE021",
		"Double Hook Chance" to "§9\uE009",
		"Trophy Chance" to "§6\uE02A",
		"Treasure Chance" to "§6\uE025",
		"Speed" to "§f\uE022",
		"Magic Find" to "§b\uE01A",
		"Pet Luck" to "§d\uE013",
		"Heat Resistance" to "§c\uE012",
		"Cold Resistance" to "§b\uE006",
		"Respiration" to "§3\uE01D",
		"Pressure Resistance" to "§9\uE01B",
		"Fear" to "§5\uE00A",
		"Tracking" to "§d\uE077",
		"Pull" to "§b\uE02D",
		"Hunting Fortune" to "§d\uE05B",
		"Rift Time" to "§a\uE020",
		"Rift Damage" to "§5\uE01E",
		"Mana Regen" to "§b\uE004",
		"Hearts" to "§c\uE01F",
	)

	private val STAT_BONUS = Regex("(?<stat>[a-zA-Z ]+): (?<bonus>[-+]?[\\d.,]+[%s]?)(?:\\s|$)")

	fun statIconReplacement(line: String, stripUnits: Boolean = false): Pair<String, String>? {
		val hit = STAT_BONUS.find(line) ?: return null
		val icon = statIcons[hit.groups["stat"]!!.value.trim()] ?: return null
		val bonus = hit.groups["bonus"]!!.value
		val shown = if (!stripUnits) bonus else bonus.removeSuffix("%").removeSuffix("s")
		return bonus to "$shown$icon"
	}

	private const val CHIMERA_OLD_TEXT = "Copies your active pet's stats."
	private val CHIMERA_PERCENT = Regex(".*Copies (\\d+)% of your active.*")
	private val CHIMERA_NERF = mapOf(100 to 75, 80 to 60, 60 to 45, 40 to 25, 20 to 10)

	fun newChimeraValue(old: Int): Int = CHIMERA_NERF[old] ?: old
	fun isAprilFools(date: LocalDate = LocalDate.now()) = date.monthValue == 4 && date.dayOfMonth == 1

	fun chimeraReplacement(line: String): Pair<String, String>? {
		if (CHIMERA_OLD_TEXT in line) return CHIMERA_OLD_TEXT to "Copies 75% of your active pet's stats."
		val old = CHIMERA_PERCENT.matchEntire(line)?.groupValues?.get(1)?.toInt() ?: return null
		return "$old%" to "${newChimeraValue(old)}%"
	}

	private val NUMERAL_VALUE = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
	private val NUMERAL_STEPS = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
		50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
	private val KEYWORD_NUMERAL =
		Regex("\\b(Level|Tier|Chapter|Skill|Milestone|Floor|Collection|Heart of the \\w+) ([IVXLCDM]+)\\b")

	fun romanToInt(roman: String): Int? {
		val values = roman.map { NUMERAL_VALUE[it] ?: return null }
		if (values.isEmpty()) return null
		val total = values.indices.sumOf { i -> if (i + 1 < values.size && values[i] < values[i + 1]) -values[i] else values[i] }
		return total.takeIf { intToRoman(it) == roman }
	}

	fun intToRoman(n: Int): String {
		val sb = StringBuilder()
		var left = n
		for ((value, symbol) in NUMERAL_STEPS) {
			val times = if (left >= value) left / value else 0
			repeat(times) { sb.append(symbol) }
			left -= times * value
		}
		return sb.toString()
	}

	fun replaceRoman(text: String): String = KEYWORD_NUMERAL.replace(text) { m ->
		romanToInt(m.groupValues[2])?.let { "${m.groupValues[1]} $it" } ?: m.value
	}

	private val HEX_CODE = Regex("#[0-9a-fA-F]{6}\\b")

	fun findHexColours(text: String): List<Int> = HEX_CODE.findAll(text).map { it.value.drop(1).toInt(16) }.toList()

	private fun restyle(line: Component): Component {
		val runs = ArrayList<Pair<String, Style>>()
		var edited = false
		line.visit(FormattedText.StyledContentConsumer<Unit> { style, text ->
			val newText = if (TConfig.romanNumerals) replaceRoman(text) else text
			val tint = if (TConfig.hexColours) findHexColours(newText).firstOrNull() else null
			val newStyle = if (tint != null) style.withColor(tint) else style
			if (newText != text || newStyle != style) edited = true
			runs += newText to newStyle
			Optional.empty()
		}, Style.EMPTY)
		if (!edited) return line
		return Component.empty().also { out -> runs.forEach { (t, s) -> out.append(Component.literal(t).withStyle(s)) } }
	}

	private inline fun replaceInLines(lines: MutableList<Component>, find: (String) -> Pair<String, String>?) {
		for (i in lines.indices) {
			val (old, new) = find(lines[i].string) ?: continue
			lines[i] = PlusText.replaceFirst(lines[i], old, new)
		}
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val lines = event.lines
		if (TConfig.romanNumerals || TConfig.hexColours) lines.replaceAll(::restyle)
		if (TConfig.statIcons) {
			val strip = TConfig.statIconsStripUnits
			replaceInLines(lines) { statIconReplacement(it, strip) }
		}
		if (TConfig.chimeraFix && isAprilFools()) replaceInLines(lines, ::chimeraReplacement)
		if (TConfig.wizardPortalLore) addMementoRecipient(event)
	}

	private fun addMementoRecipient(event: ItemTooltipEvent) {
		val item = event.stack.view()
		if (item.repoItemId?.skyblockId != "WIZARD_PORTAL_MEMENTO") return
		if (event.lines.any { it.string.startsWith("Earned by:") }) return
		val recipient = item.customDataTag.getString("recipient_name").orElse(null) ?: return
		event.lines.add(minOf(5, event.lines.size), Component.literal("§7Earned by: $recipient"))
	}
}
