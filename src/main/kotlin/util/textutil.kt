package dev.ashtonite365.apex.util

import java.util.Optional
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.util.FormattedCharSequence

val styleCodeLetters: Set<Char> = "kmolnrKMOLNR".toSet()

fun CharSequence.stripSectionCodes(keepStyleCodes: Boolean = false): String {
	val out = StringBuilder(length)
	var at = 0
	while (at < length) {
		val ch = this[at]
		if (ch != '§') {
			out.append(ch)
			at++
			continue
		}
		val letter = if (at + 1 < length) this[at + 1] else null
		if (letter != null && keepStyleCodes && letter in styleCodeLetters) out.append(ch).append(letter)
		at += 2
	}
	return out.toString()
}

private class RunCollector {
	private val runs = ArrayList<Pair<Style, StringBuilder>>()

	fun add(style: Style, text: CharSequence) {
		if (text.isEmpty()) return
		val last = runs.lastOrNull()
		if (last != null && last.first == style) last.second.append(text)
		else runs += style to StringBuilder(text)
	}

	fun build(): MutableComponent {
		val root = Component.empty().setStyle(Style.EMPTY.withItalic(false))
		for ((style, text) in runs) root.append(Component.literal(text.toString()).setStyle(style))
		return root
	}
}

fun FormattedCharSequence.rebuildComponent(): MutableComponent {
	val collector = RunCollector()
	accept { _, style, codePoint ->
		collector.add(style, codePoint.toChar().toString())
		true
	}
	return collector.build()
}

fun FormattedText.rebuildComponent(): MutableComponent {
	val collector = RunCollector()
	visit({ style, text ->
		collector.add(style, text)
		Optional.empty<Unit>()
	}, Style.EMPTY)
	return collector.build()
}

val Component.bareText: String get() = string.stripSectionCodes()

val Component.ownLiteralText: String? get() = (contents as? PlainTextContents)?.text()

fun TextColor.toChatFormatting(): ChatFormatting? =
	ChatFormatting.entries.firstOrNull { TextColor.fromLegacyFormat(it)?.value == value }

private fun legacyPrefix(style: Style): String = buildString {
	val named = style.color?.toChatFormatting()
	append(named?.toString() ?: "§r")
	if (style.isBold) append("§l")
	if (style.isItalic) append("§o")
	if (style.isUnderlined) append("§n")
	if (style.isObfuscated) append("§k")
	if (style.isStrikethrough) append("§m")
}

fun Component.getLegacyFormatString(trimmed: Boolean = false): String {
	val out = StringBuilder()
	if (!trimmed && ownLiteralText == "" && siblings.isNotEmpty()) out.append(legacyPrefix(style)).append("§r")
	var previous = "§r"
	visit({ style, text ->
		if (text.isNotEmpty()) {
			val prefix = legacyPrefix(style)
			if (!trimmed) out.append(prefix).append(text).append("§r")
			else {
				if (prefix != previous) out.append(prefix)
				previous = prefix
				out.append(text)
			}
		}
		Optional.empty<Unit>()
	}, Style.EMPTY)
	return out.toString()
}

fun Component.iterator(): Sequence<Component> = sequence {
	val stack = ArrayDeque<Component>()
	stack.addLast(this@iterator)
	while (stack.isNotEmpty()) {
		val next = stack.removeLast()
		yield(next)
		for (i in next.siblings.indices.reversed()) stack.addLast(next.siblings[i])
	}
}

fun Component.allSiblings(): List<Component> = iterator().toList()

fun MutableComponent.withColor(formatting: ChatFormatting): MutableComponent =
	setStyle(style.withColor(formatting).withItalic(false).withBold(false))

fun MutableComponent.blue(): MutableComponent = withColor(ChatFormatting.BLUE)
fun MutableComponent.aqua(): MutableComponent = withColor(ChatFormatting.AQUA)
fun MutableComponent.lime(): MutableComponent = withColor(ChatFormatting.GREEN)
fun MutableComponent.darkGreen(): MutableComponent = withColor(ChatFormatting.DARK_GREEN)
fun MutableComponent.purple(): MutableComponent = withColor(ChatFormatting.DARK_PURPLE)
fun MutableComponent.pink(): MutableComponent = withColor(ChatFormatting.LIGHT_PURPLE)
fun MutableComponent.yellow(): MutableComponent = withColor(ChatFormatting.YELLOW)
fun MutableComponent.gold(): MutableComponent = withColor(ChatFormatting.GOLD)
fun MutableComponent.grey(): MutableComponent = withColor(ChatFormatting.GRAY)
fun MutableComponent.darkGrey(): MutableComponent = withColor(ChatFormatting.DARK_GRAY)
fun MutableComponent.red(): MutableComponent = withColor(ChatFormatting.RED)
fun MutableComponent.white(): MutableComponent = withColor(ChatFormatting.WHITE)

fun MutableComponent.bold(): MutableComponent = setStyle(style.withBold(true))

fun MutableComponent.hover(tooltip: Component): MutableComponent =
	setStyle(style.withHoverEvent(HoverEvent.ShowText(tooltip)))

fun MutableComponent.colouredBy(
	on: Boolean,
	whenOn: ChatFormatting = ChatFormatting.GREEN,
	whenOff: ChatFormatting = ChatFormatting.DARK_RED,
): MutableComponent = withColor(if (on) whenOn else whenOff)

fun MutableComponent.runsOnClick(command: String): MutableComponent {
	require(command.firstOrNull() == '/')
	val clicked = style.withClickEvent(ClickEvent.RunCommand(command))
	return setStyle(clicked)
}

fun MutableComponent.prependChild(child: Component): MutableComponent {
	siblings.add(0, child)
	return this
}

fun Component.mapComponentTree(transform: (Component) -> Component): Component {
	val translation = contents as? TranslatableContents
	val alone: MutableComponent = if (translation != null) {
		val args = translation.args.map { arg ->
			(arg as? Component ?: Component.literal(arg.toString())).mapComponentTree(transform)
		}.toTypedArray<Any>()
		Component.translatableWithFallback(translation.key, translation.fallback, *args).setStyle(style)
	} else {
		MutableComponent.create(contents).setStyle(style)
	}
	val result = transform(alone)
	val children = siblings.map { it.mapComponentTree(transform) }
	if (result is MutableComponent) result.siblings.addAll(children)
	return if (translation != null) alone else result
}

fun trResolved(key: String, vararg parts: Any): MutableComponent = Component.translatableEscape(key, *parts)

@Suppress("UNUSED_PARAMETER")
fun tr(key: String, default: String): MutableComponent = throw IllegalStateException("Compiler plugin did not run.")

fun titleCase(text: String): String {
	val out = StringBuilder(text.length)
	var wordStart = true
	for (ch in text) {
		when {
			ch == '_' || ch == ' ' -> { out.append(' '); wordStart = true }
			wordStart -> { out.append(ch.titlecaseChar()); wordStart = false }
			else -> out.append(ch.lowercaseChar())
		}
	}
	return out.toString()
}
