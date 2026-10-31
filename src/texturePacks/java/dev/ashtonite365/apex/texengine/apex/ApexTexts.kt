/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: text replacements for GUI text (SPEC §2.10).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import dev.ashtonite365.apex.texengine.TextStyling
import dev.ashtonite365.apex.util.trail.Crumb
import dev.ashtonite365.apex.util.trail.Trail
import java.util.Optional
import java.util.regex.Matcher
import java.util.regex.Pattern
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

object ApexTexts : TextStyling {
	val recognisedNamespaces = listOf("firmskyblock")
	private const val FOLDER = "overrides/texts"
	private val GROUP_REF: Pattern = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}")

	class Replacement(val find: Pattern, val style: Style?, val replace: Component) {

		private val usesGroups = hasGroupRef(replace)

		fun replacementFor(hit: Matcher): Component = if (usesGroups) withGroups(replace, hit) else replace

		fun appliesTo(partStyle: Style): Boolean {
			val want = style ?: return true
			val merged = want.applyTo(partStyle)
			return merged.isBold == partStyle.isBold && merged.isItalic == partStyle.isItalic &&
				merged.isUnderlined == partStyle.isUnderlined && merged.isStrikethrough == partStyle.isStrikethrough &&
				merged.isObfuscated == partStyle.isObfuscated && merged.color == partStyle.color
		}
	}

	class Rule(val match: TextMatch, val replacements: List<Replacement>)

	@Volatile
	private var rules: Array<Rule> = arrayOf()

	override fun rewrite(text: Component): Component {
		val all = rules
		if (all.isEmpty()) return text

		val flat = text.string
		var out = text
		for (rule in all) {
			if (!rule.match.test(flat)) continue
			for (r in rule.replacements) out = replaceIn(out, r)
		}
		return out
	}

	private class Part(val style: Style, val text: String, val hit: Matcher?)

	private fun replaceIn(text: Component, r: Replacement): Component {
		val parts = ArrayList<Part>()
		var anyHit = false
		text.visit({ style, s ->
			if (s.isNotEmpty()) {
				val m = if (r.appliesTo(style)) r.find.matcher(s).takeIf { it.find() } else null
				if (m != null) anyHit = true
				parts += Part(style, s, m)
			}
			Optional.empty<Unit>()
		}, Style.EMPTY)
		if (!anyHit) return text
		val root = Component.empty()
		for (p in parts) {
			val m = p.hit
			if (m == null) {
				root.append(Component.literal(p.text).setStyle(p.style))
				continue
			}

			val part = Component.empty().setStyle(p.style)
			var last = 0
			do {
				if (m.start() > last) part.append(Component.literal(p.text.substring(last, m.start())))
				part.append(r.replacementFor(m))
				last = m.end()
			} while (m.find())
			if (last < p.text.length) part.append(Component.literal(p.text.substring(last)))
			root.append(part)
		}
		return root
	}

	private fun hasGroupRef(c: Component): Boolean {
		val contents = c.contents
		if (contents is PlainTextContents && GROUP_REF.matcher(contents.text()).find()) return true
		return c.siblings.any(::hasGroupRef)
	}

	private fun withGroups(c: Component, hit: Matcher): Component {
		val contents = c.contents
		val copy: MutableComponent =
			if (contents is PlainTextContents) Component.literal(fillGroups(contents.text(), hit))
			else MutableComponent.create(contents)
		copy.setStyle(c.style)
		for (child in c.siblings) copy.append(withGroups(child, hit))
		return copy
	}

	private fun fillGroups(s: String, hit: Matcher): String {
		val ref = GROUP_REF.matcher(s)
		if (!ref.find()) return s
		val groups = hit.namedGroups()
		val out = StringBuilder()
		var last = 0
		do {
			out.append(s, last, ref.start())
			val index = groups[ref.group(1)]

			if (index == null) out.append(ref.group()) else out.append(hit.group(index) ?: "")
			last = ref.end()
		} while (ref.find())
		return out.append(s, last, s.length).toString()
	}

	fun use(loaded: List<Rule>) {
		rules = loaded.toTypedArray()
	}

	fun load(manager: ResourceManager): List<Rule> {
		val out = ArrayList<Rule>()
		for ((fileId, resource) in PackFiles.list(manager, FOLDER, recognisedNamespaces)) {
			try {
				val o = PackFiles.read(resource).asJsonObject
				val match = TextMatch.read(o.get("match") ?: badPack("missing match"))
				val replacements = (o.getAsJsonArray("replacements") ?: badPack("missing replacements")).map {
					val ro = it.asJsonObject
					Replacement(
						TextMatch.read(ro.get("match") ?: badPack("missing replacement match")).searchPattern().toPattern(),
						ro.get("style")?.let(::style),
						component(ro.get("replace") ?: badPack("missing replace")),
					)
				}
				out += Rule(match, replacements)
			} catch (e: Exception) {
				PackFiles.log.warn("Skipping text replacement {}: {}", fileId, e.message)
				Trail.crumb(Crumb.TEXTURE_FILE_SKIPPED, fileId)
			}
		}
		return out
	}

	private fun style(json: JsonElement): Style =
		Style.Serializer.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow { PackFormatException("bad style: $it") }

	private fun component(json: JsonElement): Component =
		ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow { PackFormatException("bad replace text: $it") }

	object Listener : SimplePreparableReloadListener<List<Rule>>() {
		override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) = load(manager)
		override fun apply(data: List<Rule>, manager: ResourceManager, profiler: ProfilerFiller) = use(data)
	}
}
