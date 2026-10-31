// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.repo

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import dev.ashtonite365.apex.Apex

object BoxContents : IReloadable {
	enum class Type { ITEM, COINS, SKILL_XP }
	data class Drop(
		val type: Type, val id: String? = null, val skill: String? = null, val min: Double = 1.0, val max: Double = 1.0,
		val chance: Double? = null, val note: String? = null, val weight: Double? = null, val group: String? = null,
	)
	data class Group(val name: String, val chance: Double?, val note: String? = null)

	data class Box(val box: String, val source: String, val drops: List<Drop>, val totalWeight: Double? = null, val groups: List<Group> = emptyList(), val unresolved: List<String> = emptyList())

	private fun JsonObject.str(k: String): String? = get(k)?.takeIf { !it.isJsonNull }?.asString
	private fun JsonObject.num(k: String): Double? = get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble

	fun parse(json: JsonElement): Box {
		val o = json.asJsonObject
		val box = requireNotNull(o.str("box")) { "missing \"box\"" }
		val unresolved = mutableListOf<String>()
		val drops = o.getAsJsonArray("drops").mapNotNull { e ->
			val d = e.asJsonObject
			val type = when (d.str("type")) { "item" -> Type.ITEM; "coins" -> Type.COINS; "skill_xp" -> Type.SKILL_XP; else -> error("bad drop type ${d.str("type")}") }
			if ((type == Type.ITEM && d.str("id") == null) || (type == Type.SKILL_XP && d.str("skill") == null)) {
				val what = d.str("note") ?: (if (type == Type.ITEM) "unknown item" else "unknown skill XP")
				unresolved += what + (d.num("chance")?.let { " (" + pct(it) + ")" } ?: ""); return@mapNotNull null
			}
			val min = d.num("min") ?: 1.0
			Drop(type, d.str("id"), d.str("skill"), min, d.num("max") ?: min, d.num("chance")?.also { require(it in 0.0..1.0) { "chance $it not in 0..1" } },
				d.str("note"), d.num("weight"), d.str("group"))
		}
		val groups = o.get("groups")?.takeIf { it.isJsonArray }?.asJsonArray?.map { g ->
			val j = g.asJsonObject
			Group(requireNotNull(j.str("name")) { "group without name" }, j.num("chance"), j.str("note"))
		}.orEmpty()
		return Box(box, o.str("source") ?: "", drops, o.num("total_weight"), groups, unresolved)
	}

	fun chanceOf(d: Drop, box: Box): Double? =
		d.chance ?: d.weight?.let { w -> box.totalWeight?.takeIf { it > 0 }?.let { w / it } }

	fun pct(c: Double?): String = c?.let { java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols(java.util.Locale.ROOT)).format(it * 100) + "%" } ?: "?"
	private fun n(v: Double) = java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols(java.util.Locale.ROOT)).format(v)
	fun amount(d: Drop): String = if (d.min == d.max) n(d.min) else n(d.min) + "–" + n(d.max)

	fun dropLines(d: Drop, box: Box): List<String> = listOfNotNull(
		when (d.type) {
			Type.COINS -> "§6${amount(d)} Coins"
			Type.SKILL_XP -> "§3${amount(d)} ${d.skill!!.lowercase().replaceFirstChar(Char::uppercase)} XP"
			Type.ITEM -> if (d.min != 1.0 || d.max != 1.0) "§7Amount: §f${amount(d)}" else null
		},
		"§7Chance: §e" + pct(chanceOf(d, box)),
		d.weight?.let { w -> "§7Weight " + n(w) + (box.totalWeight?.let { "/" + n(it) } ?: "") },
		d.note?.let { "§8$it" },
	)

	fun rows(box: Box): List<Pair<Group?, List<Drop>>> {
		if (box.groups.isEmpty()) return listOf(null to box.drops)
		val named = box.groups.map { g -> g to box.drops.filter { it.group == g.name } }
		val rest = box.drops.filter { d -> box.groups.none { it.name == d.group } }
		return named + (if (rest.isEmpty()) emptyList() else listOf(Group("Other", null) to rest))
	}

	fun groupLabel(g: Group): String = g.name + (g.chance?.let { " §7(" + pct(it) + ")" } ?: "") + (g.note?.let { " §8$it" } ?: "")

	@Volatile var boxes: Map<String, Box> = emptyMap(); private set

	override fun reload(repository: NEURepository) {
		val out = linkedMapOf<String, Box>()
		val files = runCatching { repository.tree("contents")?.use { it.toList() } }.getOrNull().orEmpty()
		for (f in files.filter { it.path.endsWith(".json") }) {
			runCatching { parse(f.json(JsonElement::class.java)) }
				.onSuccess { out[it.box] = it }
				.onFailure { Apex.logger.warn("[ApexRepo] contents: skipped ${f.path}: ${it.message}") }
		}
		boxes = out
		val unresolved = out.values.sumOf { it.unresolved.size }
		if (unresolved > 0) Apex.logger.info("[ApexRepo] contents: ${out.size} boxes loaded; $unresolved unresolved drops (no item id / skill yet) left out")
	}
}

object BoxContentsLayout {
	const val PER_LINE = 7
	const val LABEL_H = 10
	const val SLOT_H = 18

	sealed interface Line { val h: Int }
	data class Label(val text: String) : Line { override val h = LABEL_H }
	data class Drops(val drops: List<BoxContents.Drop>) : Line { override val h = SLOT_H }

	val roughWidth: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }

	fun lines(box: BoxContents.Box, width: Int = Int.MAX_VALUE, measure: (String) -> Int = roughWidth): List<Line> = BoxContents.rows(box).flatMap { (g, ds) ->
		(g?.let { wrapLabel(BoxContents.groupLabel(it), width, measure) } ?: emptyList()) + ds.chunked(PER_LINE).map { Drops(it) }
	} + box.unresolved.flatMap { wrapLabel("§8? " + cleanNote(it), width, measure) }

	fun cleanNote(s: String): String = s.replace(Regex("\\s*\\([A-Z0-9_:;-]+ not in Apex repo\\)"), "")

	fun wrapLabel(s: String, width: Int, measure: (String) -> Int): List<Label> {
		if (width == Int.MAX_VALUE || measure(s) <= width) return listOf(Label(s))
		var colour = ""
		return dev.ashtonite365.apex.gui.config.RapidClickBox.wrap(s, width, measure).mapIndexed { i, l ->
			val line = if (i == 0) l else colour + l
			Regex("§[0-9a-fk-or]").findAll(l).lastOrNull()?.let { colour = it.value }
			Label(line)
		}
	}

	fun headerName(box: BoxContents.Box): String {
		val src = box.source
		if (src.isEmpty()) return box.box.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
		if (src.startsWith("http")) return java.net.URLDecoder.decode(src.substringAfterLast('/').substringBefore('#'), Charsets.UTF_8).replace('_', ' ')
		return src
	}

	fun leftLabelX(left: Int, text: String, measure: (String) -> Int): Int = left + measure(text) / 2

	fun pages(box: BoxContents.Box, height: Int, width: Int = Int.MAX_VALUE, measure: (String) -> Int = roughWidth): List<List<Line>> {
		val out = mutableListOf<MutableList<Line>>(); var used = 0
		for (l in lines(box, width, measure)) {
			if (out.isEmpty() || used + l.h > height) { out += mutableListOf<Line>(); used = 0 }
			out.last() += l; used += l.h
		}
		return out.ifEmpty { mutableListOf(mutableListOf()) }
	}
}
