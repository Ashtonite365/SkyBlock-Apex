// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.experiments

import io.github.notenoughupdates.moulconfig.ChromaColour
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

@Config
object ExperimentationTable : ManagedConfig("experimentation-table", Category.MISC) {

	val apexMenus by toggle("apex-menus") { true }

	val enabled by riskyToggle("enabled", RiskLevel.GREY_AREA)

	val blockWrongClicks by riskyToggle("block-wrong-clicks", RiskLevel.RISKY)

	val superpairsApexMenus by toggle("superpairs-apex-menus") { true }
	val superpairsReveal by riskyToggle("superpairs-reveal", RiskLevel.GREY_AREA)
	val superpairsCounter by toggle("superpairs-counter") { true }
	val ultraRareBookAlert by toggle("superpairs-ultra-rare-book-alert") { true }

	val chronoApexMenus by toggle("chronomatron-apex-menus") { true }
	val chronoCurrentColour by colour("chronomatron-current-colour") { ChromaColour.fromStaticRGB(0, 255, 0, 140) }
	val chronoNextColour by colour("chronomatron-next-colour") { ChromaColour.fromStaticRGB(255, 255, 0, 110) }
	val chronoShowNext by toggle("chronomatron-show-next") { true }
	val chronoNumbers by toggle("chronomatron-numbers") { true }
	val chronoDebugLog by toggle("chronomatron-debug-log") { true }

	val ultraApexMenus by toggle("ultrasequencer-apex-menus") { true }
	val ultraShowNext by toggle("ultrasequencer-show-next") { true }
	val ultraNumbers by toggle("ultrasequencer-numbers") { true }
	val ultraDebugLog by toggle("ultrasequencer-debug-log") { false }

	override fun loadFrom(key: Unit, jsonObject: JsonObject) =
		super.loadFrom(key, ExperimentTableMigration.apply(jsonObject))
}

object ExperimentTableMigration {
	const val NEW = "experimentation-table"

	val MAP: Map<String, Pair<String, String>> = mapOf(
		"superpairs-reveal" to ("experiment-solvers" to "superpairs-reveal"),
		"superpairs-counter" to ("experiment-solvers" to "superpairs-counter"),
		"superpairs-ultra-rare-book-alert" to ("experiment-solvers" to "ultra-rare-book-alert"),
		"chronomatron-current-colour" to ("apex-chronomatron" to "current-colour"),
		"chronomatron-next-colour" to ("apex-chronomatron" to "next-colour"),
		"chronomatron-show-next" to ("apex-chronomatron" to "show-next"),
		"chronomatron-numbers" to ("apex-chronomatron" to "numbers"),
		"chronomatron-debug-log" to ("apex-chronomatron" to "debug-log"),
		"ultrasequencer-show-next" to ("experiment-solvers" to "show-next"),
		"ultrasequencer-numbers" to ("experiment-solvers" to "sequencer-numbers"),
		"ultrasequencer-debug-log" to ("experiment-solvers" to "debug-log"),
	)

	val ANY_OF: Map<String, List<Pair<String, String>>> = mapOf(
		"enabled" to listOf(NEW to "superpairs-enabled", NEW to "chronomatron-enabled", NEW to "ultrasequencer-enabled",
			"apex-chronomatron" to "enabled", "experiment-solvers" to "enabled"),
		"block-wrong-clicks" to listOf(NEW to "superpairs-block-wrong-clicks", NEW to "chronomatron-block-wrong-clicks",
			NEW to "ultrasequencer-block-wrong-clicks", "apex-chronomatron" to "block-wrong-clicks", "experiment-solvers" to "block-wrong-clicks"),
	)

	val NEW_SWITCHES = setOf("apex-menus", "superpairs-apex-menus", "chronomatron-apex-menus", "ultrasequencer-apex-menus")

	val DROP = ANY_OF.values.flatten().filter { it.first == NEW }.map { it.second }.toSet()

	fun apply(all: JsonObject): JsonObject {
		val current = all[NEW] as? JsonObject ?: JsonObject(emptyMap())
		val merged = current.toMutableMap<String, JsonElement>()
		for ((id, sources) in ANY_OF) {
			if (id in current) continue
			val olds = sources.mapNotNull { (all[it.first] as? JsonObject)?.get(it.second)?.let { v -> (v as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull } }
			if (olds.isNotEmpty()) merged[id] = kotlinx.serialization.json.JsonPrimitive(olds.any { it })
		}
		DROP.forEach { merged.remove(it) }
		for ((id, src) in MAP) {
			if (id in merged) continue
			(all[src.first] as? JsonObject)?.get(src.second)?.let { merged[id] = it }
		}
		if (merged == current) return all
		return JsonObject(all + (NEW to JsonObject(merged)))
	}
}
