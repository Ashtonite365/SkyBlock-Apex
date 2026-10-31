// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.menus

import java.util.IdentityHashMap
import kotlin.jvm.optionals.getOrNull
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.misc.SolidMenus
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.customgui.customGui

object StaticMenus {
	const val TIMEOUT_MS = 1000L
	const val CAP_MS = 2000L

	var clock: () -> Long = { System.currentTimeMillis() }

	var enabledSource: () -> Boolean = { SolidMenus.TConfig.staticAfterClick }

	var screenCheck: (AbstractContainerMenu) -> Boolean = ::isOurOpenMenu

	val enabled: Boolean get() = try { enabledSource() } catch (_: Throwable) { false }

	class Kept(val original: ItemStack, val count: Int, val copy: ItemStack)

	class Frozen(val menu: AbstractContainerMenu, val start: Long) {
		val slots = IdentityHashMap<Slot, Kept>()
		var deadline = start + TIMEOUT_MS

		var answered = false
	}

	class Held(val screen: Any, var pendingId: Int, start: Long, cap: Long) {
		var deadline = minOf(start + TIMEOUT_MS, cap)

		var filled = false
	}

	var frozen: Frozen? = null
		private set
	var held: Held? = null
		private set

	@JvmField var drawing: Frozen? = null
	private val drawStack = ArrayList<Frozen?>()
	private var clicking = 0
	private var inViewSend = false
	private var lastArmedMenu: AbstractContainerMenu? = null
	private var lastArmedAt = Long.MIN_VALUE
	private var expectUntil = Long.MIN_VALUE

	fun resetForTests() {
		clock = { System.currentTimeMillis() }; enabledSource = { SolidMenus.TConfig.staticAfterClick }; screenCheck = ::isOurOpenMenu
		frozen = null; held = null; drawing = null; drawStack.clear(); clicking = 0; inViewSend = false
		lastArmedMenu = null; lastArmedAt = Long.MIN_VALUE; expectUntil = Long.MIN_VALUE
	}

	fun isRealItem(stack: ItemStack): Boolean =
		stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString("id")?.getOrNull()?.isNotBlank() == true

	fun shouldArm(menu: AbstractContainerMenu, slotIndex: Int, viaView: Boolean, attributed: Boolean): Boolean {
		if (!enabled) return false
		if (viaView && !attributed) return false
		val slot = menu.slots.getOrNull(slotIndex) ?: return false
		if (slot.container is Inventory) return false
		if (!menu.carried.isEmpty) return false
		val stack = slot.item
		if (stack.isEmpty) return false
		if (!viaView && isRealItem(stack)) return false
		return screenCheck(menu)
	}

	private fun arm(menu: AbstractContainerMenu) {
		val now = clock()
		val f = frozen
		if (f != null && f.menu === menu && !f.answered) {
			f.deadline = minOf(now + TIMEOUT_MS, f.start + CAP_MS)
		} else {
			val n = Frozen(menu, now)
			for (s in menu.slots) {
				if (s.container is Inventory) continue
				val st = s.item
				n.slots[s] = Kept(st, st.count, st.copy())
			}
			frozen = n
		}
		lastArmedMenu = menu; lastArmedAt = now
	}

	private inline fun safely(block: () -> Unit) {
		try { block() } catch (_: Throwable) { releaseAll() }
	}

	inline fun viewSend(menu: AbstractContainerMenu, slot: Slot, send: () -> Unit) {
		beforeViewSend(menu, slot)
		try { send() } finally { afterViewSend() }
	}

	@PublishedApi internal fun beforeViewSend(menu: AbstractContainerMenu, slot: Slot) {
		safely { if (shouldArm(menu, slot.index, true, MenuPress.attributed)) arm(menu) }
		inViewSend = true
	}

	@PublishedApi internal fun afterViewSend() { inViewSend = false }

	@JvmStatic fun beforeClick(containerId: Int, slotIndex: Int) {
		if (!inViewSend) safely {
			val menu = try { Client.player?.containerMenu } catch (_: Throwable) { null }
			if (menu != null && menu.containerId == containerId && shouldArm(menu, slotIndex, false, true)) arm(menu)
		}
		clicking++
	}

	@JvmStatic fun afterClick() { if (clicking > 0) clicking-- }

	fun expectScreen() { if (enabled) expectUntil = clock() + TIMEOUT_MS }

	@JvmStatic fun onContents(containerId: Int) {
		val h = held; val f = frozen
		if (h != null && containerId == h.pendingId) { h.filled = true; return }
		if (f != null && containerId == f.menu.containerId) { if (h == null) releaseFrozen(); return }
		if (containerId != 0 && (f != null || h != null)) releaseAll()
	}

