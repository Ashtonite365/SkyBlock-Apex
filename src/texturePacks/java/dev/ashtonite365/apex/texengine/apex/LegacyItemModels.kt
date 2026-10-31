/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: item definitions made from old-style `models/item/<name>.json` files (SPEC §2.2).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonObject
import java.util.Optional
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager

object LegacyItemModels {

	val recognisedNamespaces = listOf("firmskyblock", "cittofirmgenerated")

	val headModelKeys = listOf("apex:head_model", "firmament:head_model")

	private const val FOLDER = "models/item"
	private val properties = ClientItem.Properties(true, true, 1f)

	private fun plainModel(id: Identifier): ItemModel.Unbaked = CuboidItemModelWrapper.Unbaked(id, Optional.empty(), listOf())

	fun addTo(manager: ResourceManager, loaded: Map<Identifier, ClientItem>): Map<Identifier, ClientItem> {
		if (!settings.legacyPaths) return loaded
		val files = PackFiles.list(manager, FOLDER, recognisedNamespaces)
		if (files.isEmpty()) return loaded
		val packOrder = manager.listPacks().toList()
		val result = HashMap(loaded)
		for ((fileId, resource) in files) {
			val name = PackFiles.nameIn(FOLDER, fileId)
			val itemId = Identifier.fromNamespaceAndPath(fileId.namespace, name)
			if (!legacyWins(manager, itemId, resource, packOrder)) continue
			result[itemId] = ClientItem(definitionFor(itemId, resource), properties)
		}
		return result
	}

	private fun legacyWins(manager: ResourceManager, itemId: Identifier, legacy: Resource, packOrder: List<PackResources>): Boolean {
		val real = manager.getResource(itemId.withPath { "items/$it.json" }).orElse(null) ?: return true
		return packOrder.indexOf(legacy.source()) > packOrder.indexOf(real.source())
	}

	private fun definitionFor(itemId: Identifier, resource: Resource): ItemModel.Unbaked {
		val base = plainModel(itemId.withPath { "item/$it" })
		val json = try {
			PackFiles.read(resource).asJsonObject
		} catch (e: Exception) {
			PackFiles.log.warn("Could not read legacy item model {}: {}", itemId, e.message)
			dev.ashtonite365.apex.util.trail.Trail.crumb(dev.ashtonite365.apex.util.trail.Crumb.TEXTURE_FILE_SKIPPED, itemId)
			return base
		}
		return fromModelJson(json, base)
	}

	fun fromModelJson(json: JsonObject, base: ItemModel.Unbaked): ItemModel.Unbaked {
		var model = base
		val overrides = json.get("overrides")
		if (overrides != null && overrides.isJsonArray) {
			val entries = overrides.asJsonArray.mapNotNull { e ->
				if (!e.isJsonObject) return@mapNotNull null
				val o = e.asJsonObject
				val target = o.get("model")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
					?.let { Identifier.tryParse(it.asString) } ?: return@mapNotNull null
				PredicateModel.Entry(plainModel(target), Optional.ofNullable(o.get("predicate")))
			}
			model = PredicateModel.Unbaked(model, entries)
		}
		val head = headModelKeys.firstNotNullOfOrNull { key ->
			json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.let { Identifier.tryParse(it.asString) }
		}
		if (head != null) model = HeadModel.Unbaked(plainModel(head), model)
		return model
	}
}
