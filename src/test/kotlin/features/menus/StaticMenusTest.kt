// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.features.menus.StaticMenus
import dev.ashtonite365.apex.util.ApexClickDelay

@ExtendWith(AutoBootstrapExtension::class)
class StaticMenusTest {
	private class FakeMenu(id: Int) : AbstractContainerMenu(null, id) {
		val box = SimpleContainer(54)
		init { for (i in 0 until 54) addSlot(Slot(box, i, 0, 0)) }
		override fun quickMoveStack(player: Player, i: Int) = ItemStack.EMPTY
		override fun stillValid(player: Player) = true
	}

	private var now = 50_000L
	private var on = true
	private var sounds = 0
	data class Sent(val containerId: Int, val slot: Int, val button: Int, val input: ContainerInput)
	private val packets = mutableListOf<Sent>()
	private val menu = FakeMenu(12)
	private val oldSound = MenuPress.sound; private val oldClock = MenuPress.clock

	init {
		MenuPress.resetForTests(); StaticMenus.resetForTests()
		StaticMenus.clock = { now }; StaticMenus.enabledSource = { on }; StaticMenus.screenCheck = { true }
		MenuPress.clock = { now }; MenuPress.sound = { sounds++ }

		ApexClickDelay.testSink = { c, _, sl, b, input ->
			packets += Sent(c, sl, b, input)
			val s = menu.slots[sl]
			menu.carried = s.remove(s.item.count)
		}
		menu.slots[20].set(ItemStack(Items.EMERALD))
		menu.slots[21].set(ItemStack(Items.ARROW, 3))
	}

	@AfterEach fun reset() {
		MenuPress.sound = oldSound; MenuPress.clock = oldClock
		MenuPress.resetForTests(); ApexClickDelay.resetForTests(); StaticMenus.resetForTests()
	}

	private fun press(slot: Int = 20) = MenuPress.press { ApexClickDelay.send(menu, menu.slots[slot], 0) }

	private fun drawn(m: AbstractContainerMenu, slot: Int): Pair<ItemStack, ItemStack> {
		StaticMenus.beginDraw(m)
		try {
			val s = m.slots[slot]
			return (StaticMenus.shownItem(s) ?: s.item) to (StaticMenus.shownCarried(m) ?: m.carried)
		} finally { StaticMenus.endDraw() }
	}

	private fun assertFrozenView() {
		val (item, cursor) = drawn(menu, 20)
		Assertions.assertTrue(item.`is`(Items.EMERALD), "the pressed button stays drawn")
		Assertions.assertTrue(cursor.isEmpty, "no predicted item on the cursor")
	}

	private fun assertLiveView() {
		val (item, cursor) = drawn(menu, 20)
		Assertions.assertTrue(item === menu.slots[20].item, "live slot")
		Assertions.assertTrue(cursor === menu.carried, "live cursor")
	}

	@Test
	fun onePressIsOnePacketAndOneSoundWithTheToggleOnOrOff() {
		for (toggle in listOf(true, false)) {
			on = toggle; packets.clear(); sounds = 0
			menu.carried = ItemStack.EMPTY; menu.slots[20].set(ItemStack(Items.EMERALD)); StaticMenus.releaseAll()
			press()
			Assertions.assertEquals(listOf(Sent(12, 20, 0, ContainerInput.PICKUP)), packets, "toggle=$toggle")
			Assertions.assertEquals(1, sounds, "toggle=$toggle")
			Assertions.assertEquals(toggle, StaticMenus.frozen != null, "toggle=$toggle")

			Assertions.assertTrue(menu.slots[20].item.isEmpty); Assertions.assertTrue(menu.carried.`is`(Items.EMERALD))
			now += MenuPress.WINDOW_MS + 1
		}
	}

	@Test
	fun frozenOnlyWhileDrawing() {
		press()
		assertFrozenView()

		Assertions.assertNull(StaticMenus.shownItem(menu.slots[20]))
		Assertions.assertNull(StaticMenus.shownCarried(menu))

		val other = FakeMenu(13)
		StaticMenus.beginDraw(other); Assertions.assertNull(StaticMenus.shownCarried(other)); StaticMenus.endDraw()
	}

	@Test
	fun unchangedSlotsKeepTheirLiveStack() {
		press()
		StaticMenus.beginDraw(menu)
		Assertions.assertNull(StaticMenus.shownItem(menu.slots[21]), "unchanged: the live object is drawn")
		StaticMenus.endDraw()
	}

