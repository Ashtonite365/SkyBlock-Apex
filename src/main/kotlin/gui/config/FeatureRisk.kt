// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.data.ManagedConfig

object FeatureRisk {
	enum class Tier { HARD, SOFT }

	const val SOFT_TEXT = "This could be potentially bannable, use at your own risk."

	const val HARD_HEADER = "Borderline bannable: only shown while Misc > Dangerous > Allow borderline bannable features is on."

	private val table get() = dev.ashtonite365.apex.modules.ModuleTable.DEFAULT

	private fun tagged(tag: dev.ashtonite365.apex.modules.ModuleTag, screens: Boolean) = table.rows
		.filter { it.tag == tag && (it.kind == dev.ashtonite365.apex.modules.RowKind.SCREEN) == screens }
		.filter { screens || it.kind == dev.ashtonite365.apex.modules.RowKind.MODULE || it.kind == dev.ashtonite365.apex.modules.RowKind.OPTION }
		.map { it.key }

	val HARD: List<String> = tagged(dev.ashtonite365.apex.modules.ModuleTag.PURPLE, screens = false)

	val SOFT: List<String> = tagged(dev.ashtonite365.apex.modules.ModuleTag.RED, screens = false)

	val SOFT_SCREENS: List<String> = tagged(dev.ashtonite365.apex.modules.ModuleTag.RED, screens = true)

	val MASTERS: Map<String, List<String>> = table.modules.filter { it.tag.gated }
		.associate { row -> row.key to listOfNotNull(row.master.ifEmpty { null }) }

	val FORCED_OFF: Set<String> = setOf("puzzle-solvers.lost-adventurer", "puzzle-solvers.lost-adventurer-colour")

	val TABLE_RISK: Map<String, RiskLevel> by lazy {
		fun level(t: dev.ashtonite365.apex.modules.ModuleTag) =
			if (t == dev.ashtonite365.apex.modules.ModuleTag.PURPLE) RiskLevel.RISKY else RiskLevel.GREY_AREA
		val out = LinkedHashMap<String, RiskLevel>()
		for (r in table.rows) when {
			!r.tag.gated -> {}
			r.kind == dev.ashtonite365.apex.modules.RowKind.OPTION -> out[r.key] = level(r.tag)
			r.kind == dev.ashtonite365.apex.modules.RowKind.MODULE && r.master.isNotEmpty() -> out["${r.key}.${r.master}"] = level(r.tag)
		}
		out
	}

	fun shownRisk(o: ManagedOption<*>): RiskLevel = TABLE_RISK[key(o)] ?: RiskLevel.NONE

	private val hardSet = HARD.toHashSet()
	private val softSet = SOFT.toHashSet()

	init {
		require(hardSet.size == HARD.size && softSet.size == SOFT.size) { "Duplicate FeatureRisk tag" }
		require(hardSet.none { it in softSet }) { "A feature cannot be both hard and soft" }
	}

	fun key(o: ManagedOption<*>) = "${o.element.name}.${o.propertyName}"

	fun tierOf(config: String, option: String): Tier? = when {
		"$config.$option" in hardSet || config in hardSet -> Tier.HARD
		"$config.$option" in softSet || config in softSet -> Tier.SOFT
		else -> null
	}

	fun tierOf(o: ManagedOption<*>): Tier? = tierOf(o.element.name, o.propertyName)

	fun defaultOffKeys(tags: List<String>): List<String> = tags.flatMap { t ->
		if ('.' in t) listOf(t) else MASTERS[t].orEmpty().map { "$t.$it" }
	}

	fun forcedOff(o: ManagedOption<*>): Boolean =
		key(o) in FORCED_OFF || (tierOf(o) == Tier.HARD && !DangerousSettings.allowed)

	val HIDDEN_HOLDERS: Set<String> = setOf("modules")

	fun visibleInMenus(o: ManagedOption<*>): Boolean = !forcedOff(o) && o.element.name !in HIDDEN_HOLDERS

	fun purpleBoxed(o: ManagedOption<*>): Boolean = tierOf(o) == Tier.HARD

	fun softText(): Component = Component.literal(SOFT_TEXT).withStyle(ChatFormatting.RED)

	fun descriptionSuffix(o: ManagedOption<*>): Component? = if (tierOf(o) == Tier.SOFT) softText() else null

	fun visibleOptions(config: ManagedConfig): List<ManagedOption<*>> = config.sortedOptions.filter(::visibleInMenus)
}
