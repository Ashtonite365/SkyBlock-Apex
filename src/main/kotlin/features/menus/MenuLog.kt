// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import dev.ashtonite365.apex.Apex

object MenuLog {
	private val seenTitles = LinkedHashSet<String>()

	fun fallbackLine(menuId: String, title: String, reason: String) =
		"[Apex] $menuId: $reason (on '${MenuRegistry.strip(title)}'; showing the vanilla chest, no auto Go Back/close)"

	fun blockedClick(title: String, slot: Int, button: Int, input: String, why: String) {
		runCatching { Apex.logger.info("[ApexMenu] blocked background click slot=$slot button=$button clickType=$input on '${MenuRegistry.strip(title)}' ($why)") }
	}

	fun fallback(menuId: String, title: String, reason: String, e: Throwable? = null) {
		runCatching {
			val msg = fallbackLine(menuId, title, reason)
			if (e != null) Apex.logger.warn(msg, e) else Apex.logger.warn(msg)
		}
	}

	fun title(raw: String, slots: Int, result: String) {
		runCatching {

			val key = "$raw|$result"
			val matched = result != "none" && result != "denied"
			if (!matched && !seenTitles.add(key)) return
			if (seenTitles.size > 500) seenTitles.remove(seenTitles.first())
			Apex.logger.info("[ApexMenu] title '${MenuRegistry.strip(raw)}' raw='$raw' len=${raw.length} slots=$slots -> $result")
		}
	}
}
