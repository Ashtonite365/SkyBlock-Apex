// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.features.kit.terminals.DelayedClicks
import dev.ashtonite365.apex.features.kit.terminals.PickModel
import dev.ashtonite365.apex.features.kit.terminals.RubixModel
import dev.ashtonite365.apex.features.menus.MenuPress

class TerminalSimulatorScreen : Screen(Component.literal("Terminal Simulator")) {
	enum class Mode(val label: String) {
		PANES("Correct all the panes!"), ORDER("Click in order!"), RUBIX("Change all to same color!"),
		STARTS_WITH("What starts with: '?'?"), SELECT_ALL("Select all the ? items!");
		fun next() = entries[(ordinal + 1) % entries.size]
	}

	private class Round(val mode: Mode) {
		val lit = BooleanArray(PANE_COUNT) { Math.random() < 0.3 }
		val numbers = (1..ORDER_COUNT).shuffled()
		var wanted = 1
		val cube = RubixModel()
		var prompt = ""
		val picker: PickModel? = when (mode) {
			Mode.STARTS_WITH -> PickModel.LETTERS.random().let { prompt = "What starts with: '$it'?"; PickModel.startsWith(it) }
			Mode.SELECT_ALL -> PickModel.COLOURS.random().let { prompt = "Select all the ${it.uppercase()} items!"; PickModel.selectAll(it) }
			else -> null
		}
		val openedAt = System.currentTimeMillis()
		var tookMs: Long? = null

		val heading get() = if (picker != null) prompt else mode.label
		val columns get() = when (mode) { Mode.PANES -> 5; Mode.RUBIX -> 3; else -> 7 }
		val cells get() = when (mode) { Mode.PANES -> PANE_COUNT; Mode.ORDER -> ORDER_COUNT; Mode.RUBIX -> 9; else -> picker?.labels?.size ?: 0 }

		fun press(i: Int, right: Boolean) {
			when (mode) {
				Mode.PANES -> lit[i] = !lit[i]
				Mode.ORDER -> if (numbers[i] == wanted) wanted++
				Mode.RUBIX -> cube.click(i, right)
				else -> picker?.click(i)
			}
		}

		val solved get() = when (mode) {
			Mode.PANES -> lit.all { it }
			Mode.ORDER -> wanted > ORDER_COUNT
			Mode.RUBIX -> cube.done
			else -> picker?.done == true
		}
	}

	private var round = Round(Mode.PANES)
	private val queued = DelayedClicks<Pair<Int, Boolean>>()
	private val personalBest = HashMap<Mode, Long>()

	private fun deal(mode: Mode = round.mode) {
		round = Round(mode)
		queued.clear()
	}

	private fun gridLeft() = (width - round.columns * CELL) / 2
	private fun gridTop() = height / 2 - 30

	override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(ctx, mouseX, mouseY, delta)
		for ((i, right) in queued.due()) land(i, right)
		val r = round
		val top = gridTop()
		val hint = if (r.mode == Mode.RUBIX) "Tab: switch terminal" else "right click / Tab: switch terminal"
		ctx.centeredText(font, "§e${r.heading} §7($hint)", width / 2, top - 30, -1)
		val seconds = (r.tookMs ?: (System.currentTimeMillis() - r.openedAt)) / 1000.0
		val bestText = personalBest[r.mode]?.let { " §7best ${"%.2f".format(it / 1000.0)}s" } ?: ""
		val lead = if (r.tookMs != null) "§aDone in " else "§f"
		ctx.centeredText(font, lead + "%.2fs".format(seconds) + bestText, width / 2, top - 16, -1)
		for (i in 0 until r.cells) {
			val x = gridLeft() + (i % r.columns) * CELL
			val y = top + (i / r.columns) * CELL
			fun box(argb: Int) = ctx.fill(x, y, x + CELL - 2, y + CELL - 2, argb)
			fun caption(s: String) = ctx.centeredText(font, s, x + CELL / 2 - 1, y + 6, -1)
			when (r.mode) {
				Mode.PANES -> box(if (r.lit[i]) GREEN else RED)
				Mode.ORDER -> { val n = r.numbers[i]; box(if (n < r.wanted) GREEN else RED); caption(n.toString()) }
				Mode.RUBIX -> box(CUBE_COLOURS[r.cube.cells[i]])
				else -> {
					val p = r.picker ?: continue
					box(if (p.picked[i]) GREEN else GREY)
					val label = p.labels[i]
					caption(if (r.mode == Mode.STARTS_WITH) label.take(3) else abbreviate(label))
				}
			}
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT
		val r = round
		if (right && r.mode != Mode.RUBIX) {
			MenuPress.acted()
			deal(when (r.mode) { Mode.PANES -> Mode.ORDER; Mode.ORDER -> Mode.PANES; else -> r.mode.next() })
			return true
		}
		if (r.tookMs != null || r.picker?.failed == true) { MenuPress.acted(); deal(); return true }
		val dx = event.x() - gridLeft()
		val dy = event.y() - gridTop()
		val i = (dx / CELL).toInt() + (dy / CELL).toInt() * r.columns
		if (dx < 0 || dy < 0 || i !in 0 until r.cells || dx / CELL >= r.columns) return false
		MenuPress.acted()
		val ping = TerminalSimulator.TConfig.ping
		if (ping > 0) queued.add(i to right, ping) else land(i, right)
		return true
	}

	override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
		if (event.key() != InputConstants.KEY_TAB) return super.keyPressed(event)
		deal(round.mode.next())
		return true
	}

	private fun land(i: Int, right: Boolean) {
		val r = round
		if (r.tookMs != null) return
		r.press(i, right)
		if (!r.solved) return
		val took = System.currentTimeMillis() - r.openedAt
		r.tookMs = took
		if (personalBest[r.mode]?.let { took < it } != false) personalBest[r.mode] = took
	}

	override fun isPauseScreen() = false

	private companion object {
		const val CELL = 20
		const val PANE_COUNT = 15
		const val ORDER_COUNT = 14
		val GREEN = 0xFF00AA00.toInt()
		val RED = 0xFFAA0000.toInt()
		val GREY = 0xFF444444.toInt()
		val CUBE_COLOURS = intArrayOf(0xFFFF8800.toInt(), 0xFFFFFF55.toInt(), 0xFF00AA00.toInt(), 0xFF5555FF.toInt(), 0xFFAA0000.toInt())

		fun abbreviate(label: String): String {
			val words = label.split(' ')
			return words.dropLast(1).joinToString("") { it.take(1) } + words.last().take(1).lowercase()
		}
	}
}

object TerminalSimulator {
	@dev.ashtonite365.apex.util.data.Config
	object TConfig : dev.ashtonite365.apex.util.data.ManagedConfig("terminal-simulator", Category.DUNGEONS) {

		val ping by integer("ping", 0, 500) { 0 }
	}

	@Subscribe
	fun onSubcommand(event: CommandEvent.SubCommand) {
		event.register(dev.ashtonite365.apex.util.data.ManagedConfig.Category.DUNGEONS, "termsim", "Open the offline F7 terminal simulator") {
			onRun { ScreenUtil.setScreenLater(TerminalSimulatorScreen()) }
		}
	}
}
