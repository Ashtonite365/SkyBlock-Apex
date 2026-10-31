/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures, diagnostics. Counts what the tile source, the model wrapping and the renderers
 * actually did, logs one summary line per model reload and shows it as an F3 entry ("apex:connected_textures"),
 * so an in-game test shows at a glance which step is not happening.
 */
package dev.ashtonite365.apex.features.render.connected

import org.apache.logging.log4j.LogManager
import dev.ashtonite365.apex.util.Client
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer
import net.minecraft.client.gui.components.debug.DebugScreenEntries
import net.minecraft.client.gui.components.debug.DebugScreenEntry
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus
import net.minecraft.client.renderer.block.BlockStateModelSet
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

object ConnectedDiag {
	private val LOG = LogManager.getLogger("Apex")
	val DEBUG_ENTRY: Identifier = Identifier.fromNamespaceAndPath("apex", "connected_textures")

	@Volatile var sourceRuns = 0; private set
	@Volatile var texturesQueued = 0; private set
	@Volatile var texturesWithoutFile = 0; private set
	@Volatile var spritesQueued = 0; private set
	val spritesGenerated = AtomicInteger()
	val spritesFailed = AtomicInteger()

	@Volatile var reloads = 0; private set
	val modelsWrapped = AtomicInteger()
	val modelsSkipped = AtomicInteger()
	val texturesResolved = AtomicInteger()
	val texturesUnresolved = AtomicInteger()
	val problems = ConcurrentHashMap<String, String>()

	@Volatile var liveWrapped = 0; private set
	@Volatile var liveStates = 0; private set
	@Volatile var liveOuter: String = ""; private set

	val nearBlocks = LongAdder()
	val nearRemapped = LongAdder()

	fun sourceRan(queuedTextures: Int, missingFiles: Int, queuedSprites: Int) {
		sourceRuns++
		texturesQueued = queuedTextures; texturesWithoutFile = missingFiles; spritesQueued = queuedSprites
		spritesGenerated.set(0); spritesFailed.set(0); bands.clear()
		LOG.info("Connected textures: tile source queued $queuedSprites sprites for $queuedTextures textures ($missingFiles textures not found)")
	}

	fun spriteFailed(id: Identifier, e: Throwable?) {
		if (spritesFailed.incrementAndGet() <= 5) LOG.warn("Connected textures: could not make tile sprite $id", e)
	}

	val bands = ConcurrentHashMap<String, String>()

	fun bandsMeasured(texture: Identifier, size: Int, b: IntArray) {
		bands[texture.toString()] = "${size}px t${b[TileArt.TOP]} r${b[TileArt.RIGHT]} b${b[TileArt.BOTTOM]} l${b[TileArt.LEFT]}"
	}

	fun reloadStarted() {
		reloads++
		modelsWrapped.set(0); modelsSkipped.set(0); texturesResolved.set(0); texturesUnresolved.set(0)
		problems.clear()
	}

	fun modelWrapped() { modelsWrapped.incrementAndGet() }
	fun modelSkipped(state: BlockState) { modelsSkipped.incrementAndGet() }

	fun textureResolved(texture: String, tiles: TextureTiles?) {
		if (tiles != null) texturesResolved.incrementAndGet() else texturesUnresolved.incrementAndGet()
	}

	fun textureProblem(texture: String, why: String) {
		if (problems.put(texture, why) == null) LOG.warn("Connected textures: $texture: $why")
	}

	fun scan(set: BlockStateModelSet): Triple<Int, Int, Set<String>> {
		var wrapped = 0
		var states = 0
		val outer = LinkedHashSet<String>()
		val blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK
		for (block in blocks) {
			if (ConnectRules.of(block) == null) continue
			for (state in block.stateDefinition.possibleStates) {
				states++
				val m = set.get(state)
				if (findConnected(m) != null) {
					wrapped++
					if (m !is ConnectedModel) outer.add(m.javaClass.name)
				}
			}
		}
		return Triple(wrapped, states, outer)
	}

	private val wrappedField by lazy {
		runCatching { WrapperBlockStateModel::class.java.getDeclaredField("wrapped").apply { isAccessible = true } }.getOrNull()
	}

