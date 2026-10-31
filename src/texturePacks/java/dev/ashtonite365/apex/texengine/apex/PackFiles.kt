/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: reading pack JSON files.
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory

internal object PackFiles {
	val log = LoggerFactory.getLogger("SkyBlock Apex textures")

	fun parse(text: String): JsonElement = PackJson.parse(text)

	fun read(resource: Resource): JsonElement = parse(resource.open().use { String(it.readAllBytes(), Charsets.UTF_8) })

	fun list(manager: ResourceManager, folder: String, namespaces: Collection<String>): List<Pair<Identifier, Resource>> =
		manager.listResources(folder) { it.namespace in namespaces && it.path.endsWith(".json") }
			.entries.sortedBy { it.key }.map { it.key to it.value }

	fun readOrNull(manager: ResourceManager, id: Identifier, what: String): JsonElement? {
		val resource = manager.getResource(id).orElse(null)
		if (resource == null) {
			log.warn("Missing {} {}", what, id)
			return null
		}
		return try {
			read(resource)
		} catch (e: Exception) {
			log.warn("Could not read {} {}: {}", what, id, e.message)
			dev.ashtonite365.apex.util.trail.Trail.crumb(dev.ashtonite365.apex.util.trail.Crumb.TEXTURE_FILE_SKIPPED, id)
			null
		}
	}

	fun nameIn(folder: String, id: Identifier): String = id.path.removePrefix("$folder/").removeSuffix(".json")
}
