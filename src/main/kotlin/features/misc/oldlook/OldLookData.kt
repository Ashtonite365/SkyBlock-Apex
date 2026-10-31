/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: "classic item looks" data model and pure resolution logic. Original implementation.
 * The data file (assets/apex/old_look/old_look.json) is MIT-licensed item data and its state rules carry a
 * BSD-3-Clause notice; both licence files ship next to it. Sources are listed in the Credits menu.
 */
package dev.ashtonite365.apex.features.misc.oldlook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.minecraft.nbt.CompoundTag

data class OldLookEntry(

	val item: String,

	val skin: String? = null,

	val uuid: String? = null,

	val color: Int? = null,

	val glint: Boolean = false,

	val potion: String? = null,
) {
	val isHead get() = item == "player_head"

	val usable get() = !isHead || skin != null
}

data class OldLookRule(

	val ids: Set<String>?,
	val condition: Condition,
	val item: String,

	val elseItem: String? = null,
) {
	sealed interface Condition {

		data class CustomDataEquals(val key: String, val number: Long?, val text: String?) : Condition

		data class CustomDataPresent(val key: String, val absent: String?) : Condition

		data object OnCooldown : Condition

		data class ModelSuffix(val suffix: String) : Condition
	}

	fun appliesTo(sbId: String?) = ids == null || (sbId != null && sbId in ids)
}

sealed interface OldLookResolution {

	data object Keep : OldLookResolution

	data class Look(val item: String, val entry: OldLookEntry?) : OldLookResolution

	data class Cooldown(val onCooldown: String, val otherwise: String) : OldLookResolution
}

class OldLookBundle(val items: Map<String, OldLookEntry>, val rules: List<OldLookRule>) {

	fun resolve(sbId: String?, customData: CompoundTag?, modelPath: String?): OldLookResolution {
		for (rule in rules) {
			if (!rule.appliesTo(sbId)) continue
			when (val c = rule.condition) {
				OldLookRule.Condition.OnCooldown ->
					return OldLookResolution.Cooldown(rule.item, rule.elseItem ?: items[sbId]?.item ?: rule.item)
				else -> if (matches(c, customData, modelPath)) return OldLookResolution.Look(rule.item, null)
			}
		}
		val entry = sbId?.let(items::get) ?: return OldLookResolution.Keep
		if (!entry.usable) return OldLookResolution.Keep
		return OldLookResolution.Look(entry.item, entry)
	}

	companion object {
		fun matches(c: OldLookRule.Condition, customData: CompoundTag?, modelPath: String?): Boolean = when (c) {
			is OldLookRule.Condition.CustomDataEquals -> {
				val tag = customData?.get(c.key)
				when {
					tag == null -> false
					c.number != null -> tag.asNumber().map { it.toLong() == c.number }.orElse(false)
					else -> tag.asString().map { it == c.text }.orElse(false)
				}
			}
			is OldLookRule.Condition.CustomDataPresent ->
				customData != null && customData.contains(c.key) && (c.absent == null || !customData.contains(c.absent))
			is OldLookRule.Condition.ModelSuffix -> modelPath != null && modelPath.substringAfterLast('/') == c.suffix
			OldLookRule.Condition.OnCooldown -> false
		}

		private val json = Json { ignoreUnknownKeys = true }

		fun parse(text: String): OldLookBundle {
			val root = json.parseToJsonElement(text).jsonObject
			val format = root["_meta"]?.jsonObject?.get("format")?.jsonPrimitive?.intOrNull
			require(format == 1) { "Unsupported old look format $format" }
			val rules = root["dynamic"]?.jsonArray.orEmpty().map { parseRule(it.jsonObject) }
			val itemsObj = root["items"]?.jsonObject ?: JsonObject(emptyMap())
			val items = HashMap<String, OldLookEntry>(itemsObj.size * 2)
			for ((id, v) in itemsObj) {
				val o = v.jsonObject
				items[id] = OldLookEntry(
					item = o.str("item") ?: continue,
					skin = o.str("skin"),
					uuid = o.str("uuid"),
					color = (o["color"] as? JsonPrimitive)?.longOrNull?.toInt(),
					glint = (o["glint"] as? JsonPrimitive)?.booleanOrNull ?: false,
					potion = o.str("potion"),
				)
			}
			return OldLookBundle(items, rules)
		}

		private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

		private fun parseRule(o: JsonObject): OldLookRule {
			val ids = (o["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
			val w = o["when"]?.jsonObject ?: error("Rule without when: $o")
			val cond = when {
				w["on_cooldown"] != null -> OldLookRule.Condition.OnCooldown
				w["hypixel_model_suffix"] != null -> OldLookRule.Condition.ModelSuffix(w.str("hypixel_model_suffix")!!)
				w["custom_data"] != null && w["eq"] != null -> {
					val eq = w["eq"]!!.jsonPrimitive
					OldLookRule.Condition.CustomDataEquals(w.str("custom_data")!!, if (eq.isString) null else eq.longOrNull, if (eq.isString) eq.content else null)
				}
				w["custom_data"] != null -> OldLookRule.Condition.CustomDataPresent(w.str("custom_data")!!, w.str("and_no"))
				else -> error("Unknown rule condition: $w")
			}
			return OldLookRule(
				ids = if ("*" in ids) null else ids.toSet(),
				condition = cond,
				item = o.str("item") ?: error("Rule without item: $o"),
				elseItem = o.str("else"),
			)
		}
	}
}

object OldLookPolicy {

	fun active(skipPack: Boolean, classicLooks: Boolean) = skipPack && classicLooks

	fun replaces(namespace: String?, path: String?): Boolean =
		namespace != null && (namespace != "minecraft" || path == "paper")

	fun itemsOnlyServes(namespace: String, path: String): Boolean = when (namespace) {
		HYPIXEL_NAMESPACE -> HIDDEN_IN_HYPIXEL_NAMESPACE.none { path == it || path.startsWith("$it/") }
		"minecraft" -> path.startsWith("atlases/")
		else -> false
	}

	private val HIDDEN_IN_HYPIXEL_NAMESPACE = listOf(
		"sounds.json", "sounds", "font", "shaders", "post_effect", "lang", "texts", "particles", "equipment", "waypoint_style",
		"textures/gui", "textures/font", "textures/entity", "textures/particle", "textures/environment", "textures/misc",
		"textures/effect", "textures/mob_effect", "textures/painting", "textures/map", "textures/trims", "textures/colormap",
	)

	const val HYPIXEL_NAMESPACE = "hypixel_skyblock"

	data class PackMode(val wanted: Boolean, val itemsOnly: Boolean)

	fun packMode(skipPack: Boolean, keepNewerTextures: Boolean) =
		PackMode(wanted = !skipPack || keepNewerTextures, itemsOnly = skipPack && keepNewerTextures)

	enum class ModeAction { NONE, POP_SERVER_PACK, RELOAD, REAPPLY_SERVER_PACK }

	fun onModeChange(prev: PackMode?, now: PackMode, onHypixel: Boolean, serverPackApplied: Boolean): ModeAction = when {
		prev == null || prev == now -> ModeAction.NONE
		prev.wanted && !now.wanted -> if (onHypixel && serverPackApplied) ModeAction.POP_SERVER_PACK else ModeAction.RELOAD
		!prev.wanted && now.wanted -> if (onHypixel) ModeAction.REAPPLY_SERVER_PACK else ModeAction.RELOAD
		else -> ModeAction.RELOAD
	}
}
