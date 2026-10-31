/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: item models (SPEC §1, §2.1–§2.5, §7).
 */
package dev.ashtonite365.apex.texengine.apex

import dev.ashtonite365.apex.texengine.ItemModels
import dev.ashtonite365.apex.texengine.ItemModelCatalog
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.mc.view
import java.util.IdentityHashMap
import java.util.WeakHashMap
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object ApexItems : ItemModels {
	override fun overrideFor(stack: ItemStack, models: ItemModelCatalog): Identifier? {
		val key: Any = if (isSimple(stack)) stack.item else stack
		synchronized(lock) {
			if (key is Item) byItem[key]?.let { return it.id }
			else byStack[stack]?.let { return it.id }
		}
		val result = Cached(compute(stack, models))
		synchronized(lock) {
			if (key is Item) byItem[key] = result else byStack[stack] = result
		}
		return result.id
	}

	fun compute(stack: ItemStack, models: ItemModelCatalog): Identifier? {
		val screenModel = ScreenItemOverrides.modelFor(stack)
		if (screenModel != null && models.hasItemModel_Apex(screenModel)) return screenModel
		if (!settings.enabled) return null
		val id = stack.view().repoItemId ?: return null
		return ItemModelIds.forSkyBlockId(id.skyblockId)
	}

	override fun extraItemDefinitions(manager: ResourceManager, loaded: Map<Identifier, ClientItem>): Map<Identifier, ClientItem> =
		LegacyItemModels.addTo(manager, loaded)

	override fun openWornHeadPass() = WornHeadPass.open()

	override fun closeWornHeadPass(): Boolean = WornHeadPass.close()

	private class Cached(val id: Identifier?)

	private val lock = Any()
	private val byStack = WeakHashMap<ItemStack, Cached>()
	private val byItem = IdentityHashMap<Item, Cached>()

	private fun isSimple(stack: ItemStack): Boolean {
		val patch = stack.componentsPatch
		if (patch.isEmpty) return true
		if (patch.size() != 1) return false

		val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
		return data.isEmpty && !stack.item.components().has(DataComponents.CUSTOM_DATA)
	}

	fun clearCache() {
		synchronized(lock) {
			byStack.clear()
			byItem.clear()
		}
	}
}
