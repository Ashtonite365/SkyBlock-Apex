package dev.ashtonite365.apex.gui.config

import net.minecraft.network.chat.Component

fun interface EnumRenderer<E : Any> {
	fun getName(option: ManagedOption<E>, value: E): Component

	companion object Defaults {

		fun <E : Enum<E>> default(): EnumRenderer<E> =
			EnumRenderer { option, choice -> Component.translatable("${option.rawLabelText}.choice.${choice.name.lowercase()}") }
	}
}
