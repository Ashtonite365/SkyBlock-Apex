package dev.ashtonite365.apex.features.storage

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.features.inventory.InventoryOverlays
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayScreen
import dev.ashtonite365.apex.features.inventory.storageoverlay.StoragePageSlot
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.skyblock.SackUtil
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object StorageSearch {
	enum class Where { STORAGE, SACKS, EQUIPMENT }

	data class Result(val stack: ItemStack, val count: Long, val where: Where, val page: StoragePageSlot?, val location: String)

	@OptIn(NeedsComponents::class, dev.ashtonite365.apex.repo.ExpensiveItemCacheApi::class)
	fun search(query: String): List<Result> {
		if (query.isBlank()) return emptyList()
		val cfg = StorageOverlay.TConfig
		val out = mutableListOf<Result>()
		val data = StorageOverlay.Data.data
		for ((page, inv) in data.knownPages) {
			val name = data.labelFor(page)
			inv.inventory?.stacks?.forEachIndexed { index, lazy ->
				val stack = lazy.current()
				if (!stack.isEmpty && StorageOverlayScreen.matchesSearch(stack, query))
					out += Result(stack, stack.count.toLong(), Where.STORAGE, page, "$name, slot ${index + 1}")
			}
		}
		if (cfg.searchSacks) for ((id, amount) in SackUtil.items) {
			if (amount <= 0) continue
			val stack = SBItemStack(id).asImmutableItemStack().current()
			if (StorageOverlayScreen.matchesSearch(stack, query))
				out += Result(stack, amount, Where.SACKS, null, "Sacks")
		}
		if (cfg.searchEquipment) InventoryOverlays.DConfig.data.equipment?.stacks?.forEach { lazy ->
			val stack = lazy.current()
			if (!stack.isEmpty && StorageOverlayScreen.matchesSearch(stack, query))
				out += Result(stack, stack.count.toLong(), Where.EQUIPMENT, null, "Equipment")
		}
		if (!cfg.searchGroupResults) return out

		return out.groupBy { Triple(it.where, it.page, it.stack.hoverName.bareText) }.map { (_, rs) ->
			val first = rs.first()
			val loc = if (rs.size == 1) first.location else first.location.substringBefore(", slot") + " (${rs.size} slots)"
			first.copy(count = rs.sumOf { it.count }, location = loc)
		}
	}

	fun open(query: String? = null) {
		query?.let { StorageOverlayScreen.searchText.set(it) }
		ScreenUtil.setScreenLater(SearchScreen())
	}

	@Subscribe
	fun onWorldKey(event: WorldKeyboardEvent) {
		if (Client.screen != null) return
		if (event.matches(StorageOverlay.TConfig.searchKey)) open()
	}

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.INVENTORY, "storagesearch", "Search all cached storage, sacks and equipment") {
			onRun { open() }
			onArgument("query", RestArgumentType) { q -> onRun { open(this[q]) } }
		}
	}

	class SearchScreen : Screen(tr("apex.storage-search.title", "Storage Search")) {
		private lateinit var box: EditBox
		private var results = listOf<Result>()
		private var lastQuery: String? = null
		private var scroll = 0
		private val rowH = 20
		private val listTop = 40
		private val listW get() = minOf(360, width - 20)
		private val listX get() = width / 2 - listW / 2

		override fun init() {
			box = EditBox(font, listX, 14, listW, 18, Component.empty())
			box.setMaxLength(100)
			box.value = StorageOverlayScreen.searchText.get()
			box.setResponder { StorageOverlayScreen.searchText.set(it) }
			addRenderableWidget(box)
			setInitialFocus(box)
		}

		private fun refresh() {
			val q = StorageOverlayScreen.searchText.get()
			if (q != lastQuery) { lastQuery = q; results = search(q); scroll = 0 }
		}

		private val visibleRows get() = ((height - listTop - 10) / rowH).coerceAtLeast(1)

		@OptIn(NeedsComponents::class)
		override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
			refresh()
			context.fill(0, 0, width, height, 0xA0000000.toInt())
			super.extractRenderState(context, mouseX, mouseY, delta)
			val q = StorageOverlayScreen.searchText.get()
			val header = when {
				q.isBlank() -> tr("apex.storage-search.hint", "Type to search every cached ender chest page, backpack, sack and equipment slot.")
				results.isEmpty() -> tr("apex.storage-search.none", "No cached item matches. Open your storage pages once to cache them.")
				else -> tr("apex.storage-search.count", "${results.size} results")
			}
			context.text(font, header, listX, listTop - 6 - font.lineHeight + 4, 0xFFAAAAAA.toInt(), true)
			var hovered: Result? = null
			results.drop(scroll).take(visibleRows).forEachIndexed { i, r ->
				val y = listTop + i * rowH
				val over = mouseX in listX until listX + listW && mouseY in y until y + rowH
				if (over) hovered = r
				context.fill(listX, y, listX + listW, y + rowH - 2, if (over) 0x60FFFFFF else 0x30FFFFFF)
				context.item(r.stack, listX + 2, y + 1)
				val countText = if (r.count > 1) " §7x${ApexFormatters.withThousands(r.count)}" else ""
				context.text(font, Component.literal(r.stack.hoverName.string + countText), listX + 22, y + 2, -1, true)
				val colour = when (r.where) { Where.STORAGE -> 0xFF55FFFF; Where.SACKS -> 0xFFFFAA00; Where.EQUIPMENT -> 0xFFAA55FF }
				context.text(font, Component.literal(r.location), listX + 22, y + 11, colour.toInt(), false)
			}
			if (results.size > visibleRows)
				context.text(font, Component.literal("${scroll + 1}-${minOf(results.size, scroll + visibleRows)}/${results.size}"), listX + listW - 60, listTop - 10, 0xFF888888.toInt(), false)
			val h = hovered ?: return
			if (h.page != null && StorageOverlay.TConfig.hoverPreview) {
				StorageApex.drawPagePreview(context, width, height, mouseX, mouseY, h.page, StorageOverlay.Data.data.knownPages[h.page])
			} else {
				context.setTooltipForNextFrame(font, h.stack, mouseX, mouseY)
			}
		}

		override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
			scroll = (scroll - verticalAmount.toInt()).coerceIn(0, (results.size - visibleRows).coerceAtLeast(0))
			return true
		}

		override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
			if (super.mouseClicked(click, doubled)) return true
			val i = ((click.y - listTop) / rowH).toInt()
			if (click.x < listX || click.x >= listX + listW || click.y < listTop || i < 0) return false
			val r = results.drop(scroll).getOrNull(i) ?: return false
			val page = r.page ?: return true
			dev.ashtonite365.apex.features.menus.MenuPress.acted()
			onClose()
			page.navigateTo()
			return true
		}

		override fun isPauseScreen() = false
	}
}
