// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import dev.ashtonite365.apex.util.customgui.customGui
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class Style(val id: String, val label: String) {
	SKYGLASS("skyglass", "Skyglass"), FORGE("forge", "Forge");
	companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: SKYGLASS }
}

enum class StyleChoice(val label: String, val style: Style?) {
	SKYGLASS("Skyglass", Style.SKYGLASS), FORGE("Forge", Style.FORGE), VANILLA("Vanilla", null);
	companion object {

		fun of(s: StyleState): StyleChoice = if (!s.customMenus) VANILLA else if (s.style == Style.FORGE) FORGE else SKYGLASS

		fun apply(s: StyleState, c: StyleChoice): StyleState = if (c.style == null) s.copy(customMenus = false) else s.copy(customMenus = true, style = c.style)
	}
}

enum class ForgeMode(val id: String) {
	DARK("dark"), LIGHT("light");
	companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DARK }
}

enum class MenuAnim(val id: String, val label: String) {
	FADE("fade", "Fade"), CIRCLE("circle", "Circle"), GLITCH("glitch", "Glitch");
	companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: FADE }
}

data class StyleState(
	val style: Style = Style.SKYGLASS,
	val forgeMode: ForgeMode = ForgeMode.DARK,
	val animate: Boolean = true,
	val anim: MenuAnim = MenuAnim.FADE,
	val reduceMotion: Boolean = false,

	val wrapAll: Boolean = true,

	val inventoryButton: Boolean = true,

	val customMenus: Boolean = true,
) {

	val dark: Boolean get() = style == Style.FORGE && forgeMode == ForgeMode.DARK
}

object StyleCodec {
	private val json = Json { prettyPrint = true }
	fun encode(s: StyleState): String = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf(
		"style" to JsonPrimitive(s.style.id),
		"forge-mode" to JsonPrimitive(s.forgeMode.id),
		"animate" to JsonPrimitive(s.animate),
		"animation" to JsonPrimitive(s.anim.id),
		"reduce-motion" to JsonPrimitive(s.reduceMotion),
		"style-all-chests" to JsonPrimitive(s.wrapAll),
		"inventory-button" to JsonPrimitive(s.inventoryButton),
		"custom-menus" to JsonPrimitive(s.customMenus),
	)))
	fun decode(text: String?): StyleState {
		val o = runCatching { Json.parseToJsonElement(text ?: "").jsonObject }.getOrNull() ?: return StyleState()
		fun str(k: String) = runCatching { o[k]?.jsonPrimitive?.content }.getOrNull()
		fun bool(k: String, d: Boolean) = runCatching { o[k]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: d
		return StyleState(Style.of(str("style")), ForgeMode.of(str("forge-mode")), bool("animate", true),
			MenuAnim.of(str("animation")), bool("reduce-motion", false), bool("style-all-chests", true), bool("inventory-button", true), bool("custom-menus", true))
	}
}

object MenuStyle {

	var file: File? = null
		get() = field ?: runCatching {
			net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("apex/menu-style.json").toFile()
		}.getOrNull()

	private var loaded: StyleState? = null
	val state: StyleState get() = loaded ?: load()

	fun load(): StyleState {
		val s = StyleCodec.decode(runCatching { file?.takeIf { it.isFile }?.readText() }.getOrNull())
		loaded = s
		return s
	}

	fun update(change: (StyleState) -> StyleState) {
		val next = change(state)
		loaded = next
		runCatching { file?.let { it.parentFile?.mkdirs(); it.writeText(StyleCodec.encode(next)) } }
	}

	fun setStyle(s: Style) = update { it.copy(style = s) }

	fun choose(c: StyleChoice) {
		val was = state.customMenus
		update { StyleChoice.apply(it, c) }
		if (was == state.customMenus) return

		if (state.customMenus) refreshScreen() else showVanillaInPlace()
	}
	fun setCustomMenus(on: Boolean) = choose(if (on) StyleChoice.of(state.copy(customMenus = true)) else StyleChoice.VANILLA)

	var showVanillaInPlace: () -> Unit = {
		runCatching {
			(dev.ashtonite365.apex.util.Client.screen as? net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>)?.customGui = null
		}
	}

	var refreshScreen: () -> Unit = {
		runCatching {
			val sc = dev.ashtonite365.apex.util.Client.screen as? net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>

			if (sc != null && state.customMenus) sc.resize(sc.width, sc.height)
		}
	}

	fun wrapAllAfter(s: StyleState, on: Boolean): StyleState = if (on) s.copy(wrapAll = true, customMenus = true) else s.copy(wrapAll = false)
	fun setWrapAll(on: Boolean) {
		val was = state.customMenus
		update { wrapAllAfter(it, on) }
		if (was != state.customMenus || state.customMenus) refreshScreen()
	}

	fun toggleForgeMode() = update { it.copy(forgeMode = if (it.forgeMode == ForgeMode.DARK) ForgeMode.LIGHT else ForgeMode.DARK) }

	fun resetForTests() { loaded = null }

	val palette: StylePalette get() = StylePalette.of(state)

	fun clearOutsideNow(): Boolean = palette.clearOutside && net.minecraft.client.Minecraft.getInstance().level != null
}
