package dev.ashtonite365.apex.repo

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import dev.ashtonite365.apex.repo.SBItemStack.Companion.BuffKind
import dev.ashtonite365.apex.repo.SBItemStack.Companion.StatLine
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.LegacyFormattingCode
import dev.ashtonite365.apex.util.mc.ItemEditor
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.useMatch

internal object StatLore {

	private val labelShape = "(?<statName>.*):".toPattern()
	private val greyLabel: TextColor = TextColor.fromLegacyFormat(ChatFormatting.GRAY)!!

	val looks: Map<String, SBItemStack.Companion.StatFormatting> = buildMap {
		fun look(suffix: String, colour: ChatFormatting, stars: Boolean, vararg names: String) {
			for (n in names) put(n, SBItemStack.Companion.StatFormatting(suffix, colour, stars))
		}
		look("", ChatFormatting.RED, true, "Damage", "Strength")
		look("%", ChatFormatting.RED, true, "Crit Chance", "Crit Damage", "Bonus Attack Speed", "Ability Damage", "Sea Creature Chance")
		look("%", ChatFormatting.GREEN, true, "Double Hook Chance", "Trophy Fish Chance", "Bonus Pest Chance", "Mana Regen")
		look("s", ChatFormatting.GREEN, false, "Shot Cooldown")
	}

	fun lookOf(name: String) = looks[name] ?: SBItemStack.Companion.StatFormatting()

	fun humanName(id: String): String = id.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

	fun numberIn(value: Component?): Double? {
		val t = value?.string?.trim() ?: return null
		return t.removeSuffix("%").removeSuffix("s").removePrefix("+").toDoubleOrNull()
	}

	fun read(line: Component): StatLine? {
		val first = line.siblings.firstOrNull() ?: return null
		if (line.siblings.size < 2) return null
		if (first.style.color != greyLabel) return null
		val statName = labelShape.useMatch(first.string.trimEnd()) { group("statName") } ?: return null
		return StatLine(statName, line.siblings.getOrNull(1), rest = line.siblings.drop(2))
	}

	fun block(item: ItemView): List<StatLine> {
		val parsed = item.loreAccordingToNbt.map(::read)
		return parsed.takeWhile { it != null }.filterNotNull()
	}

	fun shortName(name: String, width: Int): String {
		if (name.length <= width) return name
		val words = name.split(' ')
		val each = (width / words.size).coerceAtLeast(1)

		return words.joinToString(" ") { it.substring(0, each) }
	}

	fun signed(n: Double) = ApexFormatters.withThousands(n, 1, includeSign = true)

	private fun plain(style: Style) = style.withBold(false).withItalic(false)

	fun added(line: StatLine, amount: Double, kind: BuffKind): StatLine {
		val total = (line.valueNum ?: 0.0) + amount
		val shown = if (kind.isHidden) line.rest else line.rest + Component.literal(
			kind.prefix + signed(amount) + line.statFormatting.postFix + kind.postFix + " "
		).withStyle(plain(Style.EMPTY.withColor(kind.color)))
		return StatLine(line.statName, null, total, shown)
	}

	fun valueText(line: StatLine): MutableComponent {
		val look = line.statFormatting
		return Component.literal(signed(line.valueNum ?: 0.0) + look.postFix + " ").withStyle(look.color)
	}

	fun render(line: StatLine, width: Int): Component {
		val root = Component.literal("").withStyle(Style.EMPTY.withItalic(false))
		root.append(Component.literal(shortName(line.statName, width) + ": ").withStyle(plain(Style.EMPTY.withColor(ChatFormatting.GRAY))))
		root.append(line.value ?: valueText(line))
		line.rest.forEach(root::append)
		return root
	}

	fun applyBuffs(item: ItemEditor, buffs: Map<String, Double>, kind: BuffKind) {
		val left = LinkedHashMap<String, Double>()
		buffs.forEach { (id, n) -> left[humanName(id)] = n }
		val lore = item.loreAccordingToNbt.toMutableList()
		var lastStat = -1
		for (i in lore.indices) {
			val stat = read(lore[i]) ?: continue
			lastStat = i
			left.remove(stat.statName)?.let { lore[i] = render(added(stat, it, kind), Int.MAX_VALUE) }
		}
		var at = lastStat + 1
		if (lastStat < 0 && left.isNotEmpty()) lore.add(0, Component.literal("")).also { at = 0 }
		for ((name, n) in left) lore.add(at, render(added(StatLine(name, null), n, kind), Int.MAX_VALUE))
		item.loreAccordingToNbt = lore
	}

	private val starColours = listOf(LegacyFormattingCode.GOLD, LegacyFormattingCode.LIGHT_PURPLE, LegacyFormattingCode.AQUA)
	private const val PER_COLOUR = 5

	fun starSuffix(count: Int): Component {
		if (count <= 0) return Component.empty()
		if (count > PER_COLOUR * starColours.size)
			return Component.literal(" $count✪").withStyle(plain(Style.EMPTY.withColor(ChatFormatting.RED)))
		val band = (count - 1) / PER_COLOUR
		val lit = count - band * PER_COLOUR
		val head = Component.literal(" " + "✪".repeat(lit)).withStyle(plain(Style.EMPTY.withColor(starColours[band].modern)))
		if (band > 0) head.append(Component.literal("✪".repeat(PER_COLOUR - lit))
			.withStyle(plain(Style.EMPTY.withColor(starColours[band - 1].modern))))
		return head
	}
}
