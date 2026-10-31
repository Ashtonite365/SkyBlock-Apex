/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: the item model id packs use for a SkyBlock item (SPEC §2.1).
 */
package dev.ashtonite365.apex.texengine.apex

import net.minecraft.resources.Identifier

object ItemModelIds {

	const val NAMESPACE = "firmskyblock"

	fun forSkyBlockId(repoItemId: String): Identifier = Identifier.fromNamespaceAndPath(NAMESPACE, pathFor(repoItemId))

	fun pathFor(repoItemId: String): String {
		val out = StringBuilder(repoItemId.length + 8)
		for (ch in repoItemId.lowercase()) {
			when {
				ch == ';' -> out.append("__")
				ch == ':' -> out.append("___")
				ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '.' || ch == '/' -> out.append(ch)
				else -> out.append("__").append(String.format("%04x", ch.code))
			}
		}
		return out.toString()
	}
}
