package dev.ashtonite365.apex.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ServerLocation(
	val server: String,
	val gametype: String? = null,
	val mode: String? = null,
	val map: String? = null,
) {

	@Transient
	val skyblockIsland: SkyBlockIsland? = if (gametype != "SKYBLOCK" || mode == null) null else SkyBlockIsland.ofMode(mode)
}
