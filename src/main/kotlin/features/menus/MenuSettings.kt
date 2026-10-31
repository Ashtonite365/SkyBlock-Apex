// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.client.input.KeyEvent
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.gui.config.ClickHandler
import dev.ashtonite365.apex.gui.config.ManagedOption

enum class MenuMode : StringRepresentable {

	APEX,

	NORMAL;
	override fun getSerializedName() = name
}

data class MenuCustomisation(
	val width: Int? = null,
	val height: Int? = null,
	val scalePct: Int = 100,
	val accent: Int = MenuTheme.GOLD,
	val panels: Set<String> = emptySet(),
) {

	val palette get() = dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.theme().let { if (accent == MenuTheme.GOLD) it else it.withAccent(accent) }
	fun shows(panel: String) = panel in panels
}

abstract class MenuSettings(
	val menuId: String,

	val panels: List<String> = emptyList(),

	defaultApex: Boolean = true,
) : ManagedConfig("custom-menu-$menuId", Category.CUSTOM_MENUS) {

	override fun migrate(saved: kotlinx.serialization.json.JsonObject) =
		dev.ashtonite365.apex.features.menus.support.LegacyMenuSwitches.apply(name, saved)
	private val modeOption = choice("mode") { if (defaultApex) MenuMode.APEX else MenuMode.NORMAL }

	val mode: MenuMode get() = modeOption.loadedOrDefault()
	val width by integer("width", 0, 1200) { 0 }
	val height by integer("height", 0, 800) { 0 }
	val scale by integer("scale", 50, 200) { 100 }
	val accent by colour("accent") { ChromaColour.fromStaticRGB(0xFF, 0xAA, 0x00, 0xFF) }
	private val panelOptions = panels.associateWith { toggle("panel-$it") { true } }

	val apex get() = mode == MenuMode.APEX

	private fun <T : Any> ManagedOption<T>.loadedOrDefault(): T = _actualValue ?: default()

	val customisation: MenuCustomisation get() = MenuCustomisation(
		width.takeIf { it > 0 }, height.takeIf { it > 0 }, scale, accent.getEffectiveColourRGB(),
		panelOptions.filterValues { it.get() }.keys)

	fun resetToDefaults() {
		sortedOptions.forEach { resetOption(it) }
		markDirty()
		onReset()
	}

	protected open fun onReset() {}

	fun shows(name: String) = panelOptions[name]?.get() ?: true

	private fun <T : Any> resetOption(o: ManagedOption<T>) {
		if (o.handler is ClickHandler) return
		o.set(o.default())
	}

	init { all += this }

	companion object {

		val all = mutableListOf<MenuSettings>()
	}

	@Config
	object Global : ManagedConfig("custom-menus", Category.CUSTOM_MENUS) {

		val showVanillaKey by keyBindingWithDefaultUnbound("show-vanilla-key")

		val logTitles by toggle("log-titles") { true }

		val debugSlotDump by toggle("debug-slot-dump") { false }
		val resetDefaults by button("reset-defaults") {
			MenuSettings.all.forEach { it.resetToDefaults() }
			Client.showChat(net.minecraft.network.chat.Component.literal("§6[Apex] Custom menus reset to Apex defaults."))
		}

		fun showVanillaMatches(input: KeyEvent) =
			showVanillaKey.isBound && showVanillaKey.matches(GenericInputAction.of(input), InputModifiers.of(input))

		fun showVanillaKeyLabel() = if (showVanillaKey.isBound) " [${showVanillaKey.format().string}]" else ""
	}
}
