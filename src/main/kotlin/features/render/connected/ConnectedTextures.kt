/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures (Render settings "Connected Glass" and "Connected Blocks"). Glass, stained glass,
 * tinted glass and panes, and a set of framed full blocks, draw as one surface with their neighbours in near terrain.
 * Apex Far Terrain draws them plain (see [FarPlain]) unless "Connected textures in far terrain" is on ([FarConnect]). Tiles are generated at runtime from the loaded textures.
 * Written from scratch; the idea is credited in the Credits menu.
 */
package dev.ashtonite365.apex.features.render.connected

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import java.util.concurrent.atomic.AtomicBoolean
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.ModelDebugName
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.client.resources.model.sprite.MaterialBaker
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.BlockState

object ConnectedTextures {
	@Config
	object TConfig : ManagedConfig("connected-textures", Category.RENDER) {

		val connectedGlass by toggle("connected-glass") { true }

		val connectedBlocks by toggle("connected-blocks") { true }

		val connectedFarTerrain by toggle("connected-far-terrain") { false }
	}

	@Volatile private var glassOn = true
	@Volatile private var blocksOn = true
	@Volatile private var farOn = false

	@JvmStatic
	fun enabled(family: ConnectFamily): Boolean = if (family == ConnectFamily.GLASS) glassOn else blocksOn

	@JvmStatic
	fun farEnabled(): Boolean = farOn

	@JvmStatic
	fun farSignature(): Int = if (!farOn) 0 else 1 or (if (glassOn) 2 else 0) or (if (blocksOn) 4 else 0)

	internal fun sync(): Boolean {
		val g = runCatching { TConfig.connectedGlass }.getOrDefault(true)
		val b = runCatching { TConfig.connectedBlocks }.getOrDefault(true)
		farOn = runCatching { TConfig.connectedFarTerrain }.getOrDefault(false)
		if (g == glassOn && b == blocksOn) return false
		glassOn = g; blocksOn = b
		return true
	}

	internal fun setForTest(glass: Boolean, blocks: Boolean, far: Boolean) { glassOn = glass; blocksOn = blocks; farOn = far }

	private val sourceRegistered = AtomicBoolean()

	fun registerSpriteSource() {
		if (sourceRegistered.compareAndSet(false, true)) SpriteSourceRegistry.register(ConnectedTilesSource.ID, ConnectedTilesSource.CODEC)
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		sync()
		registerSpriteSource()
		ConnectedDiag.registerDebugEntry()
		ModelLoadingPlugin.register { ctx ->
			val wrapper = arrayOfNulls<Wrapper>(1)
			ConnectedDiag.reloadStarted()
			ctx.modifyBlockModelAfterBake().register { model, c ->
				if (ConnectRules.of(c.state().block) == null) return@register model
				val w = synchronized(wrapper) {
					val materials = c.baker().materials()
					wrapper[0]?.takeIf { it.materials === materials } ?: Wrapper(materials).also { wrapper[0] = it }
				}
				w.wrap(model, c.state())
			}
		}
	}

	class Wrapper(val materials: MaterialBaker) {
		private val resolved = HashMap<String, TextureTiles?>()

		fun tiles(texture: String, plane: Int): TextureTiles? = synchronized(resolved) {
			resolved.getOrPut(texture) {
				val t = try {
					resolve(materials, texture, plane)
				} catch (e: Throwable) {
					ConnectedDiag.textureProblem(texture, "error: $e")
					null
				}
				ConnectedDiag.textureResolved(texture, t)
				t
			}
		}

		fun wrap(model: BlockStateModel, state: BlockState): BlockStateModel {
			val rule = ConnectRules.of(state.block) ?: return model
			if (model is ConnectedModel) return model
			val textures = rule.textures.mapNotNull { (tex, plane) -> tiles(tex, plane) }
			if (textures.isEmpty()) {
				ConnectedDiag.modelSkipped(state)
				return model
			}
			ConnectedDiag.modelWrapped()
			return ConnectedModel(model, rule, textures.toTypedArray())
		}
	}

	private val DEBUG_NAME = ModelDebugName { "apex connected textures" }

	internal fun resolve(materials: MaterialBaker, texture: String, plane: Int): TextureTiles? {
		fun sprite(id: Identifier): TextureAtlasSprite? =
			materials.get(Material(id), DEBUG_NAME).sprite().takeUnless { it.contents().name() == MissingTextureAtlasSprite.getLocation() }
		val base = sprite(Identifier.parse(texture))
		if (base == null) {
			ConnectedDiag.textureProblem(texture, "base texture not in the block atlas")
			return null
		}
		val tiles = arrayOfNulls<TextureAtlasSprite>(ConnectTiles.TILE_COUNT)
		var found = 0
		var wanted = 0
		for (t in ConnectTiles.reachable(plane)) {
			if (t == 0) continue
			wanted++
			tiles[t] = sprite(ConnectedTilesSource.tileId(texture, t))?.also { found++ }
		}
		if (found == 0) {
			ConnectedDiag.textureProblem(texture, "no tile sprites in the block atlas (0/$wanted)")
			return null
		}
		if (found < wanted) ConnectedDiag.textureProblem(texture, "only $found/$wanted tile sprites in the block atlas")
		return TextureTiles.of(texture, plane, base, tiles)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		ConnectedDiag.onTick()
		if (event.tickCount % 10 != 0) return
		if (sync()) runCatching { Client.game.levelExtractor.allChanged() }
	}

	internal val glassEnabled get() = glassOn
	internal val blocksEnabled get() = blocksOn
}
