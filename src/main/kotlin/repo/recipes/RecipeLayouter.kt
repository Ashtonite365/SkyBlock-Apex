// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo.recipes

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import dev.ashtonite365.apex.repo.SBItemStack

interface RecipeLayouter {

	enum class SlotRole(val isBig: Boolean = false) {
		SMALL_INPUT,
		SMALL_OUTPUT,
		BIG_OUTPUT(isBig = true),
		DISPLAY,
	}

	fun interface Settable<T> {
		fun setTo(value: T)
	}

	interface StackCell : Settable<SBItemStack> {
		fun showing(): SBItemStack
	}

	interface RotatingCell : StackCell {
		fun onRotate(listener: () -> Unit)
	}

	fun cyclingSlotAt(x: Int, y: Int, content: List<SBItemStack>, slotRole: SlotRole): RotatingCell

	fun itemSlotAt(x: Int, y: Int, content: SBItemStack?, slotRole: SlotRole): StackCell =
		cyclingSlotAt(x, y, if (content == null) emptyList() else listOf(content), slotRole)

	fun placeLabel(x: Int, y: Int, text: Component): Settable<Component>

	fun hoverArea(rectangle: Rectangle, label: List<Component>)

	fun hoverArea(rectangle: Rectangle, vararg label: Component) = hoverArea(rectangle, label.asList())

	fun arrowAt(left: Int, top: Int): Rectangle

	fun flameAt(point: Point, animationTicks: Int)

	fun entityIn(rectangle: Rectangle, entity: LivingEntity)

	fun embedAt(box: Rectangle, component: GuiComponent)
}
