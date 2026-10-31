// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.LiteralCommandNode

class AnyCaseLiteral<S> private constructor(from: Builder<S>) : LiteralCommandNode<S>(
	from.literal.lowercase(), from.command, from.requirement, from.redirect, from.redirectModifier, from.isFork,
) {

	class Builder<S>(word: String) : LiteralArgumentBuilder<S>(word) {
		override fun build(): LiteralCommandNode<S> {
			val node = AnyCaseLiteral(this)
			for (child in arguments) node.addChild(child)
			return node
		}
	}

	override fun createBuilder(): LiteralArgumentBuilder<S> {
		val again = Builder<S>(literal)
		again.requires(requirement)
		again.forward(redirect, redirectModifier, isFork)
		val action = command
		if (action != null) again.executes(action)
		return again
	}

	private fun startsHere(reader: StringReader): Boolean {
		val text = reader.string
		val start = reader.cursor
		val end = start + literal.length
		if (end > text.length || !text.regionMatches(start, literal, 0, literal.length, ignoreCase = true)) return false
		return end == text.length || text[end] == ' '
	}

	override fun parse(reader: StringReader, contextBuilder: CommandContextBuilder<S>) {
		if (!startsHere(reader)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().createWithContext(reader, literal)
		val span = StringRange.between(reader.cursor, reader.cursor + literal.length)
		reader.cursor = span.end
		contextBuilder.withNode(this, span)
	}

	override fun toString(): String = "<iliteral $literal>"
}
