// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.calendar

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers.DAY
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers.EPOCH
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers.MONTH
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers.YEAR
import dev.ashtonite365.apex.util.ScreenUtil

class CalendarScreen : Screen(Component.literal("SkyBlock Calendar")) {
	companion object {
		val MONTHS = listOf(
			"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
			"Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter",
		)

		const val CELL = 9
		private const val DAYS_IN_MONTH = 31
		private const val MONTHS_PER_ROW = 4
		private const val MONTH_GAP = 10
		private const val COLOUR_TODAY = 0xFF00AA00.toInt()
		private const val COLOUR_EVENT = 0xFFAA7700.toInt()
		private const val COLOUR_DAY = 0xFF333333.toInt()

		fun open() = ScreenUtil.setScreenLater(CalendarScreen())

		fun eventsOn(month: Int, day: Int): List<String> {
			if (day !in 1..DAYS_IN_MONTH) return emptyList()
			val dayOfYear = month * DAYS_IN_MONTH + day
			return SkyBlockEventTimers.yearly.filter {
				val first = it.month * DAYS_IN_MONTH + it.day
				dayOfYear in first until first + it.days
			}.map { it.name }
		}

		private fun untilText(minutesAway: Long): String =
			if (minutesAway >= 0) "in ${minutesAway / 60}h ${minutesAway % 60}m" else "${-minutesAway / 60}h ${-minutesAway % 60}m ago"
	}

	private var shownYearShift = 0

	private val monthWidth get() = 7 * CELL + MONTH_GAP
	private val monthHeight get() = 5 * CELL + 16

	override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(ctx, mouseX, mouseY, delta)
		val now = System.currentTimeMillis()
		val elapsed = now - EPOCH
		val year = Math.floorDiv(elapsed, YEAR) + 1 + shownYearShift
		val today = if (shownYearShift == 0) (Math.floorMod(elapsed, YEAR) / MONTH).toInt() to (Math.floorMod(elapsed, MONTH) / DAY).toInt() + 1 else null
		val gridLeft = (width - MONTHS_PER_ROW * monthWidth) / 2
		val gridTop = 30
		ctx.centeredText(font, "§eSkyBlock Year $year §7(scroll to change year)", width / 2, 10, -1)
		var tooltip: List<String>? = null
		MONTHS.forEachIndexed { month, monthName ->
			val x0 = gridLeft + month % MONTHS_PER_ROW * monthWidth
			val y0 = gridTop + month / MONTHS_PER_ROW * monthHeight
			ctx.text(font, "§f$monthName", x0, y0, -1, true)
			for (day in 1..DAYS_IN_MONTH) {
				val x = x0 + (day - 1) % 7 * CELL
				val y = y0 + 10 + (day - 1) / 7 * CELL
				val events = eventsOn(month, day)
				val fill = if (today == month to day) COLOUR_TODAY else if (events.isEmpty()) COLOUR_DAY else COLOUR_EVENT
				ctx.fill(x, y, x + CELL - 1, y + CELL - 1, fill)
				if (mouseX - x in 0 until CELL && mouseY - y in 0 until CELL) {
					val dayStart = EPOCH + (year - 1) * YEAR + month * MONTH + (day - 1) * DAY
					tooltip = listOf("§e$monthName $day", "§7" + untilText((dayStart - now) / 60000)) + events
				}
			}
		}
		tooltip?.let { ctx.setComponentTooltipForNextFrame(font, it.map(Component::literal), mouseX, mouseY) }
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		shownYearShift += if (scrollY > 0) -1 else 1
		return true
	}

	override fun isPauseScreen(): Boolean = false
}

object CalendarCommands {
	@Subscribe
	fun onSubcommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("calendar", dev.ashtonite365.apex.util.data.ManagedConfig.Category.TIMERS) {
			onRun { CalendarScreen.open() }
		}
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (event.matches(dev.ashtonite365.apex.features.timers.Timers.TConfig.openCalendar)) CalendarScreen.open()
	}
}
