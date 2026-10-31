// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.world.ApexWaypointManagerHandler
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.SectionPage
import dev.ashtonite365.apex.settings.Subsection
import dev.ashtonite365.apex.settings.SubsectionView
import dev.ashtonite365.apex.util.data.ManagedConfig

enum class SettingsEditorKind {
	CHOICE, TOGGLE, TEXT, COLOUR, ACTION, HUD_EDITOR, DURATION_SLIDER, INT_SLIDER, INT_INPUT, WAYPOINT_MANAGER, KEY_BINDING, DECIMAL_SLIDER,
}

object SettingsMenuPlan {

	const val FIRST_ACCORDION_ID = 720

	sealed interface Row<out T> {

		data class Shortcut(val target: Subsection) : Row<Nothing>

		data class Header(val accordionId: Int, val subsection: Subsection) : Row<Nothing>

		data class Entry<T>(val accordionId: Int, val item: T) : Row<T>
	}

	fun <T> rows(page: SectionPage<T>): List<Row<T>> {
		val out = ArrayList<Row<T>>()
		for (target in page.links) out += Row.Shortcut(target)
		var anchor = FIRST_ACCORDION_ID
		for (view in page.subsections) {
			val id = anchor++
			out += Row.Header(id, view.subsection)
			for (item in view.items) out += Row.Entry(id, item)
		}
		return out
	}

	data class Group<T>(val page: SectionPage<T>, val view: SubsectionView<T>)

	fun <T> groups(main: MainSection, pages: List<SectionPage<T>>): List<Group<T>> =
		pages.asSequence().filter { it.main == main }.flatMap { page -> page.subsections.map { Group(page, it) } }.toList()

	fun groupName(group: Group<*>): Component {
		val own = SettingsTabs.label(group.view.subsection)
		if (group.page.group.isEmpty()) return own
		return SettingsTabs.label(group.page).copy().append(": ").append(own)
	}

	private val moulConfigKinds: Map<Class<*>, SettingsEditorKind> = mapOf(
		ChoiceHandler::class.java to SettingsEditorKind.CHOICE,
		BooleanHandler::class.java to SettingsEditorKind.TOGGLE,
		StringHandler::class.java to SettingsEditorKind.TEXT,
		ColourHandler::class.java to SettingsEditorKind.COLOUR,
		ClickHandler::class.java to SettingsEditorKind.ACTION,
		HudMetaHandler::class.java to SettingsEditorKind.HUD_EDITOR,
		DurationHandler::class.java to SettingsEditorKind.DURATION_SLIDER,
		IntegerHandler::class.java to SettingsEditorKind.INT_SLIDER,
		IntegerInputHandler::class.java to SettingsEditorKind.INT_INPUT,
		ApexWaypointManagerHandler::class.java to SettingsEditorKind.WAYPOINT_MANAGER,
		KeyBindingHandler::class.java to SettingsEditorKind.KEY_BINDING,
		StringSetHandler::class.java to SettingsEditorKind.ACTION,
		DecimalHandler::class.java to SettingsEditorKind.DECIMAL_SLIDER,
		dev.ashtonite365.apex.features.timers.TimerBoards.OwnPositionsHandler::class.java to SettingsEditorKind.ACTION,
	)

	fun moulConfigKind(handler: ManagedConfig.OptionCodec<*>): SettingsEditorKind? = moulConfigKinds[handler.javaClass]

	fun yaclKind(handler: ManagedConfig.OptionCodec<*>): SettingsEditorKind? = when (handler) {
		is ActionCodec -> SettingsEditorKind.ACTION
		is HudMetaHandler -> SettingsEditorKind.HUD_EDITOR
		is ChoiceHandler<*> -> SettingsEditorKind.CHOICE
		is ColourHandler -> SettingsEditorKind.COLOUR
		is BooleanHandler -> SettingsEditorKind.TOGGLE
		is StringHandler -> SettingsEditorKind.TEXT
		is IntegerHandler -> SettingsEditorKind.INT_SLIDER
		is DurationHandler -> SettingsEditorKind.DURATION_SLIDER
		is DecimalHandler -> SettingsEditorKind.DECIMAL_SLIDER
		is KeyBindingHandler -> SettingsEditorKind.KEY_BINDING
		else -> null
	}
}

object SettingsSliders {

	const val DURATION_STEP = 0.1

	const val CHROMA_SPEED_MAX_MS = 60_000
	const val CHROMA_SPEED_STEP_MS = 10

	fun seconds(value: Duration): Double = value.toDouble(DurationUnit.SECONDS)
	fun duration(seconds: Double): Duration = seconds.seconds

	fun position(value: Duration): Float = seconds(value).toFloat()
	fun duration(position: Float): Duration = duration(position.toDouble())
	fun position(value: Int): Float = value.toFloat()

	fun whole(position: Float): Int = position.toInt()
}
