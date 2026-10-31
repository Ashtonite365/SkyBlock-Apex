package dev.ashtonite365.apex.features.inventory.storageoverlay

import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.gui.component.ColumnComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import io.github.notenoughupdates.moulconfig.observer.Property
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay
import dev.ashtonite365.apex.features.inventory.SlotLocking
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.EmptyComponent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.GuiXml.adopt
import dev.ashtonite365.apex.util.GuiXml.drawComponentIn
import dev.ashtonite365.apex.util.GuiXml.keyIntoComponent
import dev.ashtonite365.apex.util.GuiXml.mouseIntoComponent
import dev.ashtonite365.apex.util.StringUtil.words
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.mc.FakeSlot
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.enableScissorWithoutTranslation
import dev.ashtonite365.apex.util.render.guiSprite
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.tr

class StorageOverlayScreen : Screen(Component.literal("")), dev.ashtonite365.apex.util.TextInputFocus {

	companion object Shared {

		val searchText: Property<String> = Property.of("")

		private var sharedScroll = 0F

		fun matchesSearch(stack: ItemStack, query: String): Boolean {
			if (query.isBlank()) return true

			if (query != cachedQuery) { matchCache.clear(); cachedQuery = query }
			return matchCache.getOrPut(stack) { matchesUncached(stack, query) }
		}

		private val matchCache = java.util.WeakHashMap<ItemStack, Boolean>()
		private var cachedQuery = ""

		private fun matchesUncached(stack: ItemStack, query: String): Boolean {
			val view = stack.view()
			val lines = ArrayList<Component>()
			lines.add(view.displayNameAccordingToNbt)
			lines.addAll(view.loreAccordingToNbt)
			val haystack = ArrayList<String>()
			for (line in lines) for (w in line.bareText.words()) haystack.add(w.lowercase())
			for (needle in query.lowercase().words())
				if (haystack.none { needle in it }) return false
			return true
		}

		fun forgetScroll() { if (!StorageOverlay.TConfig.retainScroll) sharedScroll = 0F }
	}

	override val textInputFocused: Boolean
		get() = guiContext.focusedElement.let { it != null && it !== knobHolder }

	var leftOnPurpose = false

	private var cachedGeometry: StorageOverlayGeometry? = null
	private var cachedFor: List<Int> = emptyList()

	val geometry: StorageOverlayGeometry
		get() {
			val styled = StorageOverlay.TConfig.textureStyle.followsMenuStyle
			val wanted = listOf(width, height, StorageOverlay.TConfig.columns, StorageOverlay.TConfig.height, if (styled) 1 else 0)
			cachedGeometry?.takeIf { wanted == cachedFor }?.let { return it }

			return StorageOverlayGeometry(width, height, wanted[2], wanted[3], separateControls = styled).also { cachedGeometry = it; cachedFor = wanted }
		}

	private val knobHolder: GuiComponent = EmptyComponent()

	private var knobGrab = 0.0

	private val searchField = TextFieldComponent(
		searchText, StorageOverlayGeometry.CONTROLS_WIDTH - 8, { true },
		tr("apex.storage-overlay.search.suggestion", "Search...").string,
		IMinecraft.INSTANCE.defaultFontRenderer,
	)
	private val editButton = ApexButton(TextComponent(tr("apex.storage-overlay.edit-pages", "Edit Pages").string), action = { editPages() })
	private val controls = ColumnComponent(searchField, editButton)
	private val guiContext = GuiContext(controls).also { it.adopt(knobHolder) }

	override fun onClose() { markLeaving(); super.onClose() }

	fun markLeaving() {
		leftOnPurpose = true
		forgetScroll()
	}

	private fun editPages() {
		markLeaving()
		Client.runCommand("storage")
	}

	private fun rememberedPages(): List<StorageData.RememberedPage> = StorageOverlay.Data.data.knownPages.values.toList()

	private var livePage: StoragePageSlot? = null
	private var liveRows = 0
	fun setLivePage(page: StoragePageSlot?, rows: Int) { livePage = page; liveRows = rows }

	private fun rowsOf(page: StorageData.RememberedPage): Int? =
		StorageOverlayGeometry.boxRows(page.inventory?.rows, page.slot == livePage, liveRows)

	private var layoutKey: List<Any?> = emptyList()
	private var layoutMemo: StorageOverlayGeometry.PageLayout? = null

