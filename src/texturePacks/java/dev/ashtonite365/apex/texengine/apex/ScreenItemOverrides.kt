/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: item models that apply only inside screens with a matching title (SPEC §2.5).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.item.ItemStack

object ScreenItemOverrides : SimplePreparableReloadListener<List<ScreenItemOverrides.Group>>() {

	val recognisedNamespaces = listOf("firmskyblock")

	private const val FOLDER = "overrides/item"

	class Rule(val model: Identifier, val test: StackTest)

	class Group(val screen: Identifier, val title: TextMatch, val rules: List<Rule>)

	@Volatile
	private var loaded: List<Group> = listOf()

	@Volatile
	private var active: List<Group> = listOf()

	@Volatile
	private var early: CompletableFuture<List<Group>>? = null

	fun startEarly(manager: ResourceManager, executor: Executor) {
		early = CompletableFuture.supplyAsync({ load(manager) }, executor)
	}

	override fun prepareSharedState(state: PreparableReloadListener.SharedState) =
		startEarly(state.resourceManager(), reloadSetupExecutor())

	override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): List<Group> {
		val pending = early
		early = null
		return pending?.join() ?: load(manager)
	}

	fun preparePublic(manager: ResourceManager) = prepare(manager, net.minecraft.util.profiling.InactiveProfiler.INSTANCE)

	override fun apply(groups: List<Group>, manager: ResourceManager, profiler: ProfilerFiller) = use(groups)

	@Volatile
	private var title: Component = Component.empty()

	fun use(groups: List<Group>) {
		loaded = groups
		active = groups.filter { it.title.test(title) }
	}

	fun screenChanged(screenTitle: Component?) {
		title = screenTitle ?: Component.empty()
		active = loaded.filter { it.title.test(title) }
	}

	fun modelFor(stack: ItemStack): Identifier? {
		for (group in active) {
			for (rule in group.rules) if (rule.test.test(stack)) return rule.model
		}
		return null
	}

	fun load(manager: ResourceManager): List<Group> {
		val byScreen = LinkedHashMap<Identifier, MutableList<Rule>>()
		for ((fileId, resource) in PackFiles.list(manager, FOLDER, recognisedNamespaces)) {
			try {
				val o = PackFiles.read(resource).asJsonObject
				val screens = screenIds(o.get("screen") ?: error("missing screen"))
				val model = Identifier.parse(o.get("model")?.asString ?: error("missing model"))
				val rule = Rule(model, Predicates.read(o.get("predicate"), fileId.toString()))
				for (s in screens) byScreen.getOrPut(s) { mutableListOf() }.add(rule)
			} catch (e: Exception) {
				PackFiles.log.warn("Skipping screen item override {}: {}", fileId, e.message)
				dev.ashtonite365.apex.util.trail.Trail.crumb(dev.ashtonite365.apex.util.trail.Crumb.TEXTURE_FILE_SKIPPED, fileId)
			}
		}
		return byScreen.mapNotNull { (screen, rules) ->
			val title = titleFor(manager, screen) ?: return@mapNotNull null
			Group(screen, title, rules)
		}
	}

	private fun screenIds(json: JsonElement): List<Identifier> =
		if (json.isJsonArray) json.asJsonArray.map { Identifier.parse(it.asString) } else listOf(Identifier.parse(json.asString))

	private fun titleFor(manager: ResourceManager, screen: Identifier): TextMatch? {
		val fileId = screen.withPath { "filters/screen/$it.json" }
		val json = PackFiles.readOrNull(manager, fileId, "screen filter (its overrides are dropped)") ?: return null
		return try {
			TextMatch.read(json.asJsonObject.get("title") ?: error("missing title"))
		} catch (e: Exception) {
			PackFiles.log.warn("Bad screen filter {} (its overrides are dropped): {}", fileId, e.message)
			dev.ashtonite365.apex.util.trail.Trail.crumb(dev.ashtonite365.apex.util.trail.Crumb.TEXTURE_FILE_SKIPPED, fileId)
			null
		}
	}
}
