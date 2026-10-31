// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.items.wiki

object WikiAttribution {
	const val SITE = "Hypixel SkyBlock Wiki"
	const val LICENCE = "CC BY-NC-SA 3.0"
	const val LICENCE_URL = "https://creativecommons.org/licenses/by-nc-sa/3.0/"

	fun line(title: String?): String =
		"From the $SITE" + (if (title.isNullOrBlank()) "" else ": $title") + " ($LICENCE). Click for the licence."
}
