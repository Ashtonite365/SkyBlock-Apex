package dev.ashtonite365.apex.test.features.apexbzmenu

import dev.ashtonite365.apex.features.apexbzmenu.BzHeld
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.ApexClickDelay
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class BzInventoryClickTest {

	private class FakeMenu(id: Int) : AbstractContainerMenu(null, id) {
		val box = SimpleContainer(90)
		init { for (i in 0 until 90) addSlot(Slot(box, i, 0, 0)) }
		override fun quickMoveStack(player: Player, i: Int) = ItemStack.EMPTY
		override fun stillValid(player: Player) = true
	}
	data class Sent(val containerId: Int, val slot: Int, val button: Int, val input: ContainerInput)
	private val packets = mutableListOf<Sent>()

	init {
		MenuPress.resetForTests()
		ApexClickDelay.testSink = { c, _, sl, b, i -> packets += Sent(c, sl, b, i) }
	}

	@AfterEach fun reset() { ApexClickDelay.resetForTests(); MenuPress.resetForTests() }

	@Test fun oneRowClickIsOnePacketOnItsSlot() {
		val m = FakeMenu(12)
		val coal = BzHeld.Item("COAL", "Coal", 64, 2.0, BzHeld.Source.BAZAAR, menuSlot = 63)
		assertTrue(BzHeld.sendRowClick(m, coal, "COAL", 0, false))
		assertEquals(listOf(Sent(12, 63, 0, ContainerInput.PICKUP)), packets)
		packets.clear()
		assertTrue(BzHeld.sendRowClick(m, coal, "COAL", 1, false))
		assertEquals(listOf(Sent(12, 63, 1, ContainerInput.PICKUP)), packets, "right click kept")
		packets.clear()
		assertTrue(BzHeld.sendRowClick(m, coal, "COAL", 0, true))
		assertEquals(listOf(Sent(12, 63, 0, ContainerInput.QUICK_MOVE)), packets, "shift-click = QUICK_MOVE, like vanilla")
		packets.clear()

		assertFalse(BzHeld.sendRowClick(m, coal.copy(menuSlot = null), "COAL", 0, false))
		assertFalse(BzHeld.sendRowClick(m, coal, "WHEAT", 0, false))
		assertFalse(BzHeld.sendRowClick(m, coal.copy(menuSlot = 200), "COAL", 0, false))
		assertEquals(emptyList<Sent>(), packets)

		for (s in BzHeld.playerSlots(m.slots.size)) {
			BzHeld.sendRowClick(m, coal.copy(menuSlot = s), "COAL", 0, false)
		}
		assertEquals((54 until 90).toList(), packets.map { it.slot })
	}
}
