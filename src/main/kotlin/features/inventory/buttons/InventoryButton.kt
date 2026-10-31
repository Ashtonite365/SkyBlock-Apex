package dev.ashtonite365.apex.features.inventory.buttons

import me.shedaniel.math.Dimension
import me.shedaniel.math.Rectangle
import kotlinx.serialization.Serializable
import net.minecraft.client.gui.GuiGraphicsExtractor
import dev.ashtonite365.apex.util.mc.KeptStack

@Serializable
data class InventoryButton(var x: Int, var y: Int, var anchorRight: Boolean, var anchorBottom: Boolean, var icon: String? = "", var command: String? = "", var isGigantic: Boolean = false) {
	val size: Dimension get() = ButtonLayout.sizeOf(isGigantic)

	fun isUsable(): Boolean = listOf(icon, command).none { it.isNullOrBlank() }

	fun areaIn(frame: Rectangle): Rectangle = ButtonLayout.area(this, frame)

	fun iconStack(): KeptStack = ButtonIcons.resolve(icon.orEmpty())

	fun render(context: GuiGraphicsExtractor) = ButtonIcons.draw(context, this)
}
