// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WikiCache(private val dir: Path?, private val ttlMs: Long = 24L * 3600_000, private val clock: () -> Long = System::currentTimeMillis) {
	data class Entry(val title: String, val html: String, val sections: List<Sec> = emptyList(), val anchor: String? = null, val savedAt: Long = 0)
	data class Sec(val level: Int, val title: String, val anchor: String)

	private val mem = ConcurrentHashMap<String, Entry>()
	private fun encode(e: Entry): String = buildJsonObject {
		put("v", 1); put("title", e.title); put("html", e.html); e.anchor?.let { put("anchor", it) }; put("savedAt", e.savedAt)
		put("sections", buildJsonArray { e.sections.forEach { s -> add(buildJsonObject { put("level", s.level); put("title", s.title); put("anchor", s.anchor) }) } })
	}.toString()

	private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
	private fun decode(text: String): Entry {
		val o = Json.parseToJsonElement(text) as JsonObject
		val secs = (o["sections"] as? JsonArray).orEmpty().map { it as JsonObject }.map { Sec((it["level"] as JsonPrimitive).content.toInt(), str(it, "title")!!, str(it, "anchor")!!) }
		return Entry(str(o, "title")!!, str(o, "html")!!, secs, str(o, "anchor"), (o["savedAt"] as JsonPrimitive).content.toLong())
	}

	fun key(title: String) = WikiTitles.normalise(title).lowercase()

	private fun file(title: String): Path? {
		val d = dir ?: return null
		val safe = key(title).map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }.joinToString("").take(80)
		return d.resolve("$safe-${Integer.toHexString(key(title).hashCode())}.json")
	}

	fun get(title: String, allowStale: Boolean = false): Entry? {
		val e = mem[key(title)] ?: runCatching { file(title)?.takeIf(Files::isRegularFile)?.let { decode(Files.readString(it)) } }.getOrNull()?.also { mem[key(title)] = it }
		e ?: return null
		return if (allowStale || clock() - e.savedAt < ttlMs) e else null
	}

	fun put(requested: String, page: WikiApi.Outcome.Page): Entry {
		val e = Entry(page.title, page.html, page.sections.map { Sec(it.level, it.title, it.anchor) }, page.anchor, clock())
		for (k in setOf(requested, page.title)) {
			mem[key(k)] = e
			runCatching {
				val f = file(k) ?: return@runCatching
				f.parent.toFile().mkdirs()
				val tmp = f.resolveSibling(f.fileName.toString() + ".tmp")
				Files.writeString(tmp, encode(e))
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			}
		}
		return e
	}

	fun clearMemory() = mem.clear()

	companion object {
		fun toPage(e: Entry) = WikiApi.Outcome.Page(e.title, e.html, e.sections.map { WikiApi.Section(it.level, it.title, it.anchor) }, e.anchor)
	}
}

class WikiLoaderCore(private val cache: WikiCache, private val fetch: (String) -> WikiApi.Outcome, private val search: (String) -> List<String>) {
	data class Loaded(val page: WikiApi.Outcome.Page, val anchor: String?, val stale: Boolean = false)
	sealed interface Result {
		data class Ok(val loaded: Loaded) : Result
		data class Fail(val message: String) : Result
	}

	fun load(target: WikiTarget): Result {
		val tried = mutableListOf<String>()
		val candidates = (listOf(target.title) + target.alternates).filter { it.isNotBlank() }.distinctBy { cache.key(it) }
		for (t in candidates) {
			tried += t
			when (val r = one(t)) {
				is Step.Hit -> return Result.Ok(Loaded(r.page, anchorFor(r.page, if (t == target.title) target.anchor else null), r.stale))
				is Step.Fail -> return Result.Fail(r.message)
				Step.Missing -> {}
			}
		}
		val q = target.search ?: target.title
		val hits = runCatching { search(q) }.getOrDefault(emptyList()).filter { h -> tried.none { cache.key(it) == cache.key(h) } }
		for (h in hits.take(2)) when (val r = one(h)) {
			is Step.Hit -> return Result.Ok(Loaded(r.page, null, r.stale))
			is Step.Fail -> return Result.Fail(r.message)
			Step.Missing -> {}
		}
		return Result.Fail("There is no wiki page for \"${target.title}\" yet.")
	}

	fun loadTitle(title: String, anchor: String?) = load(WikiTarget(title, anchor, rule = "link"))

	private sealed interface Step {
		data class Hit(val page: WikiApi.Outcome.Page, val stale: Boolean) : Step
		data object Missing : Step
		data class Fail(val message: String) : Step
	}

	private fun one(title: String): Step {
		cache.get(title)?.let { return Step.Hit(WikiCache.toPage(it), false) }
		return when (val o = runCatching { fetch(title) }.getOrElse { WikiApi.errorFor(null, it, title) }) {
			is WikiApi.Outcome.Page -> { cache.put(title, o); Step.Hit(o, false) }
			is WikiApi.Outcome.Missing -> Step.Missing
			is WikiApi.Outcome.Error -> cache.get(title, allowStale = true)?.let { Step.Hit(WikiCache.toPage(it), true) } ?: Step.Fail(o.message)
		}
	}

	private fun anchorFor(page: WikiApi.Outcome.Page, wanted: String?): String? =
		WikiApi.sectionAnchor(page.sections, wanted ?: page.anchor)
}
