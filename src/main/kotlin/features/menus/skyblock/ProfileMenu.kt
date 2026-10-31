// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

enum class ProfileSlotState { PROFILE, EMPTY, LOCKED }

data class ProfileTile(
	val slot: Int, val state: ProfileSlotState, val name: String?, val active: Boolean,
	val mode: String?, val members: List<String>, val lore: List<String>,
)

data class ProfileModel(val tiles: List<ProfileTile>, val back: Int?, val close: Int?) {
	val active get() = tiles.firstOrNull { it.active }
}

object ProfileParser {
	val TITLE = Regex("^(?i)Profile Management$")
	private val PROFILE = Regex("^Profile:\\s*(.+)$", RegexOption.IGNORE_CASE)
	private val MODES = listOf("Ironman", "Stranded", "Bingo")

	fun parse(title: String, slots: List<SlotText>): ProfileModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val tiles = slots.mapNotNull { tile(it) }
		if (tiles.none { it.state == ProfileSlotState.PROFILE }) return null
		fun find(n: String) = slots.firstOrNull { it.cleanName.equals(n, true) }?.slot
		return ProfileModel(tiles, find("Go Back"), find("Close"))
	}

	fun tile(s: SlotText): ProfileTile? {
		val name = s.cleanName; val lore = s.cleanLore
		val state = when {
			PROFILE.matches(name) -> ProfileSlotState.PROFILE
			name.contains("Empty Profile", true) -> ProfileSlotState.EMPTY
			name.contains("Locked", true) -> ProfileSlotState.LOCKED
			else -> return null
		}
		val active = lore.any { it.contains("You are playing on this profile", true) || it.equals("Selected slot", true) }
		val mode = MODES.firstOrNull { m -> lore.any { it.contains(m, true) } } ?: if (state == ProfileSlotState.PROFILE) "Classic" else null
		return ProfileTile(s.slot, state, PROFILE.matchEntire(name)?.groupValues?.get(1)?.trim(), active, mode, members(lore), lore)
	}

	fun loreSummary(lore: List<String>) = lore.filter { l ->
		l.isNotBlank() && !l.startsWith("Click", true) && !l.startsWith("http") && !l.equals("Selected slot", true) && !l.startsWith("You are playing", true)
	}

	fun members(lore: List<String>): List<String> {
		val i = lore.indexOfFirst { it.trimEnd().endsWith("Members:", true) }
		if (i < 0) return emptyList()
		return lore.drop(i + 1).takeWhile { it.isNotBlank() }.map { it.removePrefix("-").trim().replace(Regex("^\\[[^]]+]\\s*"), "") }
	}
}

@Config
object ProfileMenuSettings : MenuSettings("profiles", panels = listOf("lore")) {
	val reset by button("reset") { resetToDefaults() }
}

object ProfileMenu : CustomMenu {
	override val id = "profiles"
	override val displayName = "Profile Management"
	override val title = ProfileParser.TITLE
	override val settings get() = ProfileMenuSettings
	override val defaultSize = 420 to 230

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView? = ProfileView(screen)
}

class ProfileView(screen: AbstractContainerScreen<*>) : ApexViewBase<ProfileModel>(screen) {
	override fun palette() = ProfileMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = ProfileParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: ProfileModel, mx: Int, my: Int) {
		ctx.text(font, "§6§lProfile Management", x + 6, y + 7, MenuTheme.GOLD)
		m.active?.let { ctx.text(font, "§7Active: §a${it.name} §7(${it.mode})", x + 130, y + 7, MenuTheme.GREY) }
		val vw = font.width("Vanilla") + 10

		val n = m.tiles.size.coerceAtLeast(1); val gap = 4
		val tw = (w - 12 - gap * (n - 1)) / n; val th = h - 52
		for ((i, t) in m.tiles.withIndex()) {
			val tx = x + 6 + i * (tw + gap); val ty = y + 22
			MenuTheme.frame(ctx, tx, ty, tx + tw, ty + th, if (t.active) MenuTheme.FILL_ACTIVE else MenuTheme.FILL,
				if (t.active) palette().accent else MenuTheme.BORDER)
			stacks[t.slot]?.let { ctx.item(it, tx + 4, ty + 4) }
			val title = when (t.state) { ProfileSlotState.PROFILE -> t.name ?: "?"; ProfileSlotState.EMPTY -> "Empty"; ProfileSlotState.LOCKED -> "Locked" }
			ctx.text(font, (if (t.active) "§a" else "§f") + MenuTheme.fit(title, tw - 26, font::width), tx + 22, ty + 8, MenuTheme.WHITE)
			var ly = ty + 24
			fun line(s: String) { if (ly < ty + th - 22) ctx.text(font, MenuTheme.fit(s, tw - 8, font::width), tx + 4, ly, MenuTheme.GREY); ly += 10 }
			t.mode?.let { line("§6Mode: §f$it") }
			if (t.members.isNotEmpty()) { line("§6Co-op:"); t.members.forEach { line("§7 $it") } }
			if (ProfileMenuSettings.shows("lore")) ProfileParser.loreSummary(t.lore).take(6).forEach { line("§8$it") }
			val label = when { t.state == ProfileSlotState.EMPTY -> "Create"; t.state == ProfileSlotState.LOCKED -> "Locked"; t.active -> "Manage"; else -> "Switch" }
			button(ctx, mx, my, tx + 4, ty + th - 18, tw - 8, 14, label, active = t.active, enabled = t.state != ProfileSlotState.LOCKED) { b -> clickSlot(t.slot, b) }
		}
		m.back?.let { s -> val bw = font.width("Go Back") + 14; button(ctx, mx, my, x + 6, y + h - 20, bw, 14, "Go Back") { b -> clickSlot(s, b) } }
		m.close?.let { s -> val cw = font.width("Close") + 14; button(ctx, mx, my, x + (w - cw) / 2, y + h - 20, cw, 14, "Close") { b -> clickSlot(s, b) } }
	}
}
