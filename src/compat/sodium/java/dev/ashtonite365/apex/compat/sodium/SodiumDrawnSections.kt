/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain's drawn-list reader for the Sodium terrain renderer (optional source set, loaded
 * only when Sodium is installed; see SurfaceDrawnSources). Original Apex code: it only calls Sodium's public API
 * (the render lists Sodium's section manager hands its terrain draw for this frame) through Apex's own accessor.
 *
 * Facts used (Sodium 0.9.x, from its public class signatures): the world renderer's section manager returns this
 * frame's render lists, one list per render region (8 x 4 x 8 sections); each list iterates the local indices of the
 * region's sections it draws with geometry, and of those it draws block entities for; a local index unpacks to the
 * section offset inside the region, and the region gives its first section's coordinates.
 */
package dev.ashtonite365.apex.compat.sodium

import com.google.auto.service.AutoService
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnSource
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceSectionSink
import dev.ashtonite365.apex.mixins.sodium.accessor.AccessorSodiumWorldRenderer
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator
import net.fabricmc.loader.api.FabricLoader

@AutoService(SurfaceDrawnSource::class)
class SodiumDrawnSections : SurfaceDrawnSource {
	override fun unsupportedReason(): String? {
		val version = FabricLoader.getInstance().getModContainer("sodium").map { it.metadata.version.friendlyString }.orElse(null)
			?: return "renderer version unknown"
		return if (TESTED.any { version.startsWith(it) }) null else "untested renderer version $version"
	}

	override fun collect(sink: SurfaceSectionSink): Boolean {
		val renderer = SodiumWorldRenderer.instanceNullable() ?: return false

		val access = renderer as? AccessorSodiumWorldRenderer ?: throw IllegalStateException("render-list accessor not applied")
		val manager = access.renderSectionManager_apex ?: return false
		val lists = manager.renderLists ?: return false
		val it = lists.iterator(false)
		while (it.hasNext()) {
			val list = it.next()
			val region = list.region
			val rx = region.chunkX; val ry = region.chunkY; val rz = region.chunkZ
			emit(list.sectionsWithGeometryIterator(false), rx, ry, rz, sink)
			emit(list.sectionsWithEntitiesIterator(), rx, ry, rz, sink)
		}
		return true
	}

	private fun emit(indices: ByteIterator?, rx: Int, ry: Int, rz: Int, sink: SurfaceSectionSink) {
		if (indices == null) return
		while (indices.hasNext()) {
			val i = indices.nextByteAsInt()
			sink.drawn(rx + LocalSectionIndex.unpackX(i), ry + LocalSectionIndex.unpackY(i), rz + LocalSectionIndex.unpackZ(i))
		}
	}

	private companion object {

		val TESTED = listOf("0.9.")
	}
}
