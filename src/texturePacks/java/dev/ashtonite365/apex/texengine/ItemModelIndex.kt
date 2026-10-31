/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: the set of item model ids the game can actually draw. It is rebuilt from each newly
 * baked model set at the moment the game switches to it (ItemModelIndexHook), so lookups need no access to the
 * model manager.
 */
package dev.ashtonite365.apex.texengine

import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.MissingItemModel
import net.minecraft.resources.Identifier

object ItemModelIndex : ItemModelCatalog {

	@Volatile
	private var drawable: Set<Identifier> = emptySet()

	@JvmStatic
	fun rebuild(bakedItemModels: Map<Identifier, ItemModel>) {
		val ids = HashSet<Identifier>(bakedItemModels.size * 2)
		for ((id, model) in bakedItemModels) {
			if (model !is MissingItemModel) ids.add(id)
		}
		drawable = ids
	}

	override fun hasItemModel_Apex(identifier: Identifier): Boolean = identifier in drawable
}
