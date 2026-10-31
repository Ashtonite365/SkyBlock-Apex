package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.Apex

object ClipboardUtils {
	fun setTextContent(string: String) {

		val payload = if (string.isEmpty()) " " else string
		runCatching { Client.keyboardHandler.clipboard = payload }
			.onFailure { Apex.logger.error("Could not write clipboard", it) }
	}

	fun getTextContents(): String =
		runCatching { Client.keyboardHandler.clipboard.orEmpty() }
			.onFailure { Apex.logger.error("Could not read clipboard", it) }
			.getOrDefault("")
}
