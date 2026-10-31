// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType

object RestArgumentType
	: ArgumentType<String> {
	override fun parse(
		reader: StringReader,
	): String {
		val from = reader.cursor
		reader.cursor = reader.totalLength
		return reader.string.substring(from)
	}
}
