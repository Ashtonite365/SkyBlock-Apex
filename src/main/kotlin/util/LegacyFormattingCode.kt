package dev.ashtonite365.apex.util

import net.minecraft.ChatFormatting

private const val COLOUR_COUNT = 16

enum class LegacyFormattingCode(val char: Char) {
	BLACK('0'), DARK_BLUE('1'), DARK_GREEN('2'), DARK_AQUA('3'),
	DARK_RED('4'), DARK_PURPLE('5'), GOLD('6'), GRAY('7'),
	DARK_GRAY('8'), BLUE('9'), GREEN('a'), AQUA('b'),
	RED('c'), LIGHT_PURPLE('d'), YELLOW('e'), WHITE('f'),
	OBFUSCATED('k'), BOLD('l'), STRIKETHROUGH('m'), UNDERLINE('n'), ITALIC('o'), RESET('r');

	val label: String get() = name

	val index: Int = if (ordinal < COLOUR_COUNT) ordinal else -1

	val modern: ChatFormatting = requireNotNull(ChatFormatting.getByCode(char))

	val formattingCode: String = "§$char"

	companion object {
		val byCode: Map<Char, LegacyFormattingCode> = entries.associateBy(LegacyFormattingCode::char)
	}
}