	@Test
	fun noSubstitutionDuringAClick() {
		press()
		StaticMenus.beginDraw(menu)
		StaticMenus.beforeClick(-1, 0)
		Assertions.assertNull(StaticMenus.shownItem(menu.slots[20])); Assertions.assertNull(StaticMenus.shownCarried(menu))
		StaticMenus.afterClick()
		Assertions.assertNotNull(StaticMenus.shownItem(menu.slots[20]))
		StaticMenus.endDraw()
	}

	@Test
	fun fullContentsReleaseAtOnce() {
		press(); assertFrozenView()
		StaticMenus.onContents(12)
		Assertions.assertNull(StaticMenus.frozen); assertLiveView()
	}

	@Test
	fun slotAndCursorAnswersReleaseAtTheEndOfTheTick() {
		press()
		StaticMenus.onSlot(0)
		StaticMenus.onTickEnd(null, menu); Assertions.assertNotNull(StaticMenus.frozen)
		StaticMenus.onSlot(12); assertFrozenView()
		StaticMenus.onTickEnd(null, menu); Assertions.assertNull(StaticMenus.frozen)
		menu.carried = ItemStack.EMPTY
		press(21)
		StaticMenus.onCursor(); StaticMenus.onTickEnd(null, menu); Assertions.assertNull(StaticMenus.frozen)
	}

	@Test
	fun timeoutShowsTheRealState() {
		press()
		now += StaticMenus.TIMEOUT_MS - 1; assertFrozenView()
		now += 2; assertLiveView()
		StaticMenus.onTickEnd(null, menu); Assertions.assertNull(StaticMenus.frozen)
	}

	@Test
	fun chainedPressesAreCapped() {
		press()
		repeat(2) {
			now += 900; menu.carried = ItemStack.EMPTY; menu.slots[21].set(ItemStack(Items.ARROW, 3))
			press(21); StaticMenus.onTickEnd(null, menu)
			Assertions.assertNotNull(StaticMenus.frozen, "each unanswered press extends the hold")
		}
		now += 900; StaticMenus.onTickEnd(null, menu)
		Assertions.assertNull(StaticMenus.frozen, "never longer than the cap after the first press")
		Assertions.assertEquals(3, packets.size)
	}

	@Test
	fun closeOtherWindowOrScreenChangeRelease() {
		press(); StaticMenus.onClose(); Assertions.assertNull(StaticMenus.frozen)
		menu.carried = ItemStack.EMPTY; menu.slots[20].set(ItemStack(Items.EMERALD))
		press(); StaticMenus.onContents(99); Assertions.assertNull(StaticMenus.frozen)
		menu.carried = ItemStack.EMPTY; menu.slots[20].set(ItemStack(Items.EMERALD))
		press(); StaticMenus.onSlot(98); Assertions.assertNull(StaticMenus.frozen)
		menu.carried = ItemStack.EMPTY; menu.slots[20].set(ItemStack(Items.EMERALD))
		press(); StaticMenus.onTickEnd(null, FakeMenu(14)); Assertions.assertNull(StaticMenus.frozen)
		menu.carried = ItemStack.EMPTY; menu.slots[20].set(ItemStack(Items.EMERALD))
		press(); on = false; StaticMenus.onTickEnd(null, menu); Assertions.assertNull(StaticMenus.frozen)
	}

	@Test
	fun offChangesNothing() {
		on = false
		press()
		Assertions.assertNull(StaticMenus.frozen); assertLiveView()
		StaticMenus.beforeOpen(Any(), menu, 40)
		Assertions.assertNull(StaticMenus.held)
		Assertions.assertEquals(1, packets.size)
	}

	@Test
	fun onlyMenuButtonPressesWithAnEmptyCursorFreeze() {

		ApexClickDelay.send(menu, menu.slots[20], 0)
		Assertions.assertNull(StaticMenus.frozen); Assertions.assertEquals(1, packets.size)

		menu.slots[20].set(ItemStack(Items.EMERALD)); menu.carried = ItemStack(Items.STONE)
		Assertions.assertFalse(StaticMenus.shouldArm(menu, 20, viaView = true, attributed = true))
		menu.carried = ItemStack.EMPTY

		Assertions.assertFalse(StaticMenus.shouldArm(menu, 30, viaView = true, attributed = true))
		Assertions.assertFalse(StaticMenus.shouldArm(menu, 999, viaView = true, attributed = true))
		Assertions.assertTrue(StaticMenus.shouldArm(menu, 20, viaView = true, attributed = true))

		val tag = CompoundTag(); tag.putString("id", "ASPECT_OF_THE_END")
		val real = ItemStack(Items.DIAMOND_SWORD); real.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
		menu.slots[22].set(real)
		Assertions.assertTrue(StaticMenus.isRealItem(real)); Assertions.assertFalse(StaticMenus.isRealItem(menu.slots[20].item))
		Assertions.assertFalse(StaticMenus.shouldArm(menu, 22, viaView = false, attributed = true))
		Assertions.assertTrue(StaticMenus.shouldArm(menu, 20, viaView = false, attributed = true))

		StaticMenus.screenCheck = { false }
		Assertions.assertFalse(StaticMenus.shouldArm(menu, 20, viaView = true, attributed = true))
	}

