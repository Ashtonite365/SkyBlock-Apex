// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.hud

import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.events.FinalizeResourceManagerEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.GuiXml

abstract class MoulConfigHud(val name: String, val hudMeta: HudMeta) {

	private var layout: GuiContext? = null

	init { require(isValidName(name)) }

	init { HudRenderEvent.subscribe("MoulConfigHud:render", ::draw) }

	init {
		FinalizeResourceManagerEvent.subscribe("MoulConfigHud:finalizeResourceManager") { Client.resources.registerReloadListener(ResourceManagerReloadListener { layout = null }) }
	}

	fun forceInit() = Unit

	open fun shouldRender() = true

	fun loadFragment() = GuiXml.loadGui(name, this).let { layout = it }

	private fun draw(event: HudRenderEvent) {
		if (!shouldRender()) return
		val outer = host.createContext(event.context)
		if (layout == null) loadFragment()
		val pose = event.context.pose()
		pose.pushMatrix()

		val fit = hudMeta.applyTextTransformations(pose)
		val at = hudMeta.topLeft
		layout!!.root.render(inner(outer, at.x(), at.y(), fit.scale, fit.localW.toInt(), fit.localH.toInt()))
		pose.popMatrix()
	}

	private fun inner(outer: GuiImmediateContext, x: Int, y: Int, scale: Float, width: Int, height: Int): GuiImmediateContext {
		val mapping = HudContentTransform(x, y, scale, scale)
		val moved = outer.translated(x, y, hudMeta.drawnWidth, hudMeta.drawnHeight)
		val mouseX = mapping.localX(outer.mouseXHF)
		val mouseY = mapping.localY(outer.mouseYHF)
		return GuiImmediateContext(moved.renderContext, moved.renderOffsetX, moved.renderOffsetY, width, height, mouseX.toInt(), mouseY.toInt(), moved.absoluteMouseX, moved.absoluteMouseY, mouseX, mouseY)
	}

	companion object {
		private val NAME_SHAPE = Regex("[a-z_/]+")

		fun isValidName(name: String) = NAME_SHAPE.matches(name)

		private val host by lazy { MoulConfigScreenComponent(Component.empty(), GuiContext(TextComponent("§cERROR")), null) }
	}
}

data class HudContentTransform(val x: Int, val y: Int, val scaleX: Float, val scaleY: Float) {
	fun screenX(localX: Float) = x + localX * scaleX
	fun screenY(localY: Float) = y + localY * scaleY
	fun localX(screenX: Float) = (screenX - x) / scaleX.takeIf { it > 0f }!!
	fun localY(screenY: Float) = (screenY - y) / scaleY.takeIf { it > 0f }!!
}
