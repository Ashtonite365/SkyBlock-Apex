/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, which island store a place belongs to (pure Kotlin, unit-tested).
 *
 * Keyed by the Hypixel location mode Apex already detects (SkyBlockIsland), plus the profile for your own Private
 * Island and Garden. Anything uncertain gets no store at all (nothing is recorded until it is certain), so a wrong
 * guess can never write one island's terrain into another island's save.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.util.UUID

enum class SurfaceOwnership { OWN, GUEST, UNKNOWN }

data class SurfacePlace(

	val server: String?,

	val singleplayerWorld: String?,
	val onHypixel: Boolean,

	val locationFresh: Boolean,
	val gametype: String?,
	val mode: String?,
	val ownership: SurfaceOwnership,
	val profileId: UUID?,
	val dimension: String,

	val connection: Long,
)

data class SurfaceIslandKey(val id: String, val path: String?, val label: String) {
	val persistent: Boolean get() = path != null
}

object SurfaceIslandKeys {
	enum class Policy { KEEP, OWN_PER_PROFILE, MEMORY_ONLY }

	val AREAS: Map<String, Pair<String, Policy>> = mapOf(
		"dynamic" to ("private-island" to Policy.OWN_PER_PROFILE),
		"garden" to ("garden" to Policy.OWN_PER_PROFILE),
		"dungeon" to ("dungeon" to Policy.MEMORY_ONLY),
		"kuudra" to ("kuudra" to Policy.MEMORY_ONLY),
		"mineshaft" to ("mineshaft" to Policy.MEMORY_ONLY),
		"_" to ("unknown" to Policy.MEMORY_ONLY),
		"hub" to ("hub" to Policy.KEEP),
		"dungeon_hub" to ("dungeon-hub" to Policy.KEEP),
		"mining_1" to ("gold-mine" to Policy.KEEP),
		"mining_2" to ("deep-caverns" to Policy.KEEP),
		"mining_3" to ("dwarven-mines" to Policy.KEEP),
		"crystal_hollows" to ("crystal-hollows" to Policy.KEEP),
		"combat_1" to ("spiders-den" to Policy.KEEP),
		"combat_3" to ("the-end" to Policy.KEEP),
		"crimson_isle" to ("crimson-isle" to Policy.KEEP),
		"farming_1" to ("farming-islands" to Policy.KEEP),
		"foraging_1" to ("the-park" to Policy.KEEP),
		"foraging_2" to ("galatea" to Policy.KEEP),
		"fishing_1" to ("backwater-bayou" to Policy.KEEP),
		"winter" to ("jerrys-workshop" to Policy.KEEP),
		"rift" to ("rift" to Policy.KEEP),
		"dark_auction" to ("dark-auction" to Policy.KEEP),
	)

	fun slug(raw: String): String =
		raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "unknown" }.take(60)

	private fun dimSuffix(dimension: String): String {
		val d = dimension.lowercase()
		return if (d == "minecraft:overworld" || d.isBlank()) "" else "@" + slug(d)
	}

	private val ownTitle = Regex("^SK[YI]BLOCK(?: CO-OP)?$")

	fun ownershipFromSidebarTitle(title: String?): SurfaceOwnership {
		val t = title?.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")?.trim()?.uppercase() ?: return SurfaceOwnership.UNKNOWN
		if (t.isEmpty()) return SurfaceOwnership.UNKNOWN
		if ("GUEST" in t) return SurfaceOwnership.GUEST
		if (ownTitle.matches(t)) return SurfaceOwnership.OWN
		return SurfaceOwnership.UNKNOWN
	}

	fun key(p: SurfacePlace): SurfaceIslandKey? {
		val dim = dimSuffix(p.dimension)
		val dimPath = if (dim.isEmpty()) "" else "/" + dim.removePrefix("@")
		if (p.server == null) {
			val world = slug(p.singleplayerWorld ?: return null)
			return SurfaceIslandKey("sp:$world$dim", "singleplayer/$world$dimPath", "Single player: ${p.singleplayerWorld}$dim")
		}
		if (!p.onHypixel) {
			val host = slug(p.server.substringBefore(':').ifBlank { p.server })
			return SurfaceIslandKey("server:$host$dim", "server/$host$dimPath", "Server ${p.server}$dim")
		}
		if (!p.locationFresh) return null
		if (p.gametype != "SKYBLOCK") {
			val g = slug(p.gametype ?: return null)
			return SurfaceIslandKey("mem:hypixel-$g$dim#${p.connection}", null, "Hypixel $g (not saved)")
		}
		val mode = p.mode?.takeIf { it.isNotBlank() } ?: return null
		val (area, policy) = AREAS[mode] ?: (slug(mode) to Policy.KEEP)
		return when (policy) {
			Policy.KEEP -> SurfaceIslandKey("hypixel:$area$dim", "hypixel/$area$dimPath", "$area$dim")
			Policy.MEMORY_ONLY -> SurfaceIslandKey("mem:$area$dim#${p.connection}", null, "$area (not saved)")
			Policy.OWN_PER_PROFILE -> when (p.ownership) {
				SurfaceOwnership.UNKNOWN -> null
				SurfaceOwnership.GUEST -> SurfaceIslandKey("mem:guest-$area$dim#${p.connection}", null, "visited $area (not saved)")
				SurfaceOwnership.OWN -> {
					val profile = p.profileId?.toString()?.lowercase() ?: return null
					SurfaceIslandKey("hypixel:$area/$profile$dim", "hypixel/$area/$profile$dimPath", "own $area ($profile)$dim")
				}
			}
		}
	}
}
