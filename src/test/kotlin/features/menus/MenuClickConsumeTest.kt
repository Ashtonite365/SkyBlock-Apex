// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.features.menus.MenuInputGuard
import dev.ashtonite365.apex.features.menus.MenuInputHost
import dev.ashtonite365.apex.features.menus.MenuInputRouter
import dev.ashtonite365.apex.features.menus.MenuPress

@ExtendWith(AutoBootstrapExtension::class)
class MenuClickConsumeTest {

	private class FakeMenu : AbstractContainerMenu(null, 7) {
		val box = SimpleContainer(90)
		init { for (i in 0 until 90) addSlot(Slot(box, i, 0, 0)) }
		override fun quickMoveStack(player: Player, i: Int) = ItemStack.EMPTY
		override fun stillValid(player: Player) = true
	}

	private val menu = FakeMenu()

	data class Sent(val containerId: Int, val stateId: Int, val slot: Int, val button: Int, val input: ContainerInput)
	private val packets = mutableListOf<Sent>()

	private val vanillaClicks = mutableListOf<Pair<Int, ContainerInput>>()
	private var sounds = 0
	private var now = 50_000L
	private val oldSound = MenuPress.sound; private val oldClock = MenuPress.clock

	private val realSlot: (Int) -> Boolean = { it >= 54 }

	private fun backgroundSlotAt(x: Double, y: Double) = ((y / 18).toInt() * 9 + (x / 18).toInt()).coerceIn(0, 89)

	private val host = object : MenuInputHost {
		override val hiddenNow = false
		override fun overShowButton(mx: Double, my: Double) = false
		override fun showMenu() {}
		override fun overPanelCell(mx: Double, my: Double) = my >= 100
		override fun inView(mx: Double, my: Double) = mx < 200 && my < 200
		override fun viewPress(click: MouseButtonEvent) { MenuPress.clickSent(); packets += Sent(menu.containerId, menu.stateId, 19, click.button(), ContainerInput.PICKUP) }
		override fun viewRelease(click: MouseButtonEvent) {}
		override fun viewScroll(mx: Double, my: Double, amount: Double) {}
	}
	private val router = MenuInputRouter(host)

	init {
		MenuPress.resetForTests(); MenuInputGuard.resetForTests()
		MenuPress.sound = { sounds++ }
		MenuPress.clock = { now }
		MenuPress.overRealMenu = { true }
	}
	@AfterEach fun reset() {
		MenuPress.sound = oldSound; MenuPress.clock = oldClock
		MenuPress.resetForTests(); MenuInputGuard.resetForTests()
	}

	private fun slotClicked(slot: Int, input: ContainerInput) {
		if (MenuInputGuard.allowSlotClick(realSlot, slot, input)) vanillaClicks += slot to input
	}

	private fun ev(x: Double, y: Double, button: Int = 0) = MouseButtonEvent(x, y, MouseButtonInfo(button, 0))

	private fun click(x: Double, y: Double, drags: Int = 0, button: Int = 0, input: ContainerInput = ContainerInput.PICKUP) {
		val bg = backgroundSlotAt(x, y)
		MenuInputGuard.rawPress()

		if (router.press(ev(x, y, button))) slotClicked(bg, input)
		repeat(drags) {
			if (!MenuInputGuard.consumeDrag()) {
				slotClicked(bg, ContainerInput.PICKUP)
				if (router.drag()) slotClicked(bg, ContainerInput.QUICK_CRAFT)
			}
		}
		if (!MenuInputGuard.consumeReleaseOn(true)) {
			if (router.release(ev(x, y, button))) slotClicked(bg, ContainerInput.PICKUP_ALL)
		}
		MenuInputGuard.end()
		now += 400
	}

