// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

object SubOptionLook {

	const val INDENT = 12

	const val GUIDE_X = 5
	const val GUIDE_COLOUR = 0x80A0A0A0.toInt()

	const val DISABLED_TINT = 0xA0101010.toInt()

	fun editable(option: ManagedOption<*>): Boolean = option.parentActive
}
