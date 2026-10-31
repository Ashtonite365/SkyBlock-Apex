// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import me.shedaniel.math.Rectangle
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.InventoryKeyClose
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.util.customgui.CustomGui
import dev.ashtonite365.apex.util.customgui.customGui

interface MenuView {
	val x: Int; val y: Int; val w: Int; val h: Int
	fun layout(x: Int, y: Int, w: Int, h: Int)
	fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float)
	fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean

	fun mouseReleased(click: MouseButtonEvent): Boolean = false
	fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean
	fun keyPressed(input: KeyEvent): Boolean
	fun charTyped(input: CharacterEvent): Boolean

	val textFocused: Boolean get() = false

	fun interceptClose(): Boolean = false

	fun attached(session: MenuSession) {}
	fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h

	fun hoveredRealSlot(): Slot? = null
}

class MenuSession(
	val screen: AbstractContainerScreen<*>,
	val view: MenuView,
	val showLabel: () -> String,
	val toggleKey: (KeyEvent) -> Boolean,
	val isHidden: () -> Boolean,
	val setHiddenState: (Boolean) -> Unit,
	val onEscape: () -> Unit = {},

	val onUserHide: () -> Unit = {},
	val onRemove: () -> Unit = {},

	val afterRender: (GuiGraphicsExtractor, Int, Int) -> Unit = { _, _, _ -> },

	val menuId: String = "menu",
) {
	private val failover = MenuFailover { reason, e ->
		MenuLog.fallback(menuId, screen.title.string, reason, e)
		runCatching { setHidden(true) }
		dev.ashtonite365.apex.features.menus.support.ApexFallback.handle(screen, menuId, reason)
	}

	val failed get() = failover.failed

	var panelOrigin: Pair<Int, Int>? = null

	val realCells = mutableMapOf<Int, Pair<Int, Int>>()

	var cellScale = 1f

	val cellScales = mutableMapOf<Int, Float>()
	fun scaleOf(index: Int) = cellScales[index] ?: cellScale

	val standInCells = mutableMapOf<Int, Pair<Int, Int>>()
	internal fun overPanelCell(mx: Double, my: Double) = InventoryPanel.cellAt(panelOrigin, screen.menu.slots.size, mx, my, realCells, cellScale, ::scaleOf) != null

	var retired = false
		private set
	fun retire() { retired = true }

	val chrome = MenuChrome()

	fun <R> guard(where: String, orElse: R, block: () -> R): R = failover.guard(where, orElse, block)

	fun fallback(reason: String, e: Throwable? = null) = failover.fail(reason, e)

	fun setHidden(value: Boolean) {
		val v = value || failed
		setHiddenState(v)
		screen.customGui = if (v) null else MenuFront(this)
	}

	private val showBtn = intArrayOf(4, 4, 96, 16)
	private fun overShowBtn(mx: Double, my: Double) = mx >= showBtn[0] && mx < showBtn[0] + showBtn[2] && my >= showBtn[1] && my < showBtn[1] + showBtn[3]

	init { DropdownInput.register(DropdownLayer.claimer) }

	fun drawLate(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
		if (!retired && !isHidden()) guard("chrome", Unit) { chrome.lateOverlay(ctx, view, mx, my) }
	}

	internal fun install() {
		setHidden(isHidden())
		ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
			if (retired) return@AfterExtract

			if (isHidden()) MenuTheme.button(ctx, Client.font, showBtn[0], showBtn[1], showBtn[2], showBtn[3], showLabel(),
				MenuTheme.state(overShowBtn(mx.toDouble(), my.toDouble())), textX = showBtn[0] + 5)
			afterRender(ctx, mx, my)

		})

		MenuInputGuard.markRouted(screen)

		ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click -> MenuPress.press { if (retired) true else if (chromeTakes(click)) false else router.press(click) } })
		ScreenMouseEvents.allowMouseRelease(screen).register(ScreenMouseEvents.AllowMouseRelease { _, click -> if (retired) true else { chrome.release(); router.release(click) } })
		ScreenMouseEvents.allowMouseDrag(screen).register(ScreenMouseEvents.AllowMouseDrag { _, _, _, _ -> if (retired) true else router.drag() })
		ScreenMouseEvents.allowMouseScroll(screen).register(ScreenMouseEvents.AllowMouseScroll { _, mx, my, _, v -> if (retired) true else router.scroll(mx, my, v) })
		ScreenKeyboardEvents.allowKeyPress(screen).register(ScreenKeyboardEvents.AllowKeyPress { _, key ->
			if (retired) return@AllowKeyPress true
			if (!guard("textFocused", false) { view.textFocused } && toggleKey(key)) {
				val hide = !isHidden()
				if (hide) onUserHide()
				setHidden(hide); return@AllowKeyPress false
			}
			if (isHidden()) return@AllowKeyPress true

			if (!guard("textFocused", false) { view.textFocused }
				&& dev.ashtonite365.apex.features.inventory.SlotLocking.handleLockKeyFromApexMenu(screen, key, guard("hoveredRealSlot", null) { view.hoveredRealSlot() })) return@AllowKeyPress false

			if (InventoryKeyClose.handleMenuKey(screen, key, guard("textFocused", true) { view.textFocused },
					{ guard("interceptClose", false) { view.interceptClose() } }, onEscape)) return@AllowKeyPress false
			if (guard("keyPressed", false) { view.keyPressed(key) }) return@AllowKeyPress false
			if (isHidden()) return@AllowKeyPress true

			if (key.input() == InputConstants.KEY_ESCAPE) onEscape()
			key.input() == InputConstants.KEY_ESCAPE
		})
		ScreenKeyboardEvents.allowCharType(screen).register(ScreenKeyboardEvents.AllowCharType { _, ch ->
			if (retired || isHidden()) true else { guard("charTyped", false) { view.charTyped(ch) }; false }
		})
		ScreenEvents.remove(screen).register(ScreenEvents.Remove {
			if (retired) return@Remove
			if (!isHidden()) guard("chromeClose", Unit) { chrome.closed(view) }
			onRemove()
		})
	}

	private fun chromeTakes(click: MouseButtonEvent) = !isHidden() && guard("chrome", false) { chrome.press(view, click.x(), click.y()) }

	private val inputHost = object : MenuInputHost {
		override val hiddenNow get() = isHidden()
		override fun overShowButton(mx: Double, my: Double) = overShowBtn(mx, my)
		override fun showMenu() = setHidden(false)
		override fun overPanelCell(mx: Double, my: Double) = guard("panel", false) { this@MenuSession.overPanelCell(mx, my) }
		override fun inView(mx: Double, my: Double) = guard("contains", false) { view.contains(mx, my) }
		override fun viewPress(click: MouseButtonEvent) { guard("mouseClicked", false) { view.mouseClicked(click, false) } }
		override fun viewRelease(click: MouseButtonEvent) { guard("mouseReleased", false) { view.mouseReleased(click) } }
		override fun viewScroll(mx: Double, my: Double, amount: Double) { guard("mouseScrolled", false) { view.mouseScrolled(mx, my, amount) } }

		override fun inventoryKeyClose(click: MouseButtonEvent) = InventoryKeyClose.handleMenuClick(screen, click, guard("textFocused", true) { view.textFocused },
			{ guard("interceptClose", false) { view.interceptClose() } }, onEscape)
	}

	internal val router = MenuInputRouter(inputHost)
}

