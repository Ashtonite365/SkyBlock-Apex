// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.logging.log4j.LogManager
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object EssenceSkinExport {
	private val log = LogManager.getLogger("Apex.EssenceSkins")
	private val ownLore = Regex("You currently own [\\d,]+ .*")
	private val nameRe = Regex("^([A-Za-z ]+) Essence$")
	@Volatile private var lastExported: Map<String, String> = emptyMap()
	private val seen = LinkedHashMap<String, String>()

	fun idFor(displayName: String): String? {
		val m = nameRe.matchEntire(displayName.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()) ?: return null
		return "ESSENCE_" + m.groupValues[1].trim().uppercase().replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, "_")
	}

	fun accepted(id: String): Boolean = id.startsWith("ESSENCE_") && id.length > "ESSENCE_".length

	fun changes(before: Map<String, String>, now: Map<String, String>): Map<String, String> = now.filter { (k, v) -> before[k] != v }

	fun isEssenceEntry(name: String, lore: List<String>): Boolean =
		idFor(name) != null && lore.joinToString(" ").replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").let { ownLore.containsMatchIn(it) }

	fun logLines(skins: Map<String, String>): List<String> = skins.toSortedMap().map { (k, v) -> "[ApexEssenceSkins] $k=$v" }

	fun json(skins: Map<String, String>): String =
		kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(skins.toSortedMap().mapValues { JsonPrimitive(it.value) }))

	val file: Path get() = exportPath(dev.ashtonite365.apex.Apex.DATA_DIR)

	fun exportPath(dataDir: Path): Path = dataDir.resolve("essence-skins.json")

	@Subscribe
	fun onInventory(event: ChestInventoryUpdateEvent) {
		try { scan(event) } catch (t: Throwable) { log.warn("[Apex] essence skin export skipped: $t") }
	}

	private fun scan(event: ChestInventoryUpdateEvent) {
		val stacks: List<ItemStack> = when (event) {
			is ChestInventoryUpdateEvent.Single -> listOf(event.stack)
			is ChestInventoryUpdateEvent.Multi -> event.contents
		}
		var changed = false
		for (s in stacks) {
			if (s.isEmpty) continue
			val name = s.hoverName.bareText
			val lore = s.view().loreAccordingToNbt.map { it.bareText }
			if (!isEssenceEntry(name, lore)) continue
			val id = idFor(name) ?: continue
			if (!accepted(id)) continue
			val learned = LearnedSkullTextures.liveTexture(s) ?: continue
			if (seen[id] != learned.value) { seen[id] = learned.value; changed = true }
			if (LearnedSkullTextures.put(id, learned)) runCatching { ItemCache.forget(id) }
		}
		if (changed && seen != lastExported) export(LinkedHashMap(seen))
	}

	private fun export(skins: Map<String, String>) {
		val diff = changes(lastExported, skins)
		lastExported = skins
		logLines(diff).forEach { log.info(it) }
		try {
			Files.createDirectories(file.parent)
			val tmp = file.resolveSibling("essence-skins.json.tmp")
			Files.writeString(tmp, json(skins))
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		} catch (t: Throwable) {
			log.warn("[Apex] could not write essence-skins.json: $t")
		}
	}
}
