package dev.ashtonite365.apex.util.mc

import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex

object CommonTextures {
	private const val WIDGET_SPRITE = "generic_vanilla_widget"

	fun genericWidget(): Identifier = Apex.identifier(WIDGET_SPRITE)
}
