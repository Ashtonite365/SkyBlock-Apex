// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.chat

class AnnouncementSwallower(private val opener: String, private val bodyLimit: Int) {
	private var remaining = 0

	fun swallow(line: String): Boolean {
		if (line == opener) {
			remaining = bodyLimit
			return true
		}
		if (remaining <= 0) return false
		remaining = if (line.isBlank()) 0 else remaining - 1
		return true
	}

	companion object {
		fun watchdog() = AnnouncementSwallower("[WATCHDOG ANNOUNCEMENT]", 11)
	}
}

class ArachneQuieter {
	private var insideResult = false

	fun quieten(raw: String): Boolean {
		val line = raw.trim()
		if (isNoise(line)) return true
		if (isFrame(line)) {
			insideResult = !insideResult
			return true
		}
		if (line == "ARACHNE DOWN!") insideResult = true
		return insideResult
	}

	private fun isFrame(line: String) = line.length > 20 && line.all { it == FRAME }

	private fun isNoise(line: String): Boolean =
		line in BOSS_LINES || isVenomHit(line) || placedAfterMarker(line, CALLING, mustEnd = false) || placedAfterMarker(line, CRYSTAL, mustEnd = true)

	private fun isVenomHit(line: String): Boolean {
		val who = VENOM_SOURCES.firstOrNull { line.startsWith(it) } ?: return false
		if (!line.endsWith(VENOM_TAIL)) return false
		if (line.length <= who.length + VENOM_TAIL.length) return false
		return line.substring(who.length, line.length - VENOM_TAIL.length).all { it in '0'..'9' || it == '.' || it == ',' }
	}

	private fun placedAfterMarker(line: String, text: String, mustEnd: Boolean): Boolean {
		if (!line.startsWith(MARKER)) return false
		return if (mustEnd) line.length >= MARKER.length + text.length && line.endsWith(text)
		else line.indexOf(text, MARKER.length) >= 0
	}

	private companion object {
		const val FRAME = '▬'
		const val MARKER = "☄ "
		const val CALLING = " placed an Arachne's Calling! "
		const val CRYSTAL = " placed an Arachne Crystal! Something is awakening!"
		const val VENOM_TAIL = " damage and infecting you with venom."
		val VENOM_SOURCES = listOf("Arachne", "Arachne's Keeper", "Arachne's Brood").map { "$it used Venom Shot on you hitting you for " }
		val BOSS_LINES = setOf("[BOSS] Arachne: The Era of Spiders begins now.", "[BOSS] Arachne: Ahhhh...A Calling...")
	}
}
