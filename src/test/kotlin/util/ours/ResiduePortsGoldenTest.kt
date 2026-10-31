// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.features.fishing.NearbySeaCreatures
import dev.ashtonite365.apex.features.fishing.NearbySeaCreatures.Creature
import dev.ashtonite365.apex.features.fishing.SeaCreatureBook
import dev.ashtonite365.apex.features.kit.devices.ArrowAlignLogic
import dev.ashtonite365.apex.features.kit.devices.ArrowAlignState
import dev.ashtonite365.apex.features.kit.devices.SimonSaysState
import dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureExtras
import dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureSettings

class ResiduePortsGoldenTest {
	private fun num(f: Fuzz): String {
		val base = when (f.r.nextInt(4)) {
			0 -> f.r.nextInt(1000).toString()
			1 -> "%,d".format(f.r.nextInt(5_000_000))
			2 -> "${f.r.nextInt(100)}.${f.r.nextInt(10)}"
			else -> f.str("0123456789.,", 6)
		}
		return base + f.pick(listOf("", "", "k", "M", "B", "m", "x"))
	}

	private val known = listOf("Sea Walker" to false, "Lord Jawbus" to true, "Thunder" to true, "Sea Guardian" to false, "Night Squid" to false)

	private fun tag(f: Fuzz): String {
		val name = if (f.r.nextInt(6) == 0) "Zombie" else f.pick(known).first
		return when (f.r.nextInt(5)) {
			0 -> "[Lv${f.r.nextInt(600)}] $name ${num(f)}/${num(f)}❤"
			1 -> "$name ${num(f)}❤"
			2 -> "[Lv1] $name"
			3 -> "§c$name §a${num(f)}§f/§a${num(f)}§c❤"
			else -> f.str("ab ❤/0123456789kMB.,SeaWlkr", 24)
		}
	}

	@Test
	fun nearbySeaCreatures() {
		val g = Golden("residue-nearby-sea-creatures")
		val f = Fuzz(2601)
		repeat(300) { i ->
			val t = tag(f)
			g.case("parseHealth $i $t") { NearbySeaCreatures.parseHealth(t) }
			g.case("fromTag $i") { NearbySeaCreatures.fromTag(t, known) }
			val s = num(f)
			g.case("parseShort $s") { NearbySeaCreatures.parseShort(s) }
			val cur = f.r.nextDouble(-5.0, 120.0); val max = f.r.nextDouble(-5.0, 100.0)
			g.case("healthColour $i") { NearbySeaCreatures.healthColour(cur, if (i % 7 == 0) 0.0 else max) }
		}
		repeat(80) { i ->
			val cs = List(f.r.nextInt(9)) {
				val (n, rare) = f.pick(known)
				if (f.r.nextInt(4) == 0) Creature(n, rare, null, null) else Creature(n, rare, num(f), if (f.r.nextInt(8) == 0) null else num(f))
			}
			for (c in cs) {
				g.case("fraction $i $c") { NearbySeaCreatures.fraction(c) }
				g.case("healthLine $i $c") { NearbySeaCreatures.healthLine(c) }
			}
			val rf = f.r.nextBoolean()
			g.case("sorted $i $rf") { NearbySeaCreatures.sorted(cs, rf) }
			val since = if (f.r.nextBoolean()) null else "${f.r.nextInt(5)}m ${f.r.nextInt(60)}s"
			g.case("summary $i") { NearbySeaCreatures.summaryLine(cs.size - f.r.nextInt(2), since) }
			val opts = NearbySeaCreatures.Options(f.r.nextInt(5) != 0, f.r.nextInt(-1, 6), f.r.nextBoolean())
			g.case("lines $i $opts") { NearbySeaCreatures.lines(cs, since, opts) }
		}
		g.verify()
	}

	@Test
	fun seaCreatureExtrasAndSettings() {
		val g = Golden("residue-sea-creature-extras")
		val f = Fuzz(2602)
		repeat(40) { i ->
			val c = Vec3(f.r.nextDouble(-100.0, 100.0), f.r.nextDouble(0.0, 200.0), f.r.nextDouble(-100.0, 100.0))
			val radius = f.r.nextDouble(0.0, 40.0); val dy = f.pick(listOf(-15.0, 0.0, 15.0, 45.0))
			val n = f.r.nextInt(1, 12)
			g.case("ring $i") { PlusSeaCreatureExtras.ring(c, radius, dy, n).joinToString(";") { "%.6f,%.6f,%.6f".format(it.x, it.y, it.z) } }
			g.case("ringDefault $i") { PlusSeaCreatureExtras.ring(c, radius, dy).size }
		}
		g.case("range") { PlusSeaCreatureExtras.LOOTSHARE_RANGE }
		repeat(60) { i ->
			val list = List(f.r.nextInt(5)) { f.str(" ,ab SeaWlkrJAWBUS", 10) }.joinToString(f.pick(listOf(",", ", ", " ,")))
			g.case("names $i $list") { PlusSeaCreatureSettings.names(list).sorted() }
		}
		for (sc in SeaCreatureBook.kinds) {
			g.case("creatureOf ${sc.name}") { PlusSeaCreatureSettings.creatureOf(sc.catchLine) }
			g.case("creatureOf padded ${sc.name}") { PlusSeaCreatureSettings.creatureOf(" " + sc.catchLine) }
		}
		repeat(30) { i -> val s = f.str("You caught a Sea Walker! ab", 30); g.case("creatureOf fuzz $i") { PlusSeaCreatureSettings.creatureOf(s) } }
		g.verify()
	}

