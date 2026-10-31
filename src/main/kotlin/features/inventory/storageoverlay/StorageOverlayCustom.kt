package dev.ashtonite365.apex.features.inventory.storageoverlay

import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.support.MenuButtons
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.util.accessors.castAccessor
import dev.ashtonite365.apex.util.customgui.CustomGui
import dev.ashtonite365.apex.util.stackUnderCursor

class StorageOverlayCustom(val handler: StorageMenuHandle, val screen: ContainerScreen, val overview: StorageOverlayScreen) :
	CustomGui() {

	override val textInputFocused get() = overview.textInputFocused

	private val openPage: StoragePageSlot? get() = (handler as? StorageMenuHandle.Page)?.storagePageSlot
	private val onPage: Boolean get() = handler is StorageMenuHandle.Page

	private fun syncLivePage() = overview.setLivePage(openPage, if (onPage) (screen.menu.rowCount - 1).coerceAtLeast(0) else 0)

	private val origin: Pair<Int, Int> get() = overview.geometry.frameX to overview.geometry.frameY

	private fun navRect(nav: StorageNavButtons.Nav) = StorageNavButtons.rect(origin.first, origin.second, nav)

	override fun onVoluntaryExit(): Boolean = overview.markLeaving().let { super.onVoluntaryExit() }

	override fun getBounds(): List<Rectangle> = overview.getBounds() + listOfNotNull(navColumn().takeIf { onPage }) +
		StorageBackArrow.rect(origin.first, origin.second).let { Rectangle(it[0], it[1], it[2], it[3]) }

	private fun navColumn(): Rectangle {
		val top = navRect(StorageNavButtons.Nav.entries.first())
		val bottom = navRect(StorageNavButtons.Nav.entries.last())
		return Rectangle(top[0], top[1], top[2], bottom[1] + bottom[3] - top[1])
	}

	private fun liveChestNames(): List<String?> = screen.menu.slots.map { slot ->
		if (slot.container is Inventory || !slot.hasItem()) null
		else ChatFormatting.stripFormatting(slot.item.hoverName.string)
	}

	private fun unlockedPages(): Collection<StoragePageSlot> = StorageOverlay.Data.data.knownPages.keys

	private fun enabled(nav: StorageNavButtons.Nav, names: List<String?>): Boolean =
		if (nav == StorageNavButtons.Nav.BACK) nav in StorageNavButtons.resolve(names)
		else StorageNavButtons.backpackTarget(nav, openPage, unlockedPages()) != null

	private fun backArrowClick(click: MouseButtonEvent): Boolean {
		if (!StorageBackArrow.hit(origin.first, origin.second, click.x, click.y)) return false
		if (!MenuButtons.isLeft(click.button())) return true
		MenuPress.acted()
		val player = Minecraft.getInstance().player
		val action = StorageBackArrow.press(StorageBackChain.items(screen.menu), clickSlot = { target ->

			if (player != null) Minecraft.getInstance().gameMode?.handleContainerInput(
				screen.menu.containerId, target, 0, ContainerInput.PICKUP, player)
		}, close = { overview.markLeaving(); screen.onClose() })
		StorageOverlay.debugLog { "back arrow at (${click.x}, ${click.y}) -> $action" }
		return true
	}

	private fun navClick(click: MouseButtonEvent): Boolean {
		if (backArrowClick(click)) return true
		if (!onPage) return false
		val nav = StorageNavButtons.hit(origin.first, origin.second, click.x, click.y) ?: return false

		if (!MenuButtons.isLeft(click.button())) return true
		MenuPress.acted()
		if (nav == StorageNavButtons.Nav.BACK) {

			val player = Minecraft.getInstance().player ?: return true
			val sent = StorageBackChain.onBack(StorageBackChain.items(screen.menu)) { target ->

				Minecraft.getInstance().gameMode?.handleContainerInput(
					screen.menu.containerId, target, 0, ContainerInput.PICKUP, player)
			}
			StorageOverlay.debugLog { "nav $nav at (${click.x}, ${click.y}) back sent=$sent" }
		} else {
			val page = StorageNavButtons.backpackTarget(nav, openPage, unlockedPages())
			StorageOverlay.debugLog { "nav $nav from $openPage -> $page" }
			page?.navigateTo()
		}
		return true
	}

	private fun drawNav(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val names = liveChestNames()
		val font = Minecraft.getInstance().font
		for (nav in StorageNavButtons.Nav.entries) {
			if (nav == StorageNavButtons.Nav.BACK) continue
			val r = navRect(nav)
			val enabled = enabled(nav, names)
			val hover = MenuTheme.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])
			val state = MenuTheme.state(hover, enabled = enabled)
			val palette = if (enabled) MenuTheme.Palette(text = nav.colour) else MenuTheme.Palette.DEFAULT
			MenuTheme.button(ctx, font, r[0], r[1], r[2], r[3], nav.label, state, palette = palette)
			if (hover) ctx.setComponentTooltipForNextFrame(font,
				listOf(Component.literal(nav.tooltip + if (enabled) "" else " (not available here)")), mouseX, mouseY)
		}
	}

	private fun placement(slot: Slot) = StorageClickLogic.placeSlot(slot.container is Inventory, slot.containerSlot, onPage)

	private fun insidePanel(slot: Slot) =
		slot.container !is Inventory && placement(slot) !is StorageClickLogic.Placement.Selector

	override fun beforeSlotRender(context: GuiGraphicsExtractor, slot: Slot) { if (insidePanel(slot)) overview.clipToPanel(context) }

	override fun afterSlotRender(context: GuiGraphicsExtractor, slot: Slot) { if (insidePanel(slot)) context.disableScissor() }

	override fun onInit() {
		syncLivePage()
		overview.init(screen.width, screen.height)
		val accessor = screen.castAccessor()
		accessor.x_Apex = overview.geometry.frameX
		accessor.y_Apex = overview.geometry.frameY

	}

	override fun isPointOverSlot(slot: Slot, xOffset: Int, yOffset: Int, pointX: Double, pointY: Double): Boolean =
		super.isPointOverSlot(slot, xOffset, yOffset, pointX, pointY) &&
			(!insidePanel(slot) || overview.getScrollPanelInner().contains(pointX, pointY))

	private val gesture = InventoryPanel.Gesture()
	private fun overPlayerSlot(x: Double, y: Double) =
		InventoryPanel.cellAtPositions((0 until 36).map { overview.getPlayerInventorySlotPosition(it) }, x, y) != null

	override fun shouldDrawForeground() = false

	private fun outside(x: Double, y: Double) = overview.getBounds().none { it.contains(x, y) }

	override fun mouseReleased(click: MouseButtonEvent) = !outside(click.x, click.y) && overview.mouseReleased(click)

	override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double) = overview.mouseDragged(click, offsetX, offsetY)

	override fun keyPressed(input: KeyEvent) = overview.keyPressed(input)

	override fun keyReleased(input: KeyEvent) = overview.keyReleased(input)

	override fun charTyped(input: CharacterEvent) = overview.charTyped(input)

	override fun mouseClick(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val activePage: StoragePageSlot? = openPage
		syncLivePage()
		if (navClick(click)) return true
		if (outside(click.x, click.y)) {
			StorageOverlay.debugLog { "custom click at (${click.x}, ${click.y}) button=${click.button()} outside overlay bounds ${overview.getBounds()}, passing on" }
			return false
		}
		val handled = overview.pressOverlay(click, activePage)
		StorageOverlay.debugLog { "custom click at (${click.x}, ${click.y}) button=${click.button()} handler=${handler::class.simpleName} activePage=$activePage screen=${screen.width}x${screen.height} guiScale=${Minecraft.getInstance().window.guiScale} handled=$handled" }
		return handled
	}

	override fun render(drawContext: GuiGraphicsExtractor, delta: Float, mouseX: Int, mouseY: Int) {
		val accessor = screen.castAccessor()

		val pageSlots = screen.menu.slots.subList(9, screen.menu.rowCount * 9)
		syncLivePage()
		with(overview) {
			paintFrames(drawContext)
			paintPages(drawContext, mouseX, mouseY, openPage, pageSlots, Point(accessor.x_Apex, accessor.y_Apex))
		}
		if (onPage) drawNav(drawContext, mouseX, mouseY)
		overview.paintBackArrow(drawContext, mouseX, mouseY)
		overview.paintScrollBar(drawContext)
		overview.paintControls(drawContext, mouseX, mouseY)
	}

	override fun moveSlot(slot: Slot) {
		val accessor = screen.castAccessor()

		val target = (placement(slot) as? StorageClickLogic.Placement.Player)?.let { overview.getPlayerInventorySlotPosition(it.index) }
		slot.x = target?.let { it.first - accessor.x_Apex } ?: HIDDEN
		slot.y = target?.let { it.second - accessor.y_Apex } ?: HIDDEN
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean = when {
		outside(mouseX, mouseY) -> false
		gesture.scroll(overPlayerSlot(mouseX, mouseY)) -> false
		StorageOverlay.TConfig.itemsBlockScrolling && screen.stackUnderCursor != null -> false
		else -> overview.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
	}

	private companion object {

		const val HIDDEN = -100_000
	}
}
