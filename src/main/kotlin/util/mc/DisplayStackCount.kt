// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.mc

import java.text.NumberFormat
import java.util.Locale

object DisplayStackCount {

	fun clamp(amount: Int, maxStack: Int): Int = when {
		amount <= 0 -> amount
		amount > maxStack.coerceAtLeast(1) -> 1
		else -> amount
	}

	fun label(amount: Int, maxStack: Int): String? =
		if (amount > maxStack.coerceAtLeast(1)) NumberFormat.getIntegerInstance(Locale.US).format(amount) else null
}