class MenuFront(val session: MenuSession) : CustomGui() {
	private val view get() = session.view
	override fun getBounds() = listOf(Rectangle(view.x, view.y, view.w, view.h))

	override fun sidePanelAnchor(): Rectangle = runCatching {
		val g = session.chrome.geometry(view)
		dev.ashtonite365.apex.util.render.SidePanel.union(listOf(Rectangle(view.x, view.y, view.w, view.h), Rectangle(g.dropX, g.y, view.x + view.w - g.dropX, g.h)))
	}.getOrNull() ?: Rectangle(view.x, view.y, view.w, view.h)
	private val windowSlots get() = session.screen.menu.slots.size
	override fun moveSlot(slot: Slot) { slot.x = -100000; slot.y = -100000 }

	override fun moveSlot(slot: Slot, leftPos: Int, topPos: Int) {
		val p = InventoryPanel.slotPlacement(slot.index, windowSlots, session.panelOrigin, leftPos, topPos, session.realCells)
		if (p == null) moveSlot(slot) else { slot.x = p.first; slot.y = p.second }
	}
	override fun shouldDrawForeground() = false
	override val textInputFocused get() = session.guard("textFocused", true) { view.textFocused }

	fun isRealSlot(index: Int) = InventoryPanel.slotPlacement(index, windowSlots, session.panelOrigin, 0, 0, session.realCells) != null

