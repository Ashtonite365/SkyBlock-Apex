// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources
import net.fabricmc.fabric.impl.resource.pack.ModPackResourcesSorter
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModMetadata
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.jvm.optionals.getOrNull
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.FallbackResourceManager
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceMetadata
import net.minecraft.util.FileUtil
import dev.ashtonite365.apex.Apex

class RepoModResourcePack(val basePath: Path)
	: ModPackResources {
	companion object Companion {
		private const val NAMESPACE = "neurepo"

		private const val PACK_META = """{"pack": {"pack_format": 12, "description": "item repo Resources"}}"""

		private fun onRepo() = RepoModResourcePack(RepoDownloadManager.repoSavedLocation)

		fun append(
			packs: ModPackResourcesSorter,
		) {
			Apex.logger.info("Adding the item repo as a mod resource pack")
			packs.addPack(onRepo())
		}

		fun createResourceDirectly(
			identifier: Identifier,
		): Optional<Resource> {
			val pack = onRepo()
			val body = pack.getResource(PackType.CLIENT_RESOURCES, identifier) ?: return Optional.empty()
			val sidecar = identifier.withPath(identifier.path + ".mcmeta")
			return Optional.of(Resource(pack, body) {
				pack.getResource(PackType.CLIENT_RESOURCES, sidecar)?.let(FallbackResourceManager::parseMetadata) ?: ResourceMetadata.EMPTY
			})
		}
	}

	private val metadata: ResourceMetadata by lazy { ResourceMetadata.fromJsonStream(PACK_META.byteInputStream()) }

	private fun serves(type: PackType) = type == PackType.CLIENT_RESOURCES

	fun getFile(
		segments: Array<out String>,
	): Path? {
		segments.let { FileUtil.validatePath(*it) }
		var at = basePath
		for (part in segments) at = at.resolve(part)
		return at.takeIf { it.isRegularFile() }
	}

	override fun getRootResource(vararg segments: String): IoSupplier<InputStream>? = getFile(segments)?.let(IoSupplier<InputStream>::create)

	override fun getResource(
		type: PackType, id: Identifier,
	): IoSupplier<InputStream>? {
		if (!serves(type) || id.namespace != NAMESPACE) return null
		return getFile(id.path.split("/").toTypedArray())?.let(IoSupplier<InputStream>::create)
	}

	override fun listResources(type: PackType, namespace: String, prefix: String, consumer: PackResources.ResourceOutput) {
		if (namespace != NAMESPACE || !serves(type)) return
		val root = basePath.resolve(prefix)
		if (!root.exists()) return
		Files.walk(root).use { walk ->
			for (found in walk.iterator()) {
				val relative = found.relativeTo(basePath)
				val id = Identifier.tryBuild(NAMESPACE, relative.toString()) ?: continue
				consumer.accept(id, IoSupplier.create(relative))
			}
		}
	}

	override fun getNamespaces(type: PackType): Set<String> = if (serves(type)) setOf(NAMESPACE) else emptySet()

	override fun <T : Any> getMetadataSection(metadataSerializer: MetadataSectionType<T>): T? = metadata.getSection(metadataSerializer).getOrNull()

	override fun location(): PackLocationInfo = PackLocationInfo(NAMESPACE, Component.literal("Item repo"), PackSource.BUILT_IN, Optional.empty())

	override fun getFabricModMetadata(): ModMetadata = FabricLoader.getInstance().getModContainer("apex").orElseThrow().metadata

	override fun createOverlay(overlay: String): ModPackResources = RepoModResourcePack(basePath.resolve(overlay))

	override fun close() = Unit
}
