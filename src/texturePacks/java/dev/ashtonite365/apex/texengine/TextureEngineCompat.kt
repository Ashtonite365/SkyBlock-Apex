/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: marks the `texturePacks` source set (the texture engine) for the compat loader. It has no optional
 * dependency, so it always loads.
 */
package dev.ashtonite365.apex.texengine

import dev.ashtonite365.apex.util.compatloader.CompatEntry
import dev.ashtonite365.apex.util.compatloader.CompatSwitch

@CompatEntry
object TextureEngineCompat : CompatSwitch {
	override fun enabledHere() = true
}