	override fun isPointOverSlot(slot: Slot, xOffset: Int, yOffset: Int, pointX: Double, pointY: Double) =
		InventoryPanel.hitsSlot(slot.index, windowSlots, session.panelOrigin, slot.x + xOffset, slot.y + yOffset, pointX, pointY, session.realCells, session.scaleOf(slot.index))
	override fun isPointWithinBounds(x: Int, y: Int, width: Int, height: Int, pointX: Double, pointY: Double) = false

	private var scaledPush = false
	private fun pushScaled(ctx: GuiGraphicsExtractor, slot: Slot?) {
		val s = if (slot == null) session.cellScale else session.scaleOf(slot.index)
		scaledPush = slot != null && s != 1f && slot.index in session.realCells
		if (!scaledPush || slot == null) return
		ctx.pose().pushMatrix()
		ctx.pose().translate(slot.x.toFloat(), slot.y.toFloat()); ctx.pose().scale(s, s); ctx.pose().translate(-slot.x.toFloat(), -slot.y.toFloat())
	}
	private fun popScaled(ctx: GuiGraphicsExtractor) { if (scaledPush) { ctx.pose().popMatrix(); scaledPush = false } }
	override fun beforeSlotRender(context: GuiGraphicsExtractor, slot: Slot) = pushScaled(context, slot)
	override fun afterSlotRender(context: GuiGraphicsExtractor, slot: Slot) = popScaled(context)
	override fun beforeSlotHighlight(context: GuiGraphicsExtractor, slot: Slot?) = pushScaled(context, slot)
	override fun afterSlotHighlight(context: GuiGraphicsExtractor, slot: Slot?) = popScaled(context)
	override fun isClickOutsideBounds(mouseX: Double, mouseY: Double) = false
	override fun render(drawContext: GuiGraphicsExtractor, delta: Float, mouseX: Int, mouseY: Int) {

		session.panelOrigin = null
		session.realCells.clear()
		session.cellScale = 1f
		session.cellScales.clear()
		session.standInCells.clear()
		session.guard("chrome", Unit) { session.chrome.backdrop(drawContext, session.screen.width, session.screen.height, session.screen.isInGameUi) }
		session.guard("render", Unit) { view.render(drawContext, mouseX, mouseY, delta) }
		session.guard("chrome", Unit) { session.chrome.overlay(drawContext, view, mouseX, mouseY) }
	}

	override fun slotLinkBoxes(screen: net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) =
		dev.ashtonite365.apex.features.inventory.SlotLinkOverlay.forMenuSession(
			session.panelOrigin, session.realCells, session.cellScale, session.standInCells, windowSlots)
}

class MenuFailover(private val onFail: (reason: String, error: Throwable?) -> Unit) {
	var failed = false; private set
	fun fail(reason: String, e: Throwable? = null) {
		if (failed) return
		failed = true
		onFail(reason, e)
	}
	fun <R> guard(where: String, orElse: R, block: () -> R): R = try {
		if (failed) orElse else block()
	} catch (e: Throwable) {
		fail("$where threw ${e.javaClass.simpleName}: ${e.message}", e); orElse
	}
}

object MenuHost {

	fun attach(session: MenuSession, bounds: MenuBounds) {
		session.guard("layout", Unit) { session.view.layout(bounds.x, bounds.y, bounds.w, bounds.h) }
		session.guard("attached", Unit) { session.view.attached(session) }
		session.install()
	}
}