	@Test
	fun newWindowDrawsTheOldFrameUntilItsItemsArrive() {
		val oldScreen = Any(); val newScreen = Any(); val next = FakeMenu(13)
		press()
		StaticMenus.beforeOpen(oldScreen, menu, 13)
		StaticMenus.afterOpen(newScreen, 13)
		Assertions.assertSame(oldScreen, StaticMenus.heldFor(newScreen, 13))
		Assertions.assertNull(StaticMenus.heldFor(oldScreen, 12), "the old screen draws itself")
		assertFrozenView()
		StaticMenus.onTickEnd(newScreen, next); Assertions.assertNotNull(StaticMenus.held, "items not there yet")
		StaticMenus.onContents(13)
		Assertions.assertSame(oldScreen, StaticMenus.heldFor(newScreen, 13), "until a tick has run on the new window")
		StaticMenus.onTickEnd(newScreen, next)
		Assertions.assertNull(StaticMenus.held); Assertions.assertNull(StaticMenus.frozen)
		Assertions.assertNull(StaticMenus.heldFor(newScreen, 13))
		Assertions.assertEquals(1, packets.size)
	}

	@Test
	fun heldFrameEndsOnTimeoutInputCloseOrAnotherScreen() {
		val oldScreen = Any(); val newScreen = Any()
		press(); StaticMenus.beforeOpen(oldScreen, menu, 13); StaticMenus.afterOpen(newScreen, 13)
		now += StaticMenus.TIMEOUT_MS + 1
		Assertions.assertNull(StaticMenus.heldFor(newScreen, 13)); Assertions.assertNull(StaticMenus.held)

		now += 5_000; reArm()
		StaticMenus.beforeOpen(oldScreen, menu, 13); StaticMenus.afterOpen(newScreen, 13)
		StaticMenus.onInput(); Assertions.assertNull(StaticMenus.held)

		now += 5_000; reArm()
		StaticMenus.beforeOpen(oldScreen, menu, 13); StaticMenus.afterOpen(newScreen, 13)
		StaticMenus.onClose(); Assertions.assertNull(StaticMenus.held)

		now += 5_000; reArm()
		StaticMenus.beforeOpen(oldScreen, menu, 13); StaticMenus.afterOpen(null, null)
		Assertions.assertNull(StaticMenus.held)

		now += 5_000; reArm()
		StaticMenus.beforeOpen(oldScreen, menu, 13); StaticMenus.afterOpen(newScreen, 13)
		StaticMenus.onTickEnd(Any(), FakeMenu(77)); Assertions.assertNull(StaticMenus.held)
		Assertions.assertEquals(5, packets.size)
	}

	private fun reArm() { menu.carried = ItemStack.EMPTY; menu.slots[20].set(ItemStack(Items.EMERALD)); press() }

	@Test
	fun noHeldFrameWithoutARecentPress() {
		StaticMenus.beforeOpen(Any(), menu, 13); Assertions.assertNull(StaticMenus.held, "server opened it on its own")
		press(); StaticMenus.onContents(12)
		now += StaticMenus.TIMEOUT_MS + 1
		StaticMenus.beforeOpen(Any(), menu, 13); Assertions.assertNull(StaticMenus.held, "too long after the press")

		StaticMenus.expectScreen()
		StaticMenus.beforeOpen(Any(), null, 14); Assertions.assertNotNull(StaticMenus.held)
	}

	@Test
	fun secondWindowKeepsTheFirstFrame() {
		val oldScreen = Any()
		press(); StaticMenus.beforeOpen(oldScreen, menu, 13); StaticMenus.afterOpen(Any(), 13)
		val mid = Any(); val last = Any()
		StaticMenus.beforeOpen(mid, FakeMenu(13), 14); StaticMenus.afterOpen(last, 14)
		Assertions.assertSame(oldScreen, StaticMenus.heldFor(last, 14))
		StaticMenus.onContents(14); Assertions.assertTrue(StaticMenus.held!!.filled)
	}
}
