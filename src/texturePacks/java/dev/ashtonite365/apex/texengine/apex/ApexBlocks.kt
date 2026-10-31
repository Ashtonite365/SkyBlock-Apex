/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: per-island block replacements (SPEC §2.7).
 *
 * Lookups run on the chunk-builder threads (vanilla, Sodium), the far-terrain worker and the main thread, once
 * per block, so the data they read is built once per reload and never changed afterwards: each island gets a [Table], a
 * flat two-level array indexed by block-state id, and the island in use is published through one volatile field. A
 * lookup is a volatile read, the state's id, two array reads and (with areas) a few int compares; it allocates nothing.
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import dev.ashtonite365.apex.texengine.BlockModels
import dev.ashtonite365.apex.util.trail.Crumb
import dev.ashtonite365.apex.util.trail.Trail
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.function.Function
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher
import net.minecraft.client.renderer.block.dispatch.SingleVariant
import net.minecraft.client.renderer.block.dispatch.Variant
import net.minecraft.client.resources.model.BlockStateModelLoader
import net.minecraft.client.resources.model.ModelBaker
import net.minecraft.client.resources.model.ModelDiscovery
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition

object ApexBlocks : BlockModels {

	val recognisedNamespaces = listOf("firmskyblock")

	private const val FOLDER = "overrides/blocks"

	class Entry(
		val modes: List<String>,
		val block: Block,
		val replacement: Identifier,
		val sound: Identifier?,
		val areas: IntArray?,
		val rough: IntArray?,
	)

	class Parsed(val entries: List<Entry>) {
		companion object {
			val EMPTY = Parsed(listOf())
		}
	}

	class Baked(val single: Map<Identifier, BlockStateModel>, val perState: Map<StateMapKey, Map<BlockState, BlockStateModel>>)

	data class StateMapKey(val block: Block, val replacement: Identifier)

	class Rule(
		val entries: Array<Entry>,
		val models: Array<BlockStateModel?>,
	) {

		fun pick(x: Int, y: Int, z: Int): Int {
			for (k in entries.indices) {
				val e = entries[k]
				val areas = e.areas ?: return k
				val r = e.rough
				if (r != null && !inside(r, 0, x, y, z)) continue
				var i = 0
				while (i < areas.size) {
					if (inside(areas, i, x, y, z)) return k
					i += 6
				}
			}
			return -1
		}
	}

	internal fun inside(b: IntArray, o: Int, x: Int, y: Int, z: Int) =
		x >= b[o] && y >= b[o + 1] && z >= b[o + 2] && x <= b[o + 3] && y <= b[o + 4] && z <= b[o + 5]

	class Table(private val pages: Array<Array<Rule?>?>, val entries: List<Entry>) {
		fun rule(state: BlockState): Rule? {
			val id = Block.getId(state)
			val page = id ushr PAGE_BITS
			if (page >= pages.size) return null
			return pages[page]?.get(id and PAGE_MASK)
		}
	}

	private const val PAGE_BITS = 8
	private const val PAGE_MASK = (1 shl PAGE_BITS) - 1

	class Loaded(val byMode: Map<String, Table>) {
		companion object {
			val EMPTY = Loaded(mapOf())
		}
	}

	@Volatile
	private var loaded = Loaded.EMPTY

	@Volatile
	private var current: Table? = null

	override fun modelFor(state: BlockState, pos: BlockPos?): BlockStateModel? {
		val rule = (current ?: return null).rule(state) ?: return null
		if (pos == null) return if (Fallback.active()) null else rule.models[0]
		val k = rule.pick(pos.x, pos.y, pos.z)
		return if (k < 0) null else rule.models[k]
	}

	fun entryFor(state: BlockState, pos: BlockPos?): Entry? {
		val rule = (current ?: return null).rule(state) ?: return null
		if (pos == null) return if (Fallback.active()) null else rule.entries[0]
		val k = rule.pick(pos.x, pos.y, pos.z)
		return if (k < 0) null else rule.entries[k]
	}

	override fun soundFor(state: BlockState, pos: BlockPos?): Identifier? = entryFor(state, pos)?.sound

	private object Fallback {
		private val depth = ThreadLocal.withInitial { IntArray(1) }
		fun active() = depth.get()[0] > 0
		fun enter() { depth.get()[0]++ }
		fun exit() { depth.get()[0]-- }
	}