	@JvmStatic fun onSlot(containerId: Int) {
		val h = held; val f = frozen
		if (h != null && containerId == h.pendingId) { h.filled = true; return }
		if (f != null && containerId == f.menu.containerId) { f.answered = true; return }
		if (containerId != 0 && (f != null || h != null)) releaseAll()
	}

	@JvmStatic fun onCursor() { if (held == null) frozen?.answered = true }

	@JvmStatic fun onClose() = releaseAll()

	fun beforeOpen(current: Any?, currentMenu: AbstractContainerMenu?, newId: Int) {
		if (!enabled) { releaseAll(); return }
		val now = clock()
		val h = held
		if (h != null && now <= h.deadline) { h.pendingId = newId; h.filled = false; return }
		val start = when {
			currentMenu != null && frozen?.menu === currentMenu -> frozen!!.start
			currentMenu != null && lastArmedMenu === currentMenu && now - lastArmedAt <= TIMEOUT_MS -> lastArmedAt
			now <= expectUntil -> now
			else -> null
		}
		releaseHold()
		expectUntil = Long.MIN_VALUE
		if (current == null || start == null) { releaseAll(); return }
		held = Held(current, newId, now, start + CAP_MS)
	}

	fun afterOpen(newScreen: Any?, newMenuId: Int?) {
		val h = held ?: return
		if (newScreen == null || newScreen === h.screen || newMenuId != h.pendingId) releaseAll()
	}

	@JvmStatic fun onInput() { if (held != null) releaseAll() }

	fun onTickEnd(screen: Any?, menu: AbstractContainerMenu?) {

		clicking = 0; drawing = null; drawStack.clear()
		if (frozen == null && held == null) return
		if (!enabled) { releaseAll(); return }
		val now = clock()
		held?.let { h ->
			val onIt = screen !== h.screen && menu != null && menu.containerId == h.pendingId
			if (h.filled || now > h.deadline || !onIt) releaseAll()
			return
		}
		frozen?.let { f -> if (f.answered || now > f.deadline || menu !== f.menu) releaseFrozen() }
	}

	fun releaseFrozen() { frozen = null; drawing = null }
	fun releaseHold() { held = null }
	fun releaseAll() { releaseFrozen(); releaseHold(); lastArmedMenu = null }

	fun beginDraw(menu: AbstractContainerMenu?) {
		drawStack.add(drawing)
		val f = frozen
		drawing = if (f != null && menu != null && f.menu === menu && (held != null || clock() <= f.deadline)) f else null
	}

	fun endDraw() { drawing = if (drawStack.isEmpty()) null else drawStack.removeAt(drawStack.size - 1) }

	@JvmStatic fun shownItem(slot: Slot): ItemStack? {
		val d = drawing ?: return null
		if (clicking > 0) return null
		val k = d.slots[slot] ?: return null
		val live = slot.container.getItem(slot.containerSlot)
		return if (live === k.original && live.count == k.count) null else k.copy
	}

	@JvmStatic fun shownCarried(menu: AbstractContainerMenu): ItemStack? {
		val d = drawing ?: return null
		if (clicking > 0 || d.menu !== menu) return null
		return ItemStack.EMPTY
	}

	fun heldFor(screen: Any, menuId: Int): Any? {
		val h = held ?: return null
		if (screen === h.screen || menuId != h.pendingId) return null
		if (clock() > h.deadline) { releaseAll(); return null }
		return h.screen
	}

	private fun isOurOpenMenu(menu: AbstractContainerMenu): Boolean {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return false
		if (screen.menu !== menu || screen is InventoryScreen || screen is CreativeModeInventoryScreen) return false
		if (screen.customGui != null) return true
		return SkyBlockSession.inSkyBlock && SolidMenus.allowedIn(screen.title.string)
	}

	@JvmStatic fun frameStart(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float): Boolean {
		val menu = (screen as? AbstractContainerScreen<*>)?.menu
		if (menu != null && held != null) {
			val old = heldFor(screen, menu.containerId) as? Screen
			if (old != null) {
				try {
					old.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a)
					return true
				} catch (_: Throwable) {
					releaseAll()
				}
			}
		}
		beginDraw(menu)
		return false
	}

	@JvmStatic fun frameEnd() = endDraw()

	@JvmStatic fun beforeOpenPacket(newId: Int) = safely {
		val s = Client.screen
		beforeOpen(s, (s as? AbstractContainerScreen<*>)?.menu, newId)
	}

	@JvmStatic fun afterOpenPacket() = safely {
		val s = Client.screen
		afterOpen(s, (s as? AbstractContainerScreen<*>)?.menu?.containerId)
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ClientTickEvents.END_CLIENT_TICK.register { _ ->
			safely { val s = Client.screen; onTickEnd(s, (s as? AbstractContainerScreen<*>)?.menu) }
		}
	}
}
