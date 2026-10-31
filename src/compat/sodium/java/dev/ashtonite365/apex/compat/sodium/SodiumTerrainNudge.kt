/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: asks Sodium to re-walk its section graph (public Sodium API) after block replacements switched, so
 * the rebuilt sections are picked up at once. Loaded by class name from the texture engine; only present with Sodium.
 */
package dev.ashtonite365.apex.compat.sodium

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer

class SodiumTerrainNudge : Runnable {
	override fun run() {
		SodiumWorldRenderer.instanceNullable()?.scheduleTerrainUpdate()
	}
}
