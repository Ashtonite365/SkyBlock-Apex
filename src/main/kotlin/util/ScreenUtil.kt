package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.Apex
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object ScreenUtil {
	private var pending: Screen? = null

	init {
		ClientTickEvents.START_CLIENT_TICK.register { client -> openPending(client) }
	}

	private fun openPending(client: Minecraft) {
		val screen = pending ?: return

		client.player?.takeIf { it.containerMenu != null }?.closeContainer()
		Client.gui.setScreen(screen)
		pending = null
	}

	fun setScreenLater(nextScreen: Screen?) {
		val queued = pending
		if (queued == null) {
			pending = nextScreen
			return
		}
		val incoming = nextScreen?.let { it::class.qualifiedName } ?: "null"
		Apex.logger.warn("Setting screen $incoming to be opened later, but ${queued::class.qualifiedName} is already queued.")
	}
}
