package dev.ashtonite365.apex.util.mc

import java.util.Spliterator
import java.util.Spliterators
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

val Container.indices: IntRange get() = 0..<containerSize

val Container.iterableView: Iterable<ItemStack> get() = ContainerStacks(this)

private class ContainerStacks(private val container: Container) : Iterable<ItemStack> {
	override fun iterator(): Iterator<ItemStack> = object : Iterator<ItemStack> {
		private var next = 0
		override fun hasNext(): Boolean = next < container.containerSize
		override fun next(): ItemStack {
			if (next >= container.containerSize) throw NoSuchElementException()
			return container.getItem(next++)
		}
	}

	override fun spliterator(): Spliterator<ItemStack> =
		Spliterators.spliterator(iterator(), container.containerSize.toLong(), 0)
}