	private fun pageLayout(pages: List<StorageData.RememberedPage>): StorageOverlayGeometry.PageLayout {
		val g = geometry
		val lineHeight = font.lineHeight
		val heights = pages.map { StorageOverlayGeometry.pageHeight(rowsOf(it), lineHeight) }
		val key = listOf(g, heights, sharedScroll)
		layoutMemo?.takeIf { key == layoutKey }?.let { return it }
		val first = g.layoutPages(heights, 0F, StorageOverlayGeometry.ROW_GAP)
		sharedScroll = g.clampScroll(sharedScroll, first.contentHeight)
		val out = if (sharedScroll == 0F) first else g.layoutPages(heights, sharedScroll, StorageOverlayGeometry.ROW_GAP)
		layoutKey = listOf(g, heights, sharedScroll); layoutMemo = out
		return out
	}

	private fun contentHeight() = pageLayout(rememberedPages()).contentHeight

	private fun moveScroll(to: Float) { sharedScroll = geometry.clampScroll(to, contentHeight()) }

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick)
		setLivePage(null, 0)
		paintFrames(graphics)
		paintPages(graphics, mouseX, mouseY, null, null, Point(0, 0))
		paintScrollBar(graphics)
		paintOwnInventory(graphics, mouseX, mouseY)
		paintControls(graphics, mouseX, mouseY)
		paintBackArrow(graphics, mouseX, mouseY)
	}

	fun paintBackArrow(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val r = StorageBackArrow.rect(geometry.frameX, geometry.frameY)
		val hover = dev.ashtonite365.apex.features.menus.MenuTheme.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])
		dev.ashtonite365.apex.features.menus.MenuTheme.button(context, font, r[0], r[1], r[2], r[3], "←",
			dev.ashtonite365.apex.features.menus.MenuTheme.state(hover, enabled = true),
			palette = dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.theme())
		if (hover) context.setComponentTooltipForNextFrame(font, listOf(net.minecraft.network.chat.Component.literal("Back")), mouseX, mouseY)
	}

	private fun paintOwnInventory(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val inventory = Client.player?.inventory ?: return
		val positions = (0 until 36).map { getPlayerInventorySlotPosition(it) }
		val hovered = InventoryPanel.cellAtPositions(positions, mouseX.toDouble(), mouseY.toDouble())
		val frame = SlotLinkOverlay.planFrame(SlotLinkOverlay.fromPositions(::getPlayerInventorySlotPosition),
			SlotLinkOverlay.liveState(), hovered, SlotLocking.TConfig.bindLines, null)
		SlotLinkOverlay.drawLinks(context, frame, mouseX, mouseY)
		for ((index, at) in positions.withIndex()) {
			val stack = inventory.getItem(index)
			if (stack.isEmpty) continue
			context.item(stack, at.first, at.second)
			context.itemDecorations(font, stack, at.first, at.second)
		}
		SlotLinkOverlay.drawLocks(context, frame)
		if (hovered != null) inventory.getItem(hovered).takeUnless { it.isEmpty }?.let { showTooltip(context, it, mouseX, mouseY) }
	}

	fun paintScrollBar(context: GuiGraphicsExtractor) {
		val bar = getScrollBarRect()
		context.guiSprite(StorageOverlay.TConfig.textureStyle.sprite("scroll_bar_background"), bar.x, bar.y, bar.width, bar.height)
		val knobY = geometry.knobTop(sharedScroll, contentHeight()).coerceIn(bar.y, bar.y + bar.height - StorageOverlayGeometry.KNOB_HEIGHT)
		context.guiSprite(StorageOverlay.TConfig.textureStyle.sprite("scroll_bar_knob"), bar.x, knobY, bar.width, StorageOverlayGeometry.KNOB_HEIGHT)
	}

	private fun controlsArea(): Rectangle = geometry.controls().let { Rectangle(it.x + 4, it.y + 4, it.width - 8, it.height - 8) }

	fun paintControls(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		context.drawComponentIn(controls, controlsArea(), mouseX, mouseY)
	}

	fun paintFrames(context: GuiGraphicsExtractor) {
		val g = geometry
		val style = StorageOverlay.TConfig.textureStyle
		g.frame().let { context.guiSprite(style.sprite("upper_background"), it.x, it.y, it.width, it.height) }
		g.controlsBackground().let { context.guiSprite(style.sprite("storage_controls"), it.x, it.y, it.width, it.height) }
		g.playerPanel().let { context.guiSprite(style.sprite("player_inventory"), it.x, it.y, it.width, it.height) }
	}

	private fun pageTitleColour(): Int =
		if (StorageOverlay.TConfig.textureStyle.followsMenuStyle) dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.text
		else 0xFF404040.toInt()

	fun getPlayerInventorySlotPosition(int: Int): Pair<Int, Int> = geometry.playerSlot(int)

	fun getScrollBarRect(): Rectangle = geometry.scrollBar()

	fun getScrollPanelInner(): Rectangle = geometry.panel()

	fun clipToPanel(context: GuiGraphicsExtractor) = getScrollPanelInner().let {
		context.enableScissorWithoutTranslation(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat())
	}

	fun paintPages(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, openPage: StoragePageSlot?, openSlots: List<Slot>?, slotOrigin: Point) =
		dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.STORAGE_PAGES) { paintPagesImpl(context, mouseX, mouseY, openPage, openSlots, slotOrigin) }

	private fun paintPagesImpl(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, openPage: StoragePageSlot?, openSlots: List<Slot>?, slotOrigin: Point) {
		val pages = rememberedPages()
		val layout = pageLayout(pages)
		val panel = getScrollPanelInner()
		var tooltip: ItemStack? = null
		clipToPanel(context)
		try {
			for ((page, box) in pages.zip(layout.boxes)) {
				val open = page.slot == openPage

				if (!open && !StorageOverlayGeometry.intersectsPanel(box, panel)) continue
				val hit = paintPageBox(context, page, box, open, if (open) openSlots else null, slotOrigin, mouseX, mouseY)
				if (hit != null && panel.contains(mouseX, mouseY)) tooltip = hit
			}
		} finally {
			context.disableScissor()
		}
		if (tooltip != null && StorageOverlay.TConfig.showInactivePageTooltips) showTooltip(context, tooltip, mouseX, mouseY)
	}

	@OptIn(NeedsComponents::class)
	private fun paintPageBox(
		context: GuiGraphicsExtractor, page: StorageData.RememberedPage, box: Rectangle, open: Boolean,
		realSlots: List<Slot>?, slotOrigin: Point, mouseX: Int, mouseY: Int,
	): ItemStack? {
		val style = StorageOverlay.TConfig.textureStyle
		val lineHeight = font.lineHeight
		val inventory = page.inventory
		val titleX = StorageOverlayGeometry.Page.titleX(box.x)
		val titleY = StorageOverlayGeometry.Page.titleY(box.y)
		val liveRows = if (open && realSlots != null) realSlots.size / 9 else null
		if (inventory == null && liveRows == null) {
			context.guiSprite(style.sprite("upper_background"), box.x, box.y, box.width, box.height)
			context.text(font, "Not cached yet: click to open", titleX, titleY, pageTitleColour(), false)
			return null
		}
		val rows = liveRows ?: inventory!!.rows
		if (open && StorageOverlay.TConfig.outlineActiveStoragePage) {
			val edge = StorageOverlayGeometry.Page.outline(box.x, box.y, rows, lineHeight)
			context.outlineBox(edge.x, edge.y, edge.width, edge.height,
				StorageOverlay.TConfig.outlineActiveStoragePageColour.getEffectiveColourRGB())
		}
		val label = StorageOverlay.Data.data.labelFor(page.slot)
		context.text(font, label, titleX, titleY, if (open) 0xFFFFFF55.toInt() else pageTitleColour(), false)
		val grid = StorageOverlayGeometry.Page.grid(box.x, box.y, rows, lineHeight)
		for (row in 0 until rows)
			context.guiSprite(style.sprite("storage_row"), grid.x, grid.y + row * StorageOverlayGeometry.SLOT, grid.width, StorageOverlayGeometry.SLOT)
		if (realSlots != null) {
			for ((index, slot) in realSlots.withIndex()) {
				slot.x = StorageOverlayGeometry.Page.slotX(box.x, index) - slotOrigin.x
				slot.y = StorageOverlayGeometry.Page.slotY(box.y, index, lineHeight) - slotOrigin.y
			}
			return null
		}
		var under: ItemStack? = null
		val panel = getScrollPanelInner()
		for ((index, kept) in inventory!!.stacks.withIndex()) {
			val x = StorageOverlayGeometry.Page.slotX(box.x, index)
			val y = StorageOverlayGeometry.Page.slotY(box.y, index, lineHeight)
			if (y + 16 < panel.y || y > panel.y + panel.height) continue
			val stack = kept.current()
			val fake = FakeSlot(stack, x, y)
			dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.SLOT_LISTENERS) { SlotRenderEvents.Before.publish(SlotRenderEvents.Before(context, fake)) }
			if (!stack.isEmpty) dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.ITEMS) {
				context.item(stack, x, y)
				context.itemDecorations(font, stack, x, y)
			}
			dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.SLOT_LISTENERS) { SlotRenderEvents.After.publish(SlotRenderEvents.After(context, fake)) }
			if (!stack.isEmpty && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) under = stack
		}
		return under
	}

	private fun showTooltip(context: GuiGraphicsExtractor, stack: ItemStack, mouseX: Int, mouseY: Int) {
		val shown = runCatching { context.setTooltipForNextFrame(font, stack, mouseX, mouseY) }
		if (shown.isFailure) context.setComponentTooltipForNextFrame(font, listOf(
			Component.literal("${ChatFormatting.RED}Error Getting Tooltip!"),
			Component.literal("${ChatFormatting.YELLOW}Open page to fix${ChatFormatting.RESET}"),
		), mouseX, mouseY)
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {

		if (StorageBackArrow.hit(geometry.frameX, geometry.frameY, click.x, click.y)) {
			if (dev.ashtonite365.apex.features.menus.support.MenuButtons.isLeft(click.button()))
				StorageBackArrow.press(null, clickSlot = {}, close = { onClose() })
			return true
		}
		return pressOverlay(click, null) || super.mouseClicked(click, doubled)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
		moveScroll(sharedScroll + StorageOverlay.adjustScrollSpeed(verticalAmount).toFloat())
		return true
	}

	private val draggingKnob get() = guiContext.focusedElement === knobHolder

	override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
		if (draggingKnob) {
			sharedScroll = geometry.scrollForKnobTop(click.y() - knobGrab, contentHeight())
			return true
		}
		return mouseIntoComponent(controls, controlsArea(), click.x().toInt(), click.y().toInt(),
			MouseEvent.Move(offsetX.toFloat(), offsetY.toFloat()))
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean {
		if (draggingKnob) {
			guiContext.setFocusedElement(null)
			return true
		}
		val release = MouseEvent.Click(click.button(), false)
		return mouseIntoComponent(controls, controlsArea(), click.x().toInt(), click.y().toInt(), release)
	}

	fun pressOverlay(click: MouseButtonEvent, activePage: StoragePageSlot?): Boolean {
		val x = click.x()
		val y = click.y()
		val panel = getScrollPanelInner()
		val bar = getScrollBarRect()
		StorageOverlay.debugLog {
			"overview click at ($x, $y) button=${click.button()} standalone=${this === Client.screen} panel=$panel scrollbar=$bar scroll=$sharedScroll"
		}
		guiContext.setFocusedElement(null)
		val area = controlsArea()
		if (area.contains(x, y)) {
			val press = MouseEvent.Click(click.button(), true)
			mouseIntoComponent(controls, area, x.toInt(), y.toInt(), press)
			return true
		}
		if (panel.contains(x, y)) {
			val pages = rememberedPages()
			val rects = pages.zip(pageLayout(pages).boxes).map { (page, box) ->
				StorageClickLogic.PageRect(box.x, box.y, box.width, box.height, page.slot)
			}

			val target = StorageClickLogic.pageToOpen(rects, intArrayOf(panel.x, panel.y, panel.width, panel.height), x, y, click.button(), activePage)
			if (target != null) {
				StorageOverlay.debugLog { "click hit page $target activePage=$activePage" }
				MenuPress.acted()
				target.navigateTo()
				return true
			}
			StorageOverlay.debugLog { "click in panel did not open a page (active page or no page hit), passing to slots" }
			return false
		}
		if (bar.contains(x, y)) {
			val content = contentHeight()
			var top = geometry.knobTop(sharedScroll, content).toDouble()
			if (y < top || y >= top + StorageOverlayGeometry.KNOB_HEIGHT) {
				sharedScroll = geometry.scrollForKnobTop(y - StorageOverlayGeometry.KNOB_HEIGHT / 2.0, content)
				top = geometry.knobTop(sharedScroll, content).toDouble()
			}
			knobGrab = y - top
			guiContext.setFocusedElement(knobHolder)
			return true
		}
		return false
	}

	private fun keyToControls(event: KeyboardEvent): Boolean =
		guiContext.focusedElement != null && guiContext.focusedElement !== knobHolder && keyIntoComponent(controls, controlsArea(), event)

	override fun charTyped(input: CharacterEvent): Boolean =
		keyToControls(KeyboardEvent.CharTyped(input.codepoint().toChar())) || super.charTyped(input)

	override fun keyReleased(input: KeyEvent): Boolean =
		keyToControls(KeyboardEvent.KeyPressed(input.key(), input.keycode(), false)) || super.keyReleased(input)

	override fun keyPressed(input: KeyEvent): Boolean =
		keyToControls(KeyboardEvent.KeyPressed(input.key(), input.keycode(), true)) || super.keyPressed(input)

	override fun shouldCloseOnEsc(): Boolean = this === Client.screen

	fun matchesSearch(stack: ItemStack, query: String): Boolean = Shared.matchesSearch(stack, query)

	fun getBounds(): List<Rectangle> = geometry.bounds()
}
