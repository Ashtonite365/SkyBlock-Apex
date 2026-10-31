// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class JeiLook(val id: String, val label: String) {
	VANILLA("vanilla", "Vanilla JEI"), SKYGLASS("skyglass", "Skyglass"), FORGE("forge", "Forge");
	companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: VANILLA }
}

data class JeiStyleState(val look: JeiLook = JeiLook.VANILLA, val forgeDark: Boolean = true,
		val gridFill: Boolean = true, val hideSyncNotice: Boolean = true) {

	val variant: String? get() = when (look) {
		JeiLook.VANILLA -> null
		JeiLook.SKYGLASS -> "skyglass"
		JeiLook.FORGE -> if (forgeDark) "forge_dark" else "forge_light"
	}
}

object JeiStyleCodec {
	private val json = Json { prettyPrint = true }
	fun encode(s: JeiStyleState): String = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf(
		"look" to JsonPrimitive(s.look.id), "forge-dark" to JsonPrimitive(s.forgeDark),
		"grid-fill" to JsonPrimitive(s.gridFill), "hide-sync-notice" to JsonPrimitive(s.hideSyncNotice))))
	fun decode(text: String?): JeiStyleState {
		val o = runCatching { Json.parseToJsonElement(text ?: "").jsonObject }.getOrNull() ?: return JeiStyleState()
		return JeiStyleState(JeiLook.of(runCatching { o["look"]?.jsonPrimitive?.content }.getOrNull()),
			runCatching { o["forge-dark"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: true,
			runCatching { o["grid-fill"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: true,
			runCatching { o["hide-sync-notice"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: true)
	}
}

object JeiStyle {

	val THEMED: Set<String> = setOf(
		"gui_background", "ingredient_list_background", "bookmark_list_background", "search_background",
		"button_enabled", "button_disabled", "button_highlight", "button_pressed", "button_pressed_highlighted",
		"ingredient_list_slot_background", "bookmark_list_slot_background", "recipe_catalyst_slot_background",
		"scrollbar_background", "scrollbar_marker", "catalyst_tab", "recipe_options_tab",

		"tab_selected", "tab_unselected", "single_recipe_background", "recipe_preview_background",
		"interactive_ingredient_tooltip_background",
	)

	val NO_FROST: Set<String> = setOf("ingredient_list_slot_background", "bookmark_list_slot_background", "recipe_catalyst_slot_background", "scrollbar_marker")

	val PANELS: Set<String> = setOf("gui_background", "ingredient_list_background", "bookmark_list_background",
		"single_recipe_background", "recipe_preview_background", "interactive_ingredient_tooltip_background")

	fun forged(path: String, w: Int, h: Int, s: JeiStyleState = state): Boolean =
		s.look == JeiLook.FORGE && path in PANELS && w >= 24 && h >= 24

	fun frosted(path: String, s: JeiStyleState = state): Boolean = s.look == JeiLook.SKYGLASS && path in THEMED && path !in NO_FROST
	val VARIANTS = listOf("skyglass", "forge_dark", "forge_light")

	var file: File? = null
		get() = field ?: runCatching {
			net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("apex/jei-style.json").toFile()
		}.getOrNull()

	private var loaded: JeiStyleState? = null
	val state: JeiStyleState get() = loaded ?: load()

	fun load(): JeiStyleState = JeiStyleCodec.decode(runCatching { file?.takeIf { it.isFile }?.readText() }.getOrNull()).also { loaded = it }

	fun update(change: (JeiStyleState) -> JeiStyleState) {
		val next = change(state)
		loaded = next
		runCatching { file?.let { it.parentFile?.mkdirs(); it.writeText(JeiStyleCodec.encode(next)) } }
	}

	fun choose(look: JeiLook) = update { it.copy(look = look) }
	fun toggleForgeMode() = update { it.copy(forgeDark = !it.forgeDark) }
	fun toggleGridFill() = update { it.copy(gridFill = !it.gridFill) }
	fun toggleSyncNotice() = update { it.copy(hideSyncNotice = !it.hideSyncNotice) }
	@JvmStatic fun hideSyncNoticeNow(): Boolean = state.hideSyncNotice

	fun spriteFor(path: String, s: JeiStyleState = state): String? {
		val v = s.variant ?: return null
		return if (path in THEMED) "jei/$v/$path" else null
	}

	fun paletteState(s: JeiStyleState = state): StyleState? = when (s.look) {
		JeiLook.VANILLA -> null
		JeiLook.SKYGLASS -> StyleState(style = Style.SKYGLASS)
		JeiLook.FORGE -> StyleState(style = Style.FORGE, forgeMode = if (s.forgeDark) ForgeMode.DARK else ForgeMode.LIGHT)
	}

	fun resetForTests() { loaded = null }
}
