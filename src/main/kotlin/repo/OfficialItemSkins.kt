// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.logging.log4j.LogManager
import dev.ashtonite365.apex.Apex

object OfficialItemSkins {
	private val logger = LogManager.getLogger("Apex.OfficialItemSkins")

	@Serializable
	data class Cache(val skins: Map<String, String> = emptyMap())

	@Volatile var skins: Map<String, String> = emptyMap()
		private set

	private val cacheFile: Path get() = Apex.DATA_DIR.resolve("official-item-skins.json")

	fun get(id: String): String? = skins[id]

	@Volatile private var warned = false

	fun warnOnce(what: String, t: Throwable) {
		if (warned) return
		warned = true
		logger.warn("[Apex] head textures: $what failed, using plain heads: $t")
	}

	fun parse(text: String): Map<String, String> {
		val root = Json.parseToJsonElement(text).jsonObject
		val items = root["items"]?.jsonArray ?: return emptyMap()
		return items.mapNotNull { e ->
			val o = e as? JsonObject ?: return@mapNotNull null
			val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
			val v = (o["skin"] as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
			id to v
		}.toMap()
	}

	fun fromResponse(r: HypixelStaticData.ItemsResponse): Map<String, String> =
		r.items.mapNotNull { i -> i.skin?.value?.takeIf { it.isNotBlank() }?.let { i.id to it } }.toMap()

	fun headTexture(id: String, repoTexture: String?, official: String?, learned: LearnedSkullTextures.Learned?): LearnedSkullTextures.Learned? = when {
		!repoTexture.isNullOrBlank() -> LearnedSkullTextures.Learned(uuidFor(id, repoTexture), repoTexture)
		!official.isNullOrBlank() -> LearnedSkullTextures.Learned(uuidFor(id, official), official)
		else -> learned
	}

	fun uuidFor(id: String, value: String): String = java.util.UUID.nameUUIDFromBytes("$id:$value".toByteArray()).toString()

	fun loadCache() {
		try {
			val f = cacheFile
			if (!Files.exists(f)) return
			val c = Apex.json.decodeFromString(Cache.serializer(), Files.readString(f))
			if (skins.isEmpty() && c.skins.isNotEmpty()) {
				skins = c.skins
				logger.info("[Apex] official item skins: ${c.skins.size} from the disk cache")
				ItemCache.forgetTexturelessHeads()
			}
		} catch (t: Throwable) { logger.warn("[Apex] could not read the official item skins cache: $t") }
	}

	fun onFetched(fresh: Map<String, String>) = try { onFetched0(fresh) } catch (t: Throwable) { warnOnce("official skins update", t) }

	private fun onFetched0(fresh: Map<String, String>) {
		if (fresh.isEmpty()) { logger.warn("[Apex] official items resource had no skins; keeping the cache"); return }
		val changed = fresh != skins
		skins = fresh
		logger.info("[Apex] official item skins: ${fresh.size} fetched")
		if (!changed) return
		runCatching { Files.writeString(cacheFile, Apex.json.encodeToString(Cache.serializer(), Cache(fresh))) }
			.onFailure { logger.warn("[Apex] could not save the official item skins cache: $it") }
		ItemCache.forgetTexturelessHeads()
	}
}