	@Test
	fun simonSays() {
		val g = Golden("residue-simon-says")
		val f = Fuzz(2603)
		repeat(120) { run ->
			val s = SimonSaysState()
			if (f.r.nextBoolean()) s.firstPhase = false
			val log = StringBuilder()
			repeat(40) {
				val y = 120 + f.r.nextInt(4); val z = 92 + f.r.nextInt(4)
				when (f.r.nextInt(6)) {
					0, 1 -> s.onLanternShown(BlockPos(111, y, z))
					2 -> { val b = BlockPos(110, y, z); log.append("c=").append(s.isCorrectClick(b)).append(" r=").append(s.roundFinishedBy(b)).append(' '); s.onButtonPressed(b) }
					3 -> s.onTick(f.r.nextInt(16))
					4 -> if (f.r.nextInt(5) == 0) s.onStartPressed() else s.onTick(f.r.nextInt(16))
					else -> log.append("g=").append(SimonSaysState.inGrid(BlockPos(f.pick(listOf(110, 111)), 118 + f.r.nextInt(7), 90 + f.r.nextInt(7)), 110)).append(' ')
				}
				log.append(s.order.size).append('/').append(s.clickNeeded).append('/').append(s.firstPhase).append('/').append(s.ticksSinceLantern)
					.append(s.remaining().joinToString(",", "[", "]") { "${it.y}.${it.z}" }).append(' ')
			}
			g.case("run $run") { log.toString() }
		}
		g.case("consts") { "${SimonSaysState.START_BUTTON} ${SimonSaysState.CENTER}" }
		g.verify()
	}

	@Test
	fun arrowAlign() {
		val g = Golden("residue-arrow-align")
		val f = Fuzz(2604)
		for (x in -3..-1) for (y in 118..126) for (z in 73..81) g.case("indexOf $x $y $z") { ArrowAlignLogic.indexOf(BlockPos(x, y, z)) }
		for (i in -1..25) g.case("posOf $i") { ArrowAlignLogic.posOf(i) }
		for (c in 0..7) for (t in 0..7) g.case("clicksNeeded $c $t") { ArrowAlignLogic.clicksNeeded(c, t) }
		for ((k, sol) in ArrowAlignLogic.SOLUTIONS.withIndex()) {
			g.case("solution $k") { sol }
			repeat(6) { j ->
				val rot = sol.map { if (it == -1) (if (f.r.nextInt(10) == 0) f.r.nextInt(8) else -1) else (if (f.r.nextInt(12) == 0) -1 else f.r.nextInt(8)) }
				g.case("solutionFor $k $j $rot") { ArrowAlignLogic.solutionFor(rot) }
				g.case("clicks $k $j") { ArrowAlignLogic.clicks(rot).toSortedMap() }
				val idx = if (f.r.nextInt(5) == 0) null else f.r.nextInt(25)
				val clicks = ArrowAlignLogic.clicks(rot)
				for (solved in listOf(false, true)) for (sneak in listOf(false, true)) g.case("shouldBlock $k $j $idx $solved $sneak") { ArrowAlignLogic.shouldBlock(idx, clicks, solved, sneak) }
			}
		}
		repeat(40) { run ->
			val st = ArrowAlignState()
			val sol = f.pick(ArrowAlignLogic.SOLUTIONS)
			var now = f.r.nextLong(0, 100_000)
			val log = StringBuilder()
			repeat(30) {
				now += f.r.nextLong(0, 700)
				when (f.r.nextInt(5)) {
					0, 1 -> st.update(sol.map { if (it == -1) -1 else f.r.nextInt(8) }, now)
					2 -> { val i = f.r.nextInt(25); log.append("b").append(st.shouldBlock(i, f.r.nextInt(4) == 0)).append(' '); st.onClick(i, now) }
					3 -> log.append("n").append(st.shouldBlock(null, false)).append(' ')
					else -> if (f.r.nextInt(8) == 0) st.reset() else st.update(st.rotations.toList(), now)
				}
				log.append(st.rotations.joinToString("")).append(st.clicks().toSortedMap()).append(' ')
			}
			g.case("state $run") { log.toString() }
		}
		g.case("hold") { ArrowAlignState.HOLD_MS }
		g.case("consts") { "${ArrowAlignLogic.GRID_CORNER} ${ArrowAlignLogic.CENTER}" }
		g.verify()
	}
}
