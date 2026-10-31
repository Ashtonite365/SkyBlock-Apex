// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

object DropdownLayer {
	enum class Layer { GUI, DROPDOWN, ITEM_LIST_OVERLAY, WIKI_OVERLAY, TOOLTIP }

	@JvmField val PHASE: net.minecraft.resources.Identifier = net.minecraft.resources.Identifier.fromNamespaceAndPath("apex", "gui_then_dropdown")

	inline fun runApexPass(gui: () -> Unit, dropdown: () -> Unit) { gui(); dropdown() }

	private val vanilla = java.util.WeakHashMap<net.minecraft.client.gui.screens.Screen, VanillaStyleDropdown.Dropdown>()
	fun attach(screen: net.minecraft.client.gui.screens.Screen, d: VanillaStyleDropdown.Dropdown) { vanilla[screen] = d; DropdownInput.register(claimer) }

	val claimer = DropdownInput.Claimer { screen, mx, my, button ->
		vanilla[screen]?.let { d -> if (d.claim(mx, my, button)) return@Claimer true }
		val session = ((screen as? dev.ashtonite365.apex.util.customgui.HasCustomGui)?.getCustomGui_Apex() as? MenuFront)?.session
			?: return@Claimer false
		!session.retired && !session.isHidden() && session.chrome.bar.open && session.guard("chrome", false) { session.chrome.press(session.view, mx, my) }
	}

	fun draw(screen: net.minecraft.client.gui.screens.Screen, g: net.minecraft.client.gui.GuiGraphicsExtractor, mx: Int, my: Int) {
		vanilla[screen]?.let { runCatching { it.drawLate(g, mx, my) } }
		((screen as? dev.ashtonite365.apex.util.customgui.HasCustomGui)?.getCustomGui_Apex() as? MenuFront)?.session?.drawLate(g, mx, my)
	}
}
