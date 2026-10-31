// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.rift.MotesOrbTracker
import dev.ashtonite365.apex.features.plus.rift.RiftRules
import dev.ashtonite365.apex.features.plus.rift.SnakeTracker
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch37GoldenTest {
	@Test
	fun rift() {
		val S = Batch37Rift
		val out = mutableListOf<Pair<String, String>>()
		val regexes = listOf("pickup" to MotesOrbTracker.pickupChat, "colour" to RiftRules.colourSetChat, "punched" to RiftRules.punched,
			"awkward" to RiftRules.awkward, "limit" to RiftRules.punchLimit, "ubik" to RiftRules.ubikCooldown)
		for (s in S.strings) {
			out += "hackinv|$s" to "${RiftRules.isHackingInv(s)}"
			out += "gecko|$s" to Golden.run { "${RiftRules.geckoCombo(s)}" }
			for ((n, r) in regexes) out += "re|$n|$s" to "${r.matchEntire(s)?.groupValues}|${r.pattern}"
		}
		for (d in S.durations) out += "dur|$d" to Golden.run { "${RiftRules.parseDuration(d)}" }
		for ((i, l) in S.cruxLores.withIndex()) out += "crux|$i|$l" to Golden.run { val c = RiftRules.cruxProgress(l); "$c -> ${RiftRules.cruxPercent(c)}" }
		out += "cruxpct|manual" to "${RiftRules.cruxPercent(listOf(RiftRules.CruxLine("I", "a", 1, 3), RiftRules.CruxLine("I", "b", 5, null), RiftRules.CruxLine("I", "c", 2, 0)))}"
		for ((i, l) in S.guideLores.withIndex()) out += "guide|$i|$l" to "${RiftRules.isRiftGuide(l)} ${RiftRules.guideNotCompleted(l)}"
		for (f in S.fractions) {
			out += "bar|$f|default" to Golden.run { RiftRules.progressBar(f) }
			for (n in S.lens) out += "bar|$f|$n" to Golden.run { RiftRules.progressBar(f, n) }
		}
		for (b in S.blocks) for (e in S.elapsed) out += "agaricus|$b|$e" to Golden.run { "${RiftRules.agaricusLabel(b, e)}" }
		for (h in S.held) out += "skull|$h" to "${RiftRules.skullTarget(h)}"
		for (t in RiftRules.Terminal.entries) out += "term|$t" to "${t.label} ${t.x} ${t.y} ${t.z} ${t.ordinal}"
		for ((i, p) in S.points.withIndex()) {
			out += "near|$i|$p" to "${RiftRules.nearestTerminal(p.first, p.second, p.third)}"
			for (m in S.maxes) out += "near|$i|$m" to "${RiftRules.nearestTerminal(p.first, p.second, p.third, m)}"
		}
		for ((mi, m) in S.menus.withIndex()) for (s in S.slots) {
			val calls = mutableListOf<Int>()
			out += "hack|$mi|$s" to Golden.run { "${RiftRules.hackingColour(s) { calls += it; m[it] }} calls=$calls" }
		}
		for ((si, script) in S.orbScripts.withIndex()) {
			val tr = MotesOrbTracker()
			for ((oi, op) in script.withIndex()) {
				when (op) {
					is Batch37Rift.Particle -> tr.particle(op.x, op.y, op.z, op.t)
					is Batch37Rift.Update -> tr.update(op.t)
					is Batch37Rift.PickNear -> tr.pickedUpNear(op.x, op.y, op.z)
				}
				out += "orb|$si|$oi|$op" to tr.orbs.joinToString(";") { "${it.x},${it.y},${it.z},${it.start},${it.last},${it.count},${it.isOrb},${it.pickedUp}" } +
					" vis=" + tr.visible().map { tr.orbs.indexOf(it) }
			}
		}
		for ((si, script) in S.snakeScripts.withIndex()) {
			val tr = SnakeTracker()
			for ((oi, op) in script.withIndex()) {
				when (op) {
					is Batch37Rift.Add -> tr.added(SnakeTracker.P(op.x, op.y, op.z), op.t)
					is Batch37Rift.Remove -> tr.removed(SnakeTracker.P(op.x, op.y, op.z))
					is Batch37Rift.Expire -> tr.expire(op.t)
				}
				out += "snake|$si|$oi|$op" to tr.snakes.joinToString(";") { "${it.blocks}@${it.lastAdd}" }
			}
		}
		val n = Golden.verify("batch37-rift", out, minCases = 17131)
		assertTrue(n >= 17131)
	}
}
