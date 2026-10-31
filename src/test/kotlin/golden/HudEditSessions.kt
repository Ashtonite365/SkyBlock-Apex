// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object HudEditSessions {
	data class El(val x: Int, val y: Int, val w: Int, val h: Int, val scale: Float?, val enabled: Boolean)
	sealed interface Ev { val x: Double; val y: Double }
	data class Press(val button: Int, override val x: Double, override val y: Double) : Ev
	data class Move(override val x: Double, override val y: Double) : Ev
	data class Release(val button: Int, override val x: Double, override val y: Double) : Ev
	data class Session(val screenW: Int, val screenH: Int, val els: List<El>, val events: List<Ev>)

	fun sessions(): List<Session> {
		val r = Random(4242)
		return List(400) {
			val sw = listOf(480, 320, 640)[r.nextInt(3)]; val sh = listOf(270, 240, 360)[r.nextInt(3)]
			val els = List(r.nextInt(1, 5)) {
				El(r.nextInt(0, sw - 20), r.nextInt(0, sh - 10), r.nextInt(10, 120), r.nextInt(8, 60),
					if (r.nextInt(3) == 0) null else r.nextInt(30, 300) / 100f, r.nextInt(6) != 0)
			}
			val events = mutableListOf<Ev>()
			repeat(r.nextInt(1, 4)) {
				val target = els[r.nextInt(els.size)]
				val s = target.scale ?: 1f
				val ew = (target.w * s).toInt(); val eh = (target.h * s).toInt()

				val px = if (r.nextInt(5) == 0) r.nextInt(0, sw).toDouble() else target.x + 3 + r.nextDouble() * (ew - 6).coerceAtLeast(1)
				val py = if (r.nextInt(5) == 0) r.nextInt(0, sh).toDouble() else target.y + 3 + r.nextDouble() * (eh - 6).coerceAtLeast(1)
				val button = if (r.nextInt(3) == 0) 1 else 0
				events += Press(button, px, py)
				var mx = px; var my = py
				repeat(r.nextInt(1, 6)) {
					mx += r.nextInt(-60, 61) + r.nextDouble(); my += r.nextInt(-40, 41) + r.nextDouble()
					events += Move(mx, my)
				}
				events += Release(button, mx, my)
			}
			Session(sw, sh, els, events)
		}
	}
}
