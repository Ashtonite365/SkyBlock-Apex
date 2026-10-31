package dev.ashtonite365.apex.gui.config

import net.minecraft.network.chat.Component

object OptionLabels {
	data class Label(val name: String, val description: String)

	val table: Map<String, Label> = mapOf(
		"apex.config.configconfig.enable-yacl" to Label(
			"Alternative settings screen",
			"Open settings in the alternative settings screen when that screen is installed.",
		),
		"apex.config.configconfig.enable-moulconfig" to Label(
			"Classic settings screen",
			"Open settings in the classic settings screen when it is available.",
		),
		"apex.config.configconfig.wide-moulconfig" to Label(
			"Wide classic settings screen",
			"Use the full window width for the classic settings screen.",
		),
		"apex.config.repo.enable-rei" to Label(
			"Item list integration",
			"Show repository items and recipes in the installed item list viewer.",
		),
	)

	fun label(key: String): Component? = table[key]?.let { Component.translatableWithFallback(key, it.name) }

	fun description(key: String): Component? =
		table[key]?.let { Component.translatableWithFallback("$key.description", it.description) }
}