	@JvmStatic
	fun findConnected(model: BlockStateModel?): ConnectedModel? {
		var m = model
		repeat(8) {
			when (m) {
				is ConnectedModel -> return m as ConnectedModel
				is WrapperBlockStateModel -> m = wrappedField?.get(m) as? BlockStateModel ?: return null
				else -> return null
			}
		}
		return null
	}

	private var lastSet: BlockStateModelSet? = null
	private var overlayShown = false

	fun onTick() {
		val set = runCatching { Client.game.modelManager.blockStateModelSet }.getOrNull() ?: return
		if (set === lastSet) return
		lastSet = set
		val (wrapped, states, outer) = scan(set)
		liveWrapped = wrapped; liveStates = states; liveOuter = outer.joinToString(", ")
		LOG.info(summary() + if (problems.isEmpty()) "" else " problems: " + problems.entries.take(6).joinToString("; ") { "${it.key}: ${it.value}" })
		if (bands.isNotEmpty()) LOG.info("Connected textures: block border bands: " + bands.entries.sortedBy { it.key }.joinToString(", ") { "${it.key.removePrefix("minecraft:block/")} ${it.value}" })
		if (!overlayShown) {
			overlayShown = true
			runCatching {
				val entries = Client.game.debugEntries
				if (entries.getStatus(DEBUG_ENTRY) == DebugScreenEntryStatus.NEVER) entries.setStatus(DEBUG_ENTRY, DebugScreenEntryStatus.IN_OVERLAY)
			}
		}
	}

	fun summary(): String =
		"Connected textures: glass ${onOff(ConnectedTextures.glassEnabled)}, blocks ${onOff(ConnectedTextures.blocksEnabled)}; " +
			"rules ${ConnectRules.blockIds.size} blocks; tile source runs $sourceRuns, sprites $spritesQueued queued / " +
			"${spritesGenerated.get()} generated / ${spritesFailed.get()} failed ($texturesQueued textures, $texturesWithoutFile not found); " +
			"wrapped models ${modelsWrapped.get()} (skipped ${modelsSkipped.get()}, textures ${texturesResolved.get()} ok / ${texturesUnresolved.get()} without tiles); " +
			"live $liveWrapped/$liveStates states" + (if (liveOuter.isEmpty()) "" else " (wrapped by $liveOuter)") + "; far terrain: " + (if (ConnectedTextures.farEnabled()) "connected (${FarConnect.blocks.sum()} blocks, ${FarConnect.remapped.sum()} quads)" else "plain")

	private fun onOff(b: Boolean) = if (b) "on" else "off"

	private var f3Line = ""
	private var f3BuiltAt = 0L
	const val F3_REFRESH_MS = 250L

	fun f3Line(nowMs: Long): String {
		if (f3Line.isEmpty() || nowMs - f3BuiltAt >= F3_REFRESH_MS || nowMs < f3BuiltAt) {
			f3BuiltAt = nowMs
			f3Line = "Apex connected: glass ${onOff(ConnectedTextures.glassEnabled)} blocks ${onOff(ConnectedTextures.blocksEnabled)} | " +
				"tiles ${spritesGenerated.get()}/$spritesQueued | models $liveWrapped/$liveStates | near ${nearBlocks.sum()} blocks, ${nearRemapped.sum()} quads | far ${if (ConnectedTextures.farEnabled()) "connected ${FarConnect.blocks.sum()} blocks, ${FarConnect.remapped.sum()} quads" else "plain ${FarPlain.plainBlocks.sum()} blocks"}"
		}
		return f3Line
	}

	private val entry = object : DebugScreenEntry {
		override fun display(displayer: DebugScreenDisplayer, level: Level?, clientChunk: LevelChunk?, serverChunk: LevelChunk?) {
			displayer.addLine(f3Line(System.currentTimeMillis()))
		}
	}

	fun registerDebugEntry() {
		runCatching { DebugScreenEntries.register(DEBUG_ENTRY, entry) }
			.onFailure { LOG.warn("Connected textures: could not add the F3 entry", it) }
	}
}
