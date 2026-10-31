// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.modules.ModuleBox
import dev.ashtonite365.apex.modules.ModuleListModel
import dev.ashtonite365.apex.modules.ModuleListRow
import dev.ashtonite365.apex.modules.ModuleManager
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil.setScreenLater
import dev.ashtonite365.apex.util.data.ManagedConfig

private sealed interface Line {
	data class Section(val text: String) : Line
	data class Sub(val text: String) : Line
	data class Module(val row: ModuleListRow) : Line
}

class ModulesScreen(private val parent: Screen?, private var query: String = "") : Screen(Component.literal("Modules")) {
	private var lines: List<Line> = emptyList()
	private var offset = 0
	private var listW = 0
	private lateinit var box: EditBox

	private val left get() = (width - listW) / 2

	private fun holder(id: String) = ApexConfigLoader.allConfigs.filterIsInstance<ManagedConfig>().firstOrNull { it.name == id }

	private fun nameOf(id: String): String = holder(id)?.labelText?.string?.takeIf { it != holder(id)?.translationKey } ?: id

	private fun rebuild() {
		val sections = ModuleListModel.build(ModuleTable.DEFAULT, ModuleManager.currentStates(), DangerousSettings.allowed, ::nameOf, query)
		lines = buildList {
			for (s in sections) {
				add(Line.Section(s.category))
				for (g in s.groups) {
					g.header?.let { add(Line.Sub(it)) }
					g.rows.forEach { add(Line.Module(it)) }
				}
			}
		}
		offset = offset.coerceIn(0, maxOffset())
	}

	private fun maxOffset() = maxOf(0, lines.size * ROW - (height - TOP))

	override fun init() {
		super.init()
		listW = minOf(360, width - 20)
		box = EditBox(font, left, 10, listW, 16, Component.literal("Search modules"))
		box.value = query
		addRenderableWidget(box)
		rebuild()
	}

	private fun lineAt(y: Double): Line? {
		if (y < TOP) return null
		return lines.getOrNull(((y - TOP + offset) / ROW).toInt())
	}

	override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(g, mouseX, mouseY, delta)
		g.enableScissor(0, TOP, width, height)
		var y = TOP - offset
		val hovered = lineAt(mouseY.toDouble())
		for (line in lines) {
			if (y + ROW >= TOP && y < height) when (line) {
				is Line.Section -> g.text(font, Component.literal(line.text), left, y + 3, 0xFFFFD040.toInt(), true)
				is Line.Sub -> g.text(font, Component.literal(line.text), left + 6, y + 3, 0xFFC0C0C0.toInt(), true)
				is Line.Module -> drawModule(g, line.row, y, line === hovered && mouseX in left until left + listW)
			}
			y += ROW
		}
		g.disableScissor()
	}

	private fun drawModule(g: GuiGraphicsExtractor, row: ModuleListRow, y: Int, hovered: Boolean) {
		if (hovered) g.fill(left, y, left + listW, y + ROW, 0x40FFFFFF)
		val boxColour = when (row.box) {
			ModuleBox.NONE -> 0
			ModuleBox.RED -> 0xFFE04040.toInt()
			ModuleBox.PURPLE -> 0xFFA050E0.toInt()
		}
		if (boxColour != 0) g.fill(left + 12, y + 3, left + 20, y + 11, boxColour)
		var text = row.name
		ModuleListModel.inactiveNote(row.inactive, ::nameOf)?.let { text += "  (inactive: $it)" }
		g.text(font, Component.literal(text), left + 24, y + 3, if (row.active) -1 else 0xFF909090.toInt(), false)
		val switchText = if (row.switchedOn) "ON" else "OFF"
		g.text(font, Component.literal(switchText), left + listW - SWITCH_W, y + 3, if (row.switchedOn) 0xFF55FF55.toInt() else 0xFFFF5555.toInt(), true)
		g.text(font, Component.literal("\u2699"), left + listW - GEAR_W, y + 3, -1, false)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
		if (super.mouseClicked(event, doubled)) return true
		val row = (lineAt(event.y()) as? Line.Module)?.row ?: return false
		val x = event.x() - left
		when {
			x >= listW - GEAR_W && x < listW -> holder(row.id)?.showConfigEditor(this)
			x >= listW - SWITCH_W && x < listW - GEAR_W -> toggle(row)
			else -> return false
		}
		return true
	}

	private fun toggle(row: ModuleListRow) {
		val change = ModuleManager.setSwitch(row.id, !row.switchedOn)
		if (change.autoEnabled.isNotEmpty())
			Client.showChat(Component.literal("Also switched on: " + change.autoEnabled.joinToString(", ") { nameOf(it) }))
		if (change.stoppedDependents.isNotEmpty())
			Client.showChat(Component.literal("Stopped with it: " + change.stoppedDependents.joinToString(", ") { nameOf(it) }))
		rebuild()
	}

	override fun mouseScrolled(x: Double, y: Double, h: Double, v: Double): Boolean {
		if (super.mouseScrolled(x, y, h, v)) return true
		offset = (offset - (v * ROW * 2).toInt()).coerceIn(0, maxOffset())
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		box.isFocused = true
		val handled = super.charTyped(event)
		if (box.value != query) { query = box.value; rebuild() }
		return handled
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val handled = super.keyPressed(event)
		if (box.value != query) { query = box.value; rebuild() }
		return handled
	}

	override fun added() { super.added(); if (::box.isInitialized) rebuild() }

	override fun onClose() { minecraft?.gui?.setScreen(parent) }

	override fun isPauseScreen() = false

	companion object {
		const val TOP = 32
		const val ROW = 14
		const val SWITCH_W = 40
		const val GEAR_W = 12
	}
}

object ModulesCommand {
	@Subscribe
	fun registerCommands(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.GENERAL, "modules", "Open the module list, optionally filtered") {
			onArgument("search", RestArgumentType) { text ->
				onRun { setScreenLater(ModulesScreen(null, this[text])) }
			}
			onRun { setScreenLater(ModulesScreen(null)) }
		}
	}
}
