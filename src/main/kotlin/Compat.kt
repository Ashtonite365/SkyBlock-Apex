// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex

import dev.ashtonite365.apex.util.compatloader.CompatEntry
import dev.ashtonite365.apex.util.compatloader.CompatSwitch

@CompatEntry
object Compat : CompatSwitch {
	override fun enabledHere() = true
}
