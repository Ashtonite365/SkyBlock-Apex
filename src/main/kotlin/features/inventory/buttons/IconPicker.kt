// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory.buttons

import dev.ashtonite365.apex.repo.itemsSafe
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.NeedsComponents

class IconPickerModel(val entries: List<Entry>, val columns: Int, val rows: Int) {

	data class Entry(val icon: String, val name: String, val skyblock: Boolean)

	var query = ""; private set
	var scrollRow by dev.ashtonite365.apex.util.ScrollMemory.int { "icon-picker" }; private set
	var filtered: List<Entry> = entries; private set

	fun search(q: String) {
		query = q
		val words = q.lowercase().split(' ').filter { it.isNotBlank() }
		filtered = if (words.isEmpty()) entries else entries.filter { e ->
			val hay = (e.name + " " + e.icon.replace('_', ' ') + " " + e.icon).lowercase()
			words.all { it in hay }
		}
		if (query.isNotEmpty()) scrollRow = 0
	}

	val totalRows get() = (filtered.size + columns - 1) / columns
	val maxScroll get() = maxOf(0, totalRows - rows)
	fun scroll(deltaRows: Int) { scrollRow = (scrollRow + deltaRows).coerceIn(0, maxScroll) }

	fun at(col: Int, row: Int): Entry? {
		if (col !in 0 until columns || row !in 0 until rows) return null
		return filtered.getOrNull((scrollRow + row) * columns + col)
	}

	fun pick(col: Int, row: Int): String? = at(col, row)?.icon

	companion object {

		fun build(vanilla: List<Pair<String, String>>, skyblock: List<Pair<String, String>>): List<Entry> =
			(skyblock.map { Entry(it.first, strip(it.second), true) }.sortedBy { it.name.lowercase() } +
				vanilla.filter { it.first != "air" }.map { Entry(it.first, strip(it.second), false) }.sortedBy { it.name.lowercase() })
				.distinctBy { it.icon }

		fun strip(s: String) = s.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()
	}
}

class IconPickerScreen(val parent: Screen?, val onPick: (String) -> Unit) : Screen(Component.literal("Pick an icon")) {
	private val cell = 18
	private var model = IconPickerModel(emptyList(), 1, 1)
	private var px = 0; private var py = 0; private var pw = 0; private var ph = 0
	private var gridX = 0; private var gridY = 0
	private lateinit var search: EditBox
	private val stacks = HashMap<String, ItemStack>()
	private val vanillaItems = HashMap<String, net.minecraft.world.item.Item>()

	private fun allEntries(): List<IconPickerModel.Entry> {
		val vanilla = BuiltInRegistries.ITEM.map { item ->
			val id = BuiltInRegistries.ITEM.getKey(item).path
			vanillaItems[id] = item
			id to runCatching { item.defaultInstance.hoverName.string }.getOrDefault(id)
		}
		val sky = runCatching { RepoManager.coreRepo.items.itemsSafe.values.map { it.skyblockItemId to it.displayName } }.getOrDefault(emptyList())
		return IconPickerModel.build(vanilla, sky)
	}

	override fun init() {
		super.init()
		pw = minOf(width - 20, 18 * 20 + 16)
		ph = minOf(height - 20, 18 * 12 + 50)
		px = (width - pw) / 2; py = (height - ph) / 2
		gridX = px + 8; gridY = py + 40
		val cols = (pw - 16 - 4) / cell
		val rows = (ph - 48) / cell
		val q = if (this::search.isInitialized) search.value else ""
		model = IconPickerModel(if (model.entries.isEmpty()) allEntries() else model.entries, cols, rows)
		model.search(q)
		search = EditBox(font, px + 8, py + 22, pw - 16, 14, Component.literal("Search"))
		search.setHint(Component.literal("§7Search items…"))
		search.value = q
		search.setResponder { model.search(it) }
		addRenderableWidget(search)
		setInitialFocus(search)
	}

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	private fun stack(e: IconPickerModel.Entry): ItemStack = stacks.getOrPut(e.icon) {
		runCatching {
			if (e.skyblock) ButtonIcons.resolve(e.icon).current()
			else vanillaItems.getValue(e.icon).defaultInstance
		}.getOrDefault(ItemStack(Items.BARRIER))
	}

	override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		MenuTheme.panel(ctx, px, py, pw, ph)
		ctx.text(font, "§6§lPick an icon §r§7(${model.filtered.size})", px + 8, py + 8, MenuTheme.WHITE)
		super.extractRenderState(ctx, mouseX, mouseY, delta)
		var hovered: IconPickerModel.Entry? = null
		for (r in 0 until model.rows) for (c in 0 until model.columns) {
			val e = model.at(c, r) ?: continue
			val x = gridX + c * cell; val y = gridY + r * cell
			val over = MenuTheme.inside(mouseX, mouseY, x, y, cell, cell)
			if (over) { hovered = e; ctx.fill(x, y, x + cell, y + cell, MenuTheme.HOVER_ROW) }
			ctx.item(stack(e), x + 1, y + 1)
		}
		MenuTheme.scrollbar(ctx, px + pw - 6, gridY, model.rows * cell, model.rows, model.totalRows, model.scrollRow)
		if (model.filtered.isEmpty()) ctx.text(font, "§7No items match.", gridX, gridY + 4, MenuTheme.GREY)
		hovered?.let { ctx.setTooltipForNextFrame(font, listOf<Component>(Component.literal("${it.name}§8 (${it.icon})")), java.util.Optional.empty(), mouseX, mouseY) }
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val c = Math.floorDiv(click.x().toInt() - gridX, cell)
		val r = Math.floorDiv(click.y().toInt() - gridY, cell)
		val picked = if (click.x() >= gridX && click.y() >= gridY) model.pick(c, r) else null
		if (picked != null) { dev.ashtonite365.apex.features.menus.MenuPress.acted(); onPick(picked); Client.screen = parent; return true }
		return super.mouseClicked(click, doubled)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		model.scroll(if (scrollY > 0) -1 else if (scrollY < 0) 1 else 0); return true
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		if (input.input() == InputConstants.KEY_ESCAPE) { onClose(); return true }
		return super.keyPressed(input)
	}

	override fun onClose() { Client.screen = parent }
	override fun isPauseScreen() = false
}
