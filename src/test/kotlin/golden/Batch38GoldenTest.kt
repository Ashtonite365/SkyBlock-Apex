// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.rift.BerberisSequence
import dev.ashtonite365.apex.features.plus.rift.ButtonRules
import dev.ashtonite365.apex.features.plus.rift.DanceRoom
import dev.ashtonite365.apex.features.plus.rift.Effigies
import dev.ashtonite365.apex.features.plus.rift.EnigmaRules
import dev.ashtonite365.apex.features.plus.rift.ParkourState
import dev.ashtonite365.apex.features.plus.rift.RVec
import dev.ashtonite365.apex.features.plus.rift.RiftRepo
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch38GoldenTest {
	@Test
	fun riftRepo() {
		val S = Batch38RiftRepo
		val out = mutableListOf<Pair<String, String>>()
		fun v(t: Triple<Double, Double, Double>) = RVec(t.first, t.second, t.third)
		val vecs = S.vecStrings.map { RVec.parse(it) }
		for ((i, s) in S.vecStrings.withIndex()) out += "vec|$s" to "${vecs[i]} ${vecs[i]?.block()} ${vecs[i]?.hashCode()}"
		val some = vecs.filterNotNull() + listOf(RVec(0.0, 0.0, 0.0), RVec(-0.0, 0.0, 0.0), RVec(1.5, -2.5, 3.0))
		for (a in some) for (b in some) out += "vop|$a|$b" to "${a + b} ${a.distance(b)} ${a.distanceSq(b)} ${a.distanceIgnoreY(b)} ${a == b}"
		val parsers = listOf<Pair<String, (kotlinx.serialization.json.JsonElement) -> Any?>>("parkour" to RiftRepo::parkour, "dance" to RiftRepo::dance,
			"effigies" to RiftRepo::effigies, "berberis" to RiftRepo::berberis, "enigma" to RiftRepo::enigma, "buttons" to RiftRepo::buttons, "graph" to RiftRepo::graph)
		for ((ji, js) in S.jsons.withIndex()) {
			val el = Json.parseToJsonElement(js)
			for ((n, p) in parsers) out += "json|$ji|$n" to Golden.run { "${p(el)}" }
		}
		out += "consts" to listOf(RiftRepo.RACE, RiftRepo.UPSIDE_DOWN, RiftRepo.LAVA_MAZE, RiftRepo.TUBULATOR, RiftRepo.DANCE, RiftRepo.BUTTONS,
			RiftRepo.BERBERIS, RiftRepo.EFFIGIES, RiftRepo.ENIGMA, RiftRepo.GRAPH, DanceRoom.NOW, DanceRoom.NEXT, DanceRoom.LATER, Effigies.BREAK,
			EnigmaRules.NOT_COMPLETED, ButtonRules.ALL).toString()
		for ((li, locs) in S.parkourLocs.withIndex()) for ((ci, cuts) in S.parkourCuts.withIndex()) for (order in listOf(false, true)) for (size in listOf(1.0, 2.0)) {
			for ((si, script) in S.parkourScripts.withIndex()) {
				val p = ParkourState(locs.map { v(it) }, cuts, size, if (size == 1.0) 1.0 else 1.6, order)
				for ((k, st) in script.withIndex()) {
					if (st.reset) p.reset()
					if (st.look != null) p.lookAhead = st.look
					val r = p.update(RVec(st.x, st.y, st.z), st.ground)
					out += "pk|$li|$ci|$order|$size|$si|$k" to Golden.run {
						"$r ${p.current} ${p.visible} ${p.inParkour()} ${p.shouldRender()} ${p.visibleSteps()} ${p.visibleShortCuts()}"
					}
				}
			}
			if (locs.isNotEmpty()) out += "pkc|$li|$ci|$order|$size" to "${ParkourState(locs.map { v(it) }, cuts, size).center(v(locs[0]))}"
		}
		for (i in S.rainbow) out += "rgb|$i" to "${ParkourState.rainbowRgb(i)}"
		for (x in listOf(-268.0, -267.0, -263.0, -260.0, -259.9, Double.NaN)) for (y in listOf(31.9, 32.0, 36.0, 40.0, 40.1)) for (z in listOf(-110.1, -110.0, -105.0, -102.0, -101.0))
			out += "room|$x|$y|$z" to "${DanceRoom.inRoom(x, y, z)}"
		for (w in listOf("Move", "Stand", "Sneak", "Jump", "Punch", "move", "", "Other")) out += "dcol|$w" to DanceRoom.colour(w)
		for (l in S.danceLines.flatten() + listOf("a  b", " ", "ǆx", "\uD83D\uDE00z")) out += "dfmt|$l" to DanceRoom.formatInstruction(l)
		for (t in S.danceTicks) out += "dcd|$t" to DanceRoom.countdown(t)
		for ((di, d) in S.danceLines.withIndex()) for (i in S.danceIdx) for (t in listOf(0, 33)) for (n in S.danceShow)
			out += "dlines|$di|$i|$t|$n" to Golden.run { "${DanceRoom.lines(d, i, t, n)}" }
		for (s in S.sounds) for (p in S.floats) for (vol in S.floats) out += "snd|$s|$p|$vol" to "${DanceRoom.isSuccess(s, p, vol)} ${DanceRoom.isFailure(s, p, vol)}"
		for (l in S.heartLines + S.timerLines) out += "hearts|$l" to "${Effigies.parseHearts(l)} ${Effigies.timerPattern.matchEntire(l)?.groupValues}"
		for (d in S.durations) out += "edur|$d" to Golden.run { "${Effigies.parseDuration(d)}" }
		for (m in S.millis) out += "efmt|$m" to Effigies.format(m)
		val res = listOf(Effigies.heartsPattern, Effigies.timerPattern, EnigmaRules.title, EnigmaRules.guideArea, EnigmaRules.found, EnigmaRules.inventory, ButtonRules.hit)
		for (s in S.regexStrings) for ((ri, re) in res.withIndex()) out += "re|$ri|$s" to "${re.matchEntire(s)?.groupValues} ${re.pattern}"
		for ((mi, m) in S.soulMaps.withIndex()) {
			val souls = m.mapValues { (_, a) -> a.mapValues { (_, t) -> v(t) } }
			for ((pi, p) in S.players.withIndex()) out += "soul|$mi|$pi" to "${EnigmaRules.closest(souls, v(p))}"
		}
		val spotsSets = (0 until 30).map { k ->
			(0 until S.r.nextInt(0, 6)).map { RiftRepo.ButtonSpot("H$k.$it", RVec(S.r.nextInt(-6, 7).toDouble(), 0.0, S.r.nextInt(-6, 7).toDouble()),
				(0 until S.r.nextInt(0, 3)).map { RVec(S.r.nextInt(0, 3).toDouble(), 0.0, 0.0) }) }
		}
		val hits = listOf(emptySet(), setOf(RVec(0.0, 0.0, 0.0)), setOf(RVec(0.0, 0.0, 0.0), RVec(1.0, 0.0, 0.0)), setOf(RVec(0.0, 0.0, 0.0), RVec(1.0, 0.0, 0.0), RVec(2.0, 0.0, 0.0)))
		for ((si, sp) in spotsSets.withIndex()) for ((hi, h) in hits.withIndex()) for ((pi, p) in S.players.withIndex())
			out += "btn|$si|$hi|$pi" to "${ButtonRules.nearestSpot(sp, h, v(p))?.house}"
		for ((bi, sc) in S.berberisScripts.withIndex()) {
			val b = BerberisSequence(sc.first)
			for ((k, op) in sc.second.withIndex()) {
				val at = RVec(op.x.toDouble(), 0.0, 0.0)
				val r = if (op.respawn) b.respawn(at, op.now) else b.broke(at)
				out += "ber|$bi|$k" to "$r ${b.sequence} ${b.isValid} ${b.isRendering} ${b.currentIndex} ${b.lastParticle} ${b.currentTarget} ${b.nextTarget}"
			}
			b.reset(); out += "ber|$bi|reset" to "${b.sequence} ${b.isValid} ${b.isRendering} ${b.currentIndex} ${b.lastParticle} ${b.expectedCount}"
		}
		val n = Golden.verify("batch38-riftrepo", out, minCases = 55787)
		assertTrue(n >= 55787)
	}
}