	@Test
	fun pressReleaseOnTileIsOneIntendedPacketNoVanillaClickOneSound() {
		click(40.0, 40.0)
		Assertions.assertEquals(listOf(Sent(7, menu.stateId, 19, 0, ContainerInput.PICKUP)), packets)
		Assertions.assertEquals(emptyList<Pair<Int, ContainerInput>>(), vanillaClicks)
		Assertions.assertEquals(0, sounds)
		Assertions.assertFalse(MenuInputGuard.gestureActive)

		click(40.0, 40.0); Assertions.assertEquals(2, packets.size); Assertions.assertTrue(vanillaClicks.isEmpty()); Assertions.assertEquals(0, sounds)
	}

	@Test
	fun dragOverMenuSendsNothing() {
		click(40.0, 40.0, drags = 12)
		Assertions.assertEquals(1, packets.size)
		Assertions.assertTrue(vanillaClicks.isEmpty(), "$vanillaClicks")

		MenuInputGuard.rawPress(); Assertions.assertFalse(router.press(ev(40.0, 40.0)))
		Assertions.assertTrue(MenuInputGuard.gestureActive)
		Assertions.assertFalse(router.drag())
		for (s in listOf(19, 20, 60)) Assertions.assertFalse(MenuInputGuard.allowSlotClick(realSlot, s, ContainerInput.PICKUP))
		Assertions.assertFalse(MenuInputGuard.allowSlotClick(null, 20, ContainerInput.QUICK_CRAFT), "even a page with no Apex menu (the press opened it)")
		Assertions.assertFalse(router.release(ev(40.0, 40.0)))
		Assertions.assertFalse(MenuInputGuard.gestureActive)
	}

	@Test
	fun numberKeyOverTileDoesNotSwapBackgroundSlot() {

		for (s in 0 until 54) Assertions.assertFalse(MenuInputGuard.allowSlotClick(realSlot, s, ContainerInput.SWAP), "slot $s")
		for (input in listOf(ContainerInput.QUICK_MOVE, ContainerInput.PICKUP_ALL, ContainerInput.THROW, ContainerInput.CLONE))
			Assertions.assertFalse(MenuInputGuard.allowSlotClick(realSlot, 19, input), "$input")

		Assertions.assertTrue(MenuInputGuard.allowSlotClick(realSlot, 60, ContainerInput.SWAP))
		Assertions.assertTrue(MenuInputGuard.allowSlotClick(realSlot, 60, ContainerInput.QUICK_MOVE))
		Assertions.assertTrue(MenuInputGuard.allowSlotClick(realSlot, -999, ContainerInput.PICKUP))

		Assertions.assertTrue(MenuInputGuard.allowSlotClick(null, 19, ContainerInput.SWAP))
		Assertions.assertTrue(packets.isEmpty())
	}

	@Test
	fun panelCellStaysVanillaAndOutsideTheMenuIsNotConsumed() {
		click(40.0, 150.0, drags = 2)
		Assertions.assertTrue(packets.isEmpty())
		Assertions.assertTrue(vanillaClicks.isNotEmpty() && vanillaClicks.all { it.first == backgroundSlotAt(40.0, 150.0) })
		Assertions.assertEquals(0, sounds, "vanilla's own slot click has no Apex sound")
		MenuInputGuard.rawPress(); Assertions.assertTrue(router.press(ev(300.0, 40.0)))
		Assertions.assertFalse(MenuInputGuard.gestureActive)
		Assertions.assertFalse(router.release(ev(300.0, 40.0)), "a release that is not a real-slot gesture's never reaches the chest")
	}

	@Test
	fun releaseOnThePageThePressOpenedIsConsumed() {
		MenuInputGuard.rawPress(); Assertions.assertFalse(router.press(ev(40.0, 40.0)))
		Assertions.assertTrue(MenuInputGuard.consumeReleaseOn(false), "the new page has no Apex menu: its release is eaten")
		Assertions.assertFalse(MenuInputGuard.gestureActive)
		Assertions.assertFalse(MenuInputGuard.consumeReleaseOn(false), "a later plain release is vanilla's")

		MenuInputGuard.begin(); MenuInputGuard.rawPress(); Assertions.assertFalse(MenuInputGuard.gestureActive)
	}

