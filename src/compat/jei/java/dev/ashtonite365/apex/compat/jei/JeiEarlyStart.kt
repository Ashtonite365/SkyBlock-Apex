package dev.ashtonite365.apex.compat.jei

import java.lang.reflect.Field
import java.lang.reflect.Method
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.items.recipes.ViewerStartup

object JeiEarlyStart {
	private var handler: Any? = null
	private var runningField: Field? = null
	private var startMethod: Method? = null
	private var registered = false

	private var lastLevel: Any? = null
	private var ticksInLevel = 0
	private var attempted = false

	@JvmStatic
	fun capture(lifecycleHandler: Any) {
		try {
			val cls = lifecycleHandler.javaClass
			val f = cls.getDeclaredField("running").apply { isAccessible = true }
			val m = cls.getDeclaredMethod("startJei").apply { isAccessible = true }
			if (f.type != java.lang.Boolean.TYPE) return
			handler = lifecycleHandler
			runningField = f
			startMethod = m
			if (!registered) {
				registered = true
				ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { onTick(it) })
			}
		} catch (t: Throwable) {
			Apex.logger.warn("[ItemList] early start unavailable (item list viewer internals changed): $t")
		}
	}

	private fun onTick(mc: Minecraft) {
		val h = handler ?: return
		val level = mc.level
		if (level !== lastLevel) {
			lastLevel = level
			ticksInLevel = 0
			attempted = false
		}
		if (level == null) return
		if (ticksInLevel < Int.MAX_VALUE) ticksInLevel++
		val running = try {
			runningField!!.getBoolean(h)
		} catch (t: Throwable) {
			return
		}
		val start = ViewerStartup.shouldStart(
			running = running,
			inWorld = mc.player != null,
			reloading = mc.gui.overlay() != null,
			containerOpen = mc.gui.screen() is AbstractContainerScreen<*>,
			ticksInLevel = ticksInLevel,
			attempted = attempted,
		)
		if (!start) return
		attempted = true
		val t0 = System.nanoTime()
		try {
			startMethod!!.invoke(h)
			Apex.logger.info("[ItemList] started the item list viewer on join (no recipe packet from the server) in ${(System.nanoTime() - t0) / 1_000_000} ms, off the first inventory frame")
		} catch (t: Throwable) {
			Apex.logger.warn("[ItemList] early item list start failed; it will start on its own when a screen opens: ${t.cause ?: t}")
		}
	}
}
