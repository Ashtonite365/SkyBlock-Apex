// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config.storage

import java.nio.file.Files
import java.nio.file.Path
import io.github.notenoughupdates.moulconfig.ChromaColour
import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.data.ManagedConfig

object FreshInstallDefaults {
	const val PROFILE = "quiet-hud"

	data class Place(val anchor: HudAnchor, val x: Int, val y: Int)

	data class Rgba(val r: Int, val g: Int, val b: Int, val a: Int)

	val HIDDEN: List<String> = listOf(
		"ability-cooldowns.extras-hud", "terminal-extras.hud", "event-helpers.diana-hud", "fishing-tracker.hud",
		"galatea.hud", "kuudra-supplies.hud", "trevor.hud",
	)

	val VALUES: List<Pair<String, Any>> = buildList {

		add("custom-scoreboard.enabled" to true)
		add("custom-scoreboard.hud" to Place(HudAnchor.RIGHT, -2, 0))
		add("timers-overlay.enabled" to true)
		add("timers-overlay.cookie-hud" to false)

		for (k in listOf(
			"chat-links.image-enabled", "dungeon-status-huds.blessings", "dungeon-status-huds.breaker",
			"dungeon-status-huds.invincibility", "anniversary.shiny-pigs", "fishing-helper.nearby-creatures",
			"fishing-tracker.trophy-tracker", "crystal-hollows-overlay.enabled", "mining-extras.mineshaft-tracker",
			"mining-extras.ghost-tracker", "mining-extras.nucleus-tracker", "mining-extras.excavator-tracker",
			"mining-loot.loot-tracker", "powder-grinding.enabled", "pristine-profit.enabled", "dragon-tracker.dragons",
			"dragon-tracker.zealots", "gap-extras.thunder-bottle",
		)) add(k to false)
		add("hud.hidden-elements" to HIDDEN.toSet())

		add("hud-style.background-opacity" to 25)
		add("hud-style.theme" to "DARK")
		add("hud-style.text-colour" to Rgba(255, 255, 255, 255))
		add("hud-style.text-shadow" to true)
		add("timer-boards.inventory-background-opacity" to 25)
		add("timer-boards.inventory-theme" to "DARK")
	}

	fun isFresh(configFolder: Path): Boolean = !Files.exists(configFolder)

	fun shouldApply(folderExisted: Boolean, marker: Any?): Boolean = !folderExisted && marker == null

	@Suppress("UNCHECKED_CAST")
	fun convert(current: Any, value: Any): Any? = when {
		current is Boolean && value is Boolean -> value
		current is Int && value is Int -> value
		current is Double && value is Number -> value.toDouble()
		current is Enum<*> && value is String -> current.declaringJavaClass.enumConstants.firstOrNull { it.name == value }
		current is Set<*> && value is Set<*> -> value.toSet()
		current is ChromaColour && value is Rgba -> ChromaColour.fromStaticRGB(value.r, value.g, value.b, value.a)
		else -> null
	}

	@Suppress("UNCHECKED_CAST")
	fun apply(holders: Collection<ManagedConfig>, values: List<Pair<String, Any>> = VALUES): List<String> {
		val byName = holders.associateBy { it.name }
		val missed = mutableListOf<String>()
		for ((path, value) in values) {
			val opt = byName[path.substringBefore('.')]?.allOptions?.get(path.substringAfter('.')) as ManagedOption<Any>?
			val current = opt?._actualValue ?: opt?.default?.invoke()
			if (opt == null || current == null) { missed += path; continue }
			if (current is HudMeta && value is Place) {
				current.position.anchor = value.anchor; current.position.x = value.x; current.position.y = value.y
				opt._actualValue = current
				continue
			}
			val next = convert(current, value)
			if (next == null) { missed += path; continue }
			opt._actualValue = next
		}
		return missed
	}
}
