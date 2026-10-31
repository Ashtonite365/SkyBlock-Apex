// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import java.util.Collections
import java.util.WeakHashMap
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.ContainerInput
import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.util.customgui.customGui

object MenuInputGuard {

	var gestureActive = false; private set

	var blocked = 0; private set

	private val routed: MutableSet<Screen> = Collections.newSetFromMap(WeakHashMap())

	fun begin() { gestureActive = true }
	fun end() { gestureActive = false }

	fun rawPress() { gestureActive = false }

	internal fun markRouted(screen: Screen) { routed += screen }
	fun isRouted(screen: Screen) = screen in routed

	fun consumeRelease(screen: Screen): Boolean = consumeReleaseOn(isRouted(screen))

	fun consumeReleaseOn(routedScreen: Boolean): Boolean {
		if (!gestureActive || routedScreen) return false
		end(); return true
	}

	fun consumeDrag(): Boolean = gestureActive

	fun allowSlotClick(realSlot: ((Int) -> Boolean)?, slotId: Int, @Suppress("UNUSED_PARAMETER") input: ContainerInput): Boolean {
		if (gestureActive) return false
		if (realSlot == null) return true
		if (slotId < 0) return true
		return realSlot(slotId)
	}

	fun allowVanillaSlotClick(screen: AbstractContainerScreen<*>, slotId: Int, input: ContainerInput, buttonNum: Int): Boolean {
		val front = screen.customGui as? MenuFront
		val ok = allowSlotClick(front?.let { f -> { i: Int -> f.isRealSlot(i) } }, slotId, input)
		if (!ok) {
			blocked++
			MenuLog.blockedClick(screen.title.string, slotId, buttonNum, input.name, if (gestureActive) "apex press held" else "behind the menu")
		}
		return ok
	}

	fun resetForTests() { gestureActive = false; blocked = 0; routed.clear() }
}

interface MenuInputHost {
	val hiddenNow: Boolean
	fun overShowButton(mx: Double, my: Double): Boolean

	fun showMenu()
	fun overPanelCell(mx: Double, my: Double): Boolean
	fun inView(mx: Double, my: Double): Boolean
	fun viewPress(click: MouseButtonEvent)
	fun viewRelease(click: MouseButtonEvent)
	fun viewScroll(mx: Double, my: Double, amount: Double)

	fun inventoryKeyClose(click: MouseButtonEvent): Boolean = false
}

class MenuInputRouter(private val host: MenuInputHost) {

	val gesture = InventoryPanel.Gesture()

	fun press(click: MouseButtonEvent): Boolean = MenuPress.press {

		if (!host.hiddenNow && host.inventoryKeyClose(click)) { MenuInputGuard.begin(); false }
		else if (host.hiddenNow) {
			if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && host.overShowButton(click.x(), click.y())) {
				MenuPress.acted(); host.showMenu(); MenuInputGuard.begin(); false
			} else true
		} else if (MenuChainRunner.active && host.overPanelCell(click.x(), click.y())) {

			MenuChainRunner.cancel("cancelled"); gesture.press(false); MenuInputGuard.begin(); false
		} else if (gesture.press(host.overPanelCell(click.x(), click.y()))) true
		else if (host.inView(click.x(), click.y())) { host.viewPress(click); MenuInputGuard.begin(); false }
		else true
	}

	fun release(click: MouseButtonEvent): Boolean {
		val toVanilla = gesture.release()

		if (!host.hiddenNow && !toVanilla) host.viewRelease(click)
		if (MenuInputGuard.gestureActive) { MenuInputGuard.end(); return false }
		return host.hiddenNow || toVanilla
	}

	fun drag(): Boolean = !MenuInputGuard.gestureActive && (host.hiddenNow || gesture.drag())

	fun scroll(mx: Double, my: Double, amount: Double): Boolean {
		if (host.hiddenNow || !host.inView(mx, my)) return true
		if (!MenuInputGuard.gestureActive && gesture.scroll(host.overPanelCell(mx, my))) return true
		host.viewScroll(mx, my, amount); return false
	}
}
