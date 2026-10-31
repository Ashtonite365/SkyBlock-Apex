// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import dev.ashtonite365.apex.features.menus.MenuTheme

data class StylePalette(
	val style: Style, val dark: Boolean,
	val backdrop: Int, val window: Int, val card: Int, val raised: Int, val well: Int,
	val border: Int, val borderHover: Int, val rimHighlight: Int, val shadow: Int,
	val accent: Int, val accent2: Int, val glow: Int,
	val text: Int, val textDim: Int, val textOnAccent: Int,
	val buttonFill: Int, val buttonHover: Int, val buttonPressed: Int, val buttonActive: Int, val buttonEdge: Int,
	val risk: Int,
	val cardRadius: Int, val controlRadius: Int, val dockRadius: Int,

	val blurWorld: Boolean,

	val overlay: Int = raised,

	val clearOutside: Boolean = false,

	val textShadow: Boolean = false,
) {

	fun theme(): MenuTheme.Palette = MenuTheme.Palette(
		bg = window, border = border, borderHover = borderHover, accent = accent,
		fill = buttonFill, fillHover = buttonHover, fillPressed = buttonPressed, fillActive = buttonActive,
		fillDisabled = well, text = text, textDim = textDim, disabled = textDim and 0x00FFFFFF or (0x90 shl 24),
	)

	companion object {
		private fun a(argb: Int, alphaPct: Int) = (argb and 0xFFFFFF) or ((alphaPct * 255 / 100) shl 24)

		val SKYGLASS = StylePalette(
			Style.SKYGLASS, dark = true,
			backdrop = 0x00000000, window = a(0x0E141C, 38), card = a(0x0E141C, 22), raised = a(0x16202C, 34), well = a(0x000000, 24),
			border = a(0xFFFFFF, 24), borderHover = a(0x9CD4FF, 85), rimHighlight = a(0xFFFFFF, 30), shadow = a(0x000000, 14),
			accent = 0xFF7CC4FF.toInt(), accent2 = 0xFF8CF5D2.toInt(), glow = a(0x8CF5D2, 55),
			text = 0xFFF2F6FA.toInt(), textDim = 0xFFB4C2D2.toInt(), textOnAccent = 0xFF0B2238.toInt(),
			buttonFill = a(0xFFFFFF, 16), buttonHover = a(0xFFFFFF, 26), buttonPressed = a(0xFFFFFF, 10), buttonActive = a(0x7CC4FF, 80),
			buttonEdge = a(0x000000, 22), risk = 0xFFFF7A85.toInt(),
			cardRadius = 5, controlRadius = 4, dockRadius = 7, blurWorld = true,
			overlay = a(0x1C2633, 90), clearOutside = true, textShadow = true,
		)

		val FORGE_DARK = StylePalette(
			Style.FORGE, dark = true,
			backdrop = a(0x0D0906, 72), window = 0xFF1A1410.toInt(), card = 0xFF241B15.toInt(), raised = 0xFF2C2119.toInt(), well = 0xFF120D0A.toInt(),
			border = 0xFF3A2B20.toInt(), borderHover = 0xFF5A4130.toInt(), rimHighlight = a(0xFF8A3D, 35), shadow = a(0x000000, 40),
			accent = 0xFFFF8A3D.toInt(), accent2 = 0xFFFFC857.toInt(), glow = 0xFFFF6A1A.toInt(),
			text = 0xFFF3E6D8.toInt(), textDim = 0xFFA89280.toInt(), textOnAccent = 0xFF1A1410.toInt(),
			buttonFill = 0xFF2A2018.toInt(), buttonHover = 0xFF3A2B20.toInt(), buttonPressed = 0xFF120D0A.toInt(), buttonActive = 0xFF4A2A14.toInt(),
			buttonEdge = 0xFF0A0705.toInt(), risk = 0xFFFF4D4D.toInt(),
			cardRadius = 3, controlRadius = 2, dockRadius = 3, blurWorld = false,
		)

		val FORGE_LIGHT = StylePalette(
			Style.FORGE, dark = false,
			backdrop = a(0x3A2B20, 40), window = 0xFFF4E9DC.toInt(), card = 0xFFFFF6EC.toInt(), raised = 0xFFFFFBF5.toInt(), well = 0xFFE8D8C6.toInt(),
			border = 0xFFC9AE92.toInt(), borderHover = 0xFFB07A4A.toInt(), rimHighlight = a(0xFFFFFF, 80), shadow = a(0x3A2B20, 25),
			accent = 0xFFE06A1E.toInt(), accent2 = 0xFFC8901A.toInt(), glow = 0xFFFF8A3D.toInt(),
			text = 0xFF2A1D12.toInt(), textDim = 0xFF7A6250.toInt(), textOnAccent = 0xFFFFF6EC.toInt(),
			buttonFill = 0xFFFFF1E0.toInt(), buttonHover = 0xFFFFE4C8.toInt(), buttonPressed = 0xFFE8D0B4.toInt(), buttonActive = 0xFFFFD2A8.toInt(),
			buttonEdge = 0xFFB89878.toInt(), risk = 0xFFD03030.toInt(),
			cardRadius = 3, controlRadius = 2, dockRadius = 3, blurWorld = false,
		)

		fun of(s: StyleState): StylePalette = when {
			s.style == Style.SKYGLASS -> SKYGLASS
			s.forgeMode == ForgeMode.DARK -> FORGE_DARK
			else -> FORGE_LIGHT
		}
	}
}