	override fun enterFallback() = Fallback.enter()
	override fun exitFallback() = Fallback.exit()

	@Volatile
	var onTableChanged: () -> Unit = ::requestRebuild

	fun useIsland(mode: String?) {
		val table = if (mode != null && settings.blockOverrides) loaded.byMode[mode] else null
		if (table === current) return
		current = table
		onTableChanged()
	}

	val currentTable: Table? get() = current

	private val sodiumReloader: Runnable? by lazy {
		runCatching {
			Class.forName("dev.ashtonite365.apex.compat.sodium.SodiumTerrainNudge").getConstructor().newInstance() as Runnable
		}.onFailure {
			if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) PackFiles.log.error("Block replacements: no Sodium terrain nudge: {}", it.toString())
		}.getOrNull()
	}

	private fun requestRebuild() {
		dev.ashtonite365.apex.util.Client.onNextTick {
			runCatching { net.minecraft.client.Minecraft.getInstance().levelExtractor.allChanged() }
				.onFailure { PackFiles.log.warn("Block replacements: chunk rebuild failed: {}", it.toString()) }
			runCatching { sodiumReloader?.run() }
			dev.ashtonite365.apex.util.render.BlockModelChanges.bump()
		}
	}

	fun parse(manager: ResourceManager): Parsed {
		val out = ArrayList<Entry>()
		for ((fileId, resource) in PackFiles.list(manager, FOLDER, recognisedNamespaces)) {
			try {
				out += entriesFrom(fileId, PackFiles.read(resource).asJsonObject)
			} catch (e: Exception) {
				PackFiles.log.warn("Skipping block replacement file {}: {}", fileId, e.message)
				Trail.crumb(Crumb.TEXTURE_FILE_SKIPPED, fileId)
			}
		}
		return Parsed(out)
	}

	private fun entriesFrom(fileId: Identifier, o: JsonObject): List<Entry> {
		val modes = when (val m = o.get("modes") ?: badPack("missing modes")) {
			is com.google.gson.JsonArray -> m.map { it.asString }
			else -> listOf(m.asString)
		}
		var areas: IntArray? = null
		o.get("area")?.takeUnless { it.isJsonNull }?.let { a ->
			val list = a.asJsonArray
			val flat = IntArray(list.size() * 6)
			for ((i, box) in list.withIndex()) {
				val b = box.asJsonObject
				val min = corner(b.get("min") ?: badPack("area without min"))
				val max = corner(b.get("max") ?: badPack("area without max"))
				for (axis in 0 until 3) {
					flat[i * 6 + axis] = minOf(min[axis], max[axis])
					flat[i * 6 + 3 + axis] = maxOf(min[axis], max[axis])
				}
			}
			areas = flat
		}
		val rough = areas?.takeIf { it.size >= 18 }?.let(::around)
		val replacements = (o.get("replacements") ?: badPack("missing replacements")).asJsonObject

		val read = replacements.entrySet().map { (key, value) ->
			val original = Identifier.parse(key)
			val (to, sound) = replacementOf(value)
			Triple(original, to, sound)
		}
		val out = ArrayList<Entry>(read.size)
		for ((original, to, sound) in read) {
			val block = BuiltInRegistries.BLOCK.getOptional(original).orElse(null)
			if (block == null) {
				PackFiles.log.warn("Block replacement file {}: unknown block '{}', entry skipped", fileId, original)
				continue
			}
			out += Entry(modes, block, to, sound, areas, rough)
		}
		return out
	}

	private fun corner(e: JsonElement): IntArray {
		val a = e.asJsonArray
		if (a.size() != 3) badPack("a corner needs three numbers")
		return IntArray(3) { a[it].asInt }
	}

	private fun around(areas: IntArray): IntArray {
		val r = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
		var i = 0
		while (i < areas.size) {
			for (axis in 0 until 3) {
				r[axis] = minOf(r[axis], areas[i + axis])
				r[3 + axis] = maxOf(r[3 + axis], areas[i + 3 + axis])
			}
			i += 6
		}
		return r
	}

	private fun replacementOf(value: JsonElement): Pair<Identifier, Identifier?> {
		if (value.isJsonPrimitive) return Identifier.parse(value.asString) to null
		val o = value.asJsonObject
		val block = Identifier.parse((o.get("block") ?: badPack("replacement without block")).asString)
		val sound = o.get("sound")?.takeUnless { it.isJsonNull }?.let { Identifier.parse(it.asString) }
		return block to sound
	}

	fun build(parsed: Parsed, baked: Baked?): Loaded {
		val modes = LinkedHashSet<String>()
		for (e in parsed.entries) modes += e.modes
		val shared = HashMap<List<Entry>, Table>()
		val byMode = HashMap<String, Table>()
		for (mode in modes) {
			val entries = parsed.entries.filter { mode in it.modes }
			byMode[mode] = shared.getOrPut(entries) { table(entries, baked) }
		}
		return Loaded(byMode)
	}

	private fun table(entries: List<Entry>, baked: Baked?): Table {
		val byBlock = LinkedHashMap<Block, MutableList<Entry>>()
		for (e in entries) byBlock.getOrPut(e.block, ::ArrayList) += e
		val rules = HashMap<Int, Rule>()
		for ((block, list) in byBlock) {
			val cut = list.indexOfFirst { it.areas == null }.let { if (it < 0) list else list.subList(0, it + 1) }.toTypedArray()
			val perStateMaps = cut.map { e -> baked?.perState?.get(StateMapKey(block, e.replacement)) }
			val anyPerState = perStateMaps.any { it != null }
			var sharedRule: Rule? = null
			for (state in block.stateDefinition.possibleStates) {
				val rule = if (!anyPerState && sharedRule != null) sharedRule else Rule(cut, Array(cut.size) { k ->
					val map = perStateMaps[k]
					if (map != null) map[state] else baked?.single?.get(cut[k].replacement)
				})
				sharedRule = rule
				rules[Block.getId(state)] = rule
			}
		}
		val maxId = rules.keys.maxOrNull() ?: -1
		val pages = arrayOfNulls<Array<Rule?>>((maxId ushr PAGE_BITS) + 1)
		for ((id, rule) in rules) {
			val page = pages[id ushr PAGE_BITS] ?: arrayOfNulls<Rule>(PAGE_MASK + 1).also { pages[id ushr PAGE_BITS] = it }
			page[id and PAGE_MASK] = rule
		}
		return Table(pages, entries)
	}

	class Reload(val parsed: CompletableFuture<Parsed>) {
		@Volatile
		var stateMaps: Map<StateMapKey, Map<BlockState, BlockStateModel.UnbakedRoot>> = mapOf()
		val singles = ConcurrentHashMap<Identifier, SingleVariant.Unbaked>()
		fun single(id: Identifier): SingleVariant.Unbaked = singles.computeIfAbsent(id) { SingleVariant.Unbaked(Variant(it.withPrefix("block/"))) }

		val baked = CompletableFuture<Baked>()
	}

	private const val BAKE_WAIT_MINUTES = 5L

	@Volatile

	var reload = Reload(CompletableFuture.completedFuture(Parsed.EMPTY)).also { it.baked.complete(Baked(mapOf(), mapOf())) }

	fun startEarly(manager: ResourceManager, executor: Executor) {
		reload = Reload(CompletableFuture.supplyAsync({
			try {
				parse(manager)
			} catch (e: Exception) {
				PackFiles.log.error("Block replacements: could not list block files, none this reload", e)
				Parsed.EMPTY
			}
		}, executor))
	}

	override fun loadBlockStates(
		original: CompletableFuture<Map<Identifier, List<Resource>>>,
		stateDefinitions: Function<Identifier, StateDefinition<Block, BlockState>?>,
		executor: Executor,
	): CompletableFuture<Map<Identifier, List<Resource>>> {
		val r = reload
		return original.thenCombineAsync(r.parsed, { files, parsed ->
			r.stateMaps = stateMaps(parsed, files, stateDefinitions)
			files
		}, executor)
	}

	private fun stateMaps(
		parsed: Parsed,
		files: Map<Identifier, List<Resource>>,
		stateDefinitions: Function<Identifier, StateDefinition<Block, BlockState>?>,
	): Map<StateMapKey, Map<BlockState, BlockStateModel.UnbakedRoot>> {
		val out = HashMap<StateMapKey, Map<BlockState, BlockStateModel.UnbakedRoot>>()
		for (e in parsed.entries) {
			val key = StateMapKey(e.block, e.replacement)
			if (key in out) continue
			val resources = files[BlockStateModelLoader.BLOCKSTATE_LISTER.idToFile(e.replacement)] ?: continue
			val blockId = BuiltInRegistries.BLOCK.getKey(e.block)
			val definition = stateDefinitions.apply(blockId) ?: continue
			val loadedFiles = resources.mapNotNull { res ->
				try {
					val dispatcher = BlockStateModelDispatcher.CODEC.parse(JsonOps.INSTANCE, PackFiles.read(res)).getOrThrow()
					BlockStateModelLoader.LoadedBlockStateModelDispatcher(res.sourcePackId(), dispatcher)
				} catch (ex: Exception) {
					PackFiles.log.warn("Skipping blockstate file for block replacement {} from pack {}: {}", e.replacement, res.sourcePackId(), ex.message)
					null
				}
			}
			try {
				out[key] = BlockStateModelLoader.loadBlockStateDefinitionStack(blockId, definition, loadedFiles).models()
			} catch (ex: Exception) {
				PackFiles.log.warn("Could not combine blockstate files for block replacement {}: {}", e.replacement, ex.message)
			}
		}
		return out
	}

	override fun addModelRoots(discovery: ModelDiscovery) {
		val r = reload
		for (e in r.parsed.join().entries) discovery.addRoot(r.single(e.replacement))
		for (map in r.stateMaps.values) for (root in map.values) discovery.addRoot(root)
	}

	override fun bake(baker: ModelBaker, executor: Executor): CompletableFuture<Void?> {
		val r = reload
		return r.parsed.thenComposeAsync({ parsed ->
			val single = ConcurrentHashMap<Identifier, BlockStateModel>()
			val perState = ConcurrentHashMap<StateMapKey, Map<BlockState, BlockStateModel>>()
			val jobs = ArrayList<CompletableFuture<Void>>()
			for (id in parsed.entries.mapTo(LinkedHashSet()) { it.replacement }) jobs += CompletableFuture.runAsync({
				try {
					single[id] = r.single(id).bake(baker)
				} catch (e: Exception) {
					PackFiles.log.warn("Could not bake block replacement model {}: {}", id, e.toString())
				}
			}, executor)
			for ((key, map) in r.stateMaps) jobs += CompletableFuture.runAsync({
				try {
					perState[key] = map.entries.associate { (state, root) -> state to root.bake(state, baker) }
				} catch (e: Exception) {
					PackFiles.log.warn("Could not bake blockstate models of block replacement {}: {}", key.replacement, e.toString())
				}
			}, executor)
			CompletableFuture.allOf(*jobs.toTypedArray()).handle { _, _ ->
				r.baked.complete(Baked(single, perState))
				null
			}
		}, executor).handle { _, e ->

			if (e != null) PackFiles.log.error("Block replacements: baking failed, no replacement models this reload", e)
			r.baked.complete(Baked(mapOf(), mapOf()))
			null
		}
	}

	object Listener : SimplePreparableReloadListener<Loaded>() {

		override fun prepareSharedState(state: PreparableReloadListener.SharedState) =
			startEarly(state.resourceManager(), reloadSetupExecutor())

		override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): Loaded {
			val r = reload
			val parsed = r.parsed.join()

			val baked = try {
				r.baked.get(BAKE_WAIT_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
			} catch (e: java.util.concurrent.TimeoutException) {
				PackFiles.log.error("Block replacements: models were not baked within {} minutes, none this reload", BAKE_WAIT_MINUTES)
				null
			}
			return build(parsed, baked ?: Baked(mapOf(), mapOf()))
		}

		fun preparePublic(manager: ResourceManager) = prepare(manager, net.minecraft.util.profiling.InactiveProfiler.INSTANCE)

		override fun apply(data: Loaded, manager: ResourceManager, profiler: ProfilerFiller) {
			use(data)
			useIsland(dev.ashtonite365.apex.util.SkyBlockSession.skyblockIsland?.serverMode)
		}
	}

	fun use(data: Loaded) {
		loaded = data
	}
}
