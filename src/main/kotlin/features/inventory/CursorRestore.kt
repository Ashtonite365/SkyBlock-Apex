package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object CursorRestore {
	@Config
	object TConfig : ManagedConfig("save-cursor-position", Category.INVENTORY) {
		val active by toggle("enable") { true }
		val reopenWindow by duration("tolerance", 10.milliseconds, 5000.milliseconds) { 500.milliseconds }
	}

	class Memory(private val nowMillis: () -> Long) {
		private var freePosition: Pair<Double, Double>? = null
		private var pending: Snapshot? = null

		private class Snapshot(val centreX: Double, val centreY: Double, val restoreTo: Pair<Double, Double>, val at: Long)

		fun beforeGrab(x: Double, y: Double) {
			freePosition = x to y
		}

		fun afterCentre(x: Double, y: Double, enabled: Boolean) {
			if (!enabled) return
			val from = freePosition ?: return
			pending = Snapshot(x, y, from, nowMillis())
		}

		fun onRelease(x: Double, y: Double, enabled: Boolean, toleranceMillis: Long): Pair<Double, Double>? {
			if (!enabled) return null
			val snap = pending
			pending = null
			if (snap == null || nowMillis() - snap.at >= toleranceMillis) return null
			val stillCentred = abs(snap.centreX - x) < 1 && abs(snap.centreY - y) < 1
			return if (stillCentred) snap.restoreTo else null
		}
	}

	private val memory = Memory { System.nanoTime() / 1_000_000 }

	@JvmStatic
	fun beforeGrab(x: Double, y: Double) = memory.beforeGrab(x, y)

	@JvmStatic
	fun afterCentre(x: Double, y: Double) = memory.afterCentre(x, y, TConfig.active)

	@JvmStatic
	fun restoreOnRelease(x: Double, y: Double): Pair<Double, Double>? {
		val target = memory.onRelease(x, y, TConfig.active, TConfig.reopenWindow.inWholeMilliseconds) ?: return null
		InputConstants.releaseMouse(Client.window, target.first, target.second)
		return target
	}
}
