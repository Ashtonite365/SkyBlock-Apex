/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures, the block-atlas sprite source (`apex:connected_tiles`, listed in
 * assets/minecraft/atlases/blocks.json). On every resource reload it makes the tile sprites from the textures that are
 * loaded at that moment (vanilla or any resource pack), so Apex ships no tile art and packs re-texture the tiles too.
 */
package dev.ashtonite365.apex.features.render.connected

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.serialization.MapCodec
import java.util.Optional
import net.minecraft.client.renderer.texture.SpriteContents
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader
import net.minecraft.client.renderer.texture.atlas.SpriteSource
import net.minecraft.client.resources.metadata.animation.FrameSize
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager

object ConnectedTilesSource : SpriteSource {
	val ID: Identifier = Identifier.fromNamespaceAndPath("apex", "connected_tiles")
	val CODEC: MapCodec<ConnectedTilesSource> = MapCodec.unit(this)

	@Volatile var generated: Map<String, Int> = emptyMap()
		private set

	fun tileId(texture: String, tile: Int): Identifier {
		val id = Identifier.parse(texture)
		return Identifier.fromNamespaceAndPath("apex", "block/connected/${id.namespace}/${id.path.removePrefix("block/")}/$tile")
	}

	override fun run(resourceManager: ResourceManager, output: SpriteSource.Output) {
		val done = LinkedHashMap<String, Int>()
		var missing = 0
		var sprites = 0
		for ((texture, plane) in ConnectRules.textures) {
			val id = Identifier.tryParse(texture) ?: continue
			val res = resourceManager.getResource(SpriteSource.TEXTURE_ID_CONVERTER.idToFile(id)).orElse(null)
			if (res == null) { missing++; continue }
			val frame = SharedFrame(res, id, ConnectRules.styleOf(texture))
			for (t in ConnectTiles.reachable(plane)) {
				if (t == 0) continue
				val tid = tileId(texture, t)
				output.add(tid, TileLoader(frame, tid, ConnectTiles.keyOfTile(t)))
				sprites++
			}
			done[texture] = plane
		}
		generated = done
		ConnectedDiag.sourceRan(done.size, missing, sprites)
	}

	override fun codec(): MapCodec<out SpriteSource> = CODEC

	private class SharedFrame(private val res: Resource, private val texture: Identifier, val style: TileStyle) {
		private var loaded = false
		var size = 0
		var pixels: IntArray? = null

		var bands: IntArray? = null

		var field: TileArt.SurfaceField? = null
		var meta: Optional<TextureMetadataSection> = Optional.empty()

		@Synchronized
		fun get(): IntArray? {
			if (loaded) return pixels
			loaded = true
			try {
				meta = runCatching { res.metadata().getSection(TextureMetadataSection.TYPE) }.getOrDefault(Optional.empty())
				res.open().use { NativeImage.read(it) }.use { img ->
					val s = minOf(img.width, img.height)
					size = s
					val px = IntArray(s * s) { i -> img.getPixel(i % s, i / s) }
					if (style == TileStyle.SURFACE) {
						val b = TileArt.bands(px, s).also { ConnectedDiag.bandsMeasured(texture, s, it) }
						bands = b
						field = TileArt.surfaceField(px, s, b)
					}
					pixels = px
				}
			} catch (e: Throwable) {
				ConnectedDiag.spriteFailed(texture, e)
			}
			return pixels
		}
	}

	private class TileLoader(private val frame: SharedFrame, private val id: Identifier, private val key: Int) : SpriteSource.DiscardableLoader {
		override fun get(loader: SpriteResourceLoader): SpriteContents? {
			val src = frame.get() ?: return null
			return try {
				val s = frame.size
				val px = TileArt.tile(frame.style, src, s, key, frame.bands, frame.field)
				val img = NativeImage(s, s, false)
				for (y in 0 until s) for (x in 0 until s) img.setPixel(x, y, px[y * s + x])
				SpriteContents(id, FrameSize(s, s), img, Optional.empty(), emptyList(), frame.meta).also { ConnectedDiag.spritesGenerated.incrementAndGet() }
			} catch (e: Throwable) {
				ConnectedDiag.spriteFailed(id, e)
				null
			}
		}
	}
}
