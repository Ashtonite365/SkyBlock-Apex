/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: a read-only view of a server resource pack that only shows its item files. Original implementation.
 */
package dev.ashtonite365.apex.features.misc.oldlook

import java.io.InputStream
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.resources.IoSupplier

class ItemsOnlyPackResources(private val delegate: PackResources) : PackResources {
	private fun visible(type: PackType, id: Identifier) =
		type != PackType.CLIENT_RESOURCES || OldLookPolicy.itemsOnlyServes(id.namespace, id.path)

	override fun getResource(type: PackType, id: Identifier): IoSupplier<InputStream>? =
		if (visible(type, id)) delegate.getResource(type, id) else null

	override fun listResources(type: PackType, namespace: String, path: String, output: PackResources.ResourceOutput) {
		if (type != PackType.CLIENT_RESOURCES) return delegate.listResources(type, namespace, path, output)
		if (namespace != OldLookPolicy.HYPIXEL_NAMESPACE && namespace != "minecraft") return
		delegate.listResources(type, namespace, path) { id, supplier -> if (visible(type, id)) output.accept(id, supplier) }
	}

	override fun getNamespaces(type: PackType): Set<String> {
		val all = delegate.getNamespaces(type)
		if (type != PackType.CLIENT_RESOURCES) return all
		return all.filterTo(HashSet()) { it == OldLookPolicy.HYPIXEL_NAMESPACE || it == "minecraft" }
	}

	override fun location(): PackLocationInfo = delegate.location()
	override fun getRootResource(vararg path: String): IoSupplier<InputStream>? = delegate.getRootResource(*path)
	override fun <T : Any> getMetadataSection(type: MetadataSectionType<T>): T? = delegate.getMetadataSection(type)
	override fun close() = delegate.close()
}
