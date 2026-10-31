/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex error trail: short markers of what the client was doing, shown in a written trail.
 */
package dev.ashtonite365.apex.util.trail

enum class Crumb(private val label: String) {
	TEXTURE_RELOAD_START("texture engine: resource reload started"),
	TEXTURE_RELOAD_END("texture engine: resource reload applied"),
	TEXTURE_ENGINE("texture engine selected"),
	TEXTURE_FILE_SKIPPED("texture engine: pack file skipped after an error"),
	SCREEN("screen changed to"),
	SERVER_CONNECTED("connected to server"),
	SERVER_JOINED("joined server"),
	WORLD_READY("world ready"),
	;

	fun describe(detail: Any?, number: Long): String {
		val d = when (detail) {
			null -> ""
			is Class<*> -> detail.name
			else -> detail.toString()
		}
		val n = if (number != 0L) " ($number)" else ""
		return if (d.isEmpty()) "$label$n" else "$label: $d$n"
	}
}
