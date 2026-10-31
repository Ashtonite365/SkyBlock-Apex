package util.mc

import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

class FakeInventory(val stack: ItemStack) : Container {
	override fun getContainerSize(): Int = 1

	override fun isEmpty(): Boolean = stack.isEmpty

	override fun getItem(slot: Int): ItemStack {
		require(slot == 0) { "Failed requirement." }
		return stack
	}

	override fun removeItem(slot: Int, amount: Int): ItemStack = ItemStack.EMPTY
	override fun removeItemNoUpdate(slot: Int): ItemStack = ItemStack.EMPTY
	override fun setItem(slot: Int, stack: ItemStack) = Unit
	override fun clearContent() = Unit
	override fun setChanged() = Unit
	override fun stillValid(player: Player): Boolean = true
}
