package dev.ashtonite365.apex.util.mc

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.ContainerInput
import dev.ashtonite365.apex.util.Client

object SlotUtils {

	const val PACKET_LEFT = 0
	const val PACKET_RIGHT = 1

	const val PACKET_MIDDLE = 2

	private fun Slot.send(menu: AbstractContainerMenu, button: Int, input: ContainerInput) {
		val gameMode = Client.gameMode ?: return
		gameMode.handleContainerInput(menu.containerId, index, button, input, Client.player!!)
	}

	fun Slot.clickMiddleMouseButton(handler: AbstractContainerMenu) = send(handler, PACKET_MIDDLE, ContainerInput.CLONE)

	fun Slot.swapWithHotBar(handler: AbstractContainerMenu, hotbarIndex: Int) = send(handler, hotbarIndex, ContainerInput.SWAP)

	fun Slot.clickRightMouseButton(handler: AbstractContainerMenu) = send(handler, PACKET_RIGHT, ContainerInput.PICKUP)

	fun Slot.clickLeftMouseButton(handler: AbstractContainerMenu) = send(handler, PACKET_LEFT, ContainerInput.PICKUP)
}