	@Test
	fun wheelInsideMenuIsTheMenus() {
		Assertions.assertFalse(router.scroll(40.0, 40.0, 1.0))
		Assertions.assertTrue(router.scroll(40.0, 150.0, 1.0))
		Assertions.assertTrue(router.scroll(300.0, 40.0, 1.0))
	}

	private val root = listOf("src/main", "../src/main", "../../src/main").map { java.io.File(it).absoluteFile.normalize() }
		.first { java.io.File(it, "kotlin/features/menus/MenuInput.kt").isFile }
	private fun text(rel: String) = java.io.File(root, rel).readText()
	private fun sources() = root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }.toList()

	@Test
	fun everyApexMenuOverAContainerRoutesThroughTheConsumingBase() {
		val host = text("kotlin/features/menus/MenuHost.kt")

		for (f in sources().filter { Regex("""=\s*MenuSession\(""").containsMatchIn(it.readText()) })
			Assertions.assertTrue(f.readText().contains("MenuHost.attach("), "${f.name}: MenuSession without MenuHost.attach")
		Assertions.assertTrue(Regex("""fun attach[\s\S]*?session\.install\(\)""").containsMatchIn(host))
		val install = host.substringAfter("internal fun install()").substringBefore("\n\t}\n")
		for ((event, call) in listOf("allowMouseClick" to "router.press(", "allowMouseRelease" to "router.release(",
			"allowMouseDrag" to "router.drag(", "allowMouseScroll" to "router.scroll(")) {
			val line = install.lines().filter { it.contains("ScreenMouseEvents.$event(") }
			Assertions.assertEquals(1, line.size, "$event registered once")
			Assertions.assertTrue(line.single().contains(call), "$event -> $call")
		}
		Assertions.assertTrue(install.contains("MenuInputGuard.markRouted(screen)"))
		Assertions.assertFalse(host.contains("swallowRelease"), "the old global release flag is gone")

		for (f in sources().filter { it.readText().contains("MenuFront(") }) Assertions.assertEquals("MenuHost.kt", f.name)
		val guis = sources().filter { Regex(""":\s*CustomGui\(\)""").containsMatchIn(it.readText()) }.map { it.name }.toSet()
		Assertions.assertEquals(setOf("MenuHost.kt", "StorageOverlayCustom.kt", "HotmPresets.kt"), guis, "a new menu-over-container must use MenuSession")

		val mouse = sources().filter { it.readText().contains("ScreenMouseEvents.allowMouse") }.map { it.name }.toSet()
		Assertions.assertEquals(setOf("MenuHost.kt", "AccessoryBagHelper.kt", "MuseumHelper.kt", "AuctionBrowserPanel.kt", "ViewerTabs.kt", "InventoryKeyClose.kt"), mouse)

		val handled = text("java/dev/ashtonite365/apex/mixins/MixinHandledScreen.java")
		Assertions.assertTrue(Regex("""slotClicked\(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;\)V"[^\n]*\n[^\n]*apex\${'$'}guardSlotClick[^\n]*\n[^\n]*ContainerScreenHooks\.dropSlotClick\(""").containsMatchIn(handled))

		val guard = text("kotlin/hooks/ContainerScreenHooks.kt").substringAfter("fun dropSlotClick(").substringAfter("{\n").trimStart()
		Assertions.assertTrue(guard.startsWith("if (!MenuInputGuard.allowVanillaSlotClick(screen, id, input, button)) return true"))
		val raw = text("java/dev/ashtonite365/apex/mixins/MenuGesturePatch.java")
		for (s in listOf("rawPress()", "Screen;mouseReleased(", "Screen;mouseDragged(", "consumeRelease(", "consumeDrag()", "end()"))
			Assertions.assertTrue(raw.contains(s), s)
	}
}
