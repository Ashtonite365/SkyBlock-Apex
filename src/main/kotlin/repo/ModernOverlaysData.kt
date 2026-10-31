// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import dev.ashtonite365.apex.util.ItemId

class ModernOverlaysData
	: IReloadable {
	data class OverlayFile(val version: Int, val path: Path)

	var overlays: Map<ItemId, List<OverlayFile>> = emptyMap()

	override fun reload(repo: NEURepository): Unit {
		val found = LinkedHashMap<ItemId, MutableList<OverlayFile>>()
		val root = repo.baseFolder.resolve("itemsOverlay")
		if (root.isDirectory()) {
			for (folder in root.listDirectoryEntries()) {
				val dataVersion = folder.fileName.toString().toIntOrNull() ?: continue
				for (file in folder.listDirectoryEntries()) {
					if (file.extension == "snbt")
						found.computeIfAbsent(ItemId(file.nameWithoutExtension)) { ArrayList() } += OverlayFile(dataVersion, file)
				}
			}
		}
		overlays = found
	}

	fun getOverlayFiles(catalogItemId: ItemId): List<OverlayFile> = overlays[catalogItemId].orEmpty()

	fun getMostModernReadableOverlay(catalogItemId: ItemId, version: Int): OverlayFile? {
		var best: OverlayFile? = null
		for (candidate in getOverlayFiles(catalogItemId)) {
			if (candidate.version > version) continue
			if (best == null || candidate.version > best.version) best = candidate
		}
		return best
	}
}
