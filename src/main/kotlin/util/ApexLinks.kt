// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util

object ApexLinks {
	const val SOURCE_URL = ""
	const val ISSUES_URL = ""
	const val HOMEPAGE_URL = ""
	const val MODRINTH_URL = ""
	const val WIKI_URL = ""
	const val DISCORD_URL = ""

	val hasDiscord get() = DISCORD_URL.isNotBlank()

	fun orNull(url: String): String? = url.takeIf { it.isNotBlank() }
}
