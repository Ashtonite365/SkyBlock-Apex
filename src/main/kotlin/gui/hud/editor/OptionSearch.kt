// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.hud.editor

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.Locale
import java.util.regex.Pattern

class SearchableOption(
	val title: Component,
	val category: Component? = null,
	val description: List<Component> = emptyList(),

	val alsoMatches: (String) -> Boolean = { false },
	val open: (Screen?) -> Screen,
) {
	val rowHeight: Int get() = OptionSearch.TITLE_ROW + description.size * OptionSearch.DESCRIPTION_ROW
}

object OptionSearch {
	const val TITLE_ROW = 15
	const val DESCRIPTION_ROW = 10
	private val BAR = Pattern.compile("\\|")
	private val SPACES = Pattern.compile(" +")

	fun parse(query: String): List<List<String>> =
		BAR.split(query.lowercase(Locale.ROOT)).map { alt -> SPACES.split(alt.trim()).map { it.lowercase(Locale.ROOT) } }

	fun matches(option: SearchableOption, modName: String, query: List<List<String>>): Boolean {
		val haystack = buildList {
			add(option.title.string.lowercase(Locale.ROOT))
			option.category?.let { add(it.string.lowercase(Locale.ROOT)) }
			add(modName.lowercase(Locale.ROOT))
			option.description.forEach { add(it.string.lowercase(Locale.ROOT)) }
		}
		return query.any { words -> words.all { w -> option.alsoMatches(w) || haystack.any { w in it } } }
	}

	fun filter(options: List<SearchableOption>, modName: String, query: String): List<SearchableOption> {
		val parsed = parse(query)
		return options.filter { matches(it, modName, parsed) }
	}

	fun scrolled(current: Double, notches: Double, rows: List<SearchableOption>, screenH: Int, listTop: Int): Double {
		val overflow = rows.sumOf { it.rowHeight } - (screenH - listTop)
		return maxOf(minOf(current - notches * 5, overflow.toDouble()), 0.0)
	}
}

class OptionSearchScreen(
	private val returnTo: Screen?,
	private val options: List<SearchableOption>,
	private val modName: String,
) : Screen(Component.translatable("apex.option-search.title")) {
	private var query = ""
	private var results: List<SearchableOption> = OptionSearch.filter(options, modName, "")
	private var offset = 0.0
	private var boxW = 0
	private lateinit var box: EditBox

	private val listLeft get() = width / 2 - boxW / 2

	override fun init() {
		super.init()
		boxW = minOf(400, width / 3)
		box = EditBox(font, listLeft, 10, boxW, 18, Component.translatable("apex.option-search.hint"))
		box.value = query
		addRenderableWidget(box)
	}

	private fun refresh(text: String) {
		query = text
		results = OptionSearch.filter(options, modName, text)
		offset = OptionSearch.scrolled(offset, 0.0, results, height, LIST_TOP)
	}

	private fun rowAt(x: Double, y: Double): SearchableOption? {
		val lx = x - listLeft
		if (lx < 0 || lx >= boxW) return null
		var top = LIST_TOP - offset
		for (row in results) {
			if (y >= top && y < top + row.rowHeight) return row
			top += row.rowHeight
		}
		return null
	}

	override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(g, mouseX, mouseY, delta)
		g.fill(0, 0, width, height, 0x50000000)
		g.enableScissor(0, LIST_TOP, width, height)
		val hovered = rowAt(mouseX.toDouble(), mouseY.toDouble())
		var top = LIST_TOP - offset.toFloat()
		for (row in results) {
			g.pose().pushMatrix()
			g.pose().translate(listLeft.toFloat(), top)
			if (row === hovered) g.fill(0, 0, boxW, row.rowHeight, 0x50A0A0A0)
			g.text(font, Component.literal("$modName > ").append(row.title), 2, 2, -1, false)
			for ((i, line) in row.description.withIndex())
				g.text(font, line, 2, OptionSearch.TITLE_ROW + i * OptionSearch.DESCRIPTION_ROW, 0xFF808080.toInt(), true)
			g.pose().popMatrix()
			top += row.rowHeight
		}
		g.disableScissor()
	}

	override fun mouseScrolled(x: Double, y: Double, h: Double, v: Double): Boolean {
		if (super.mouseScrolled(x, y, h, v)) return true
		if (y < LIST_TOP || y >= height || x < listLeft || x >= width / 2 + boxW / 2) return false
		offset = OptionSearch.scrolled(offset, v, results, height, LIST_TOP)
		return true
	}

	override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
		if (super.mouseClicked(event, doubled)) return true
		val row = rowAt(event.x(), event.y()) ?: return false
		minecraft?.gui?.setScreen(row.open(this))
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		box.isFocused = true
		val handled = super.charTyped(event)
		if (box.value != query) refresh(box.value)
		return handled
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val handled = super.keyPressed(event)
		if (box.value != query) refresh(box.value)
		return handled
	}

	override fun onClose() { minecraft?.gui?.setScreen(returnTo) }

	companion object { const val LIST_TOP = 35 }
}
