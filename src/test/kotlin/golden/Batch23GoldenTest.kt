// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.features.kit.devices.SharpshooterHighlight
import dev.ashtonite365.apex.features.kit.dungeon.DungeonExtras
import dev.ashtonite365.apex.features.kit.dungeon.RoomClear
import dev.ashtonite365.apex.features.kit.puzzles.IceFillFloors
import dev.ashtonite365.apex.features.kit.render.GyroWandRing
import dev.ashtonite365.apex.features.kit.skyblock.PetKeybinds
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch23GoldenTest {
	@Test
	fun kit() {
		val S = Batch23Kit
		val out = mutableListOf<Pair<String, String>>()
		out += "sharpKeys" to "${SharpshooterHighlight.TConfig.allOptions.keys} ${SharpshooterHighlight.TConfig.allOptions.values.map { it.riskLevel }} ${SharpshooterHighlight.TARGETS}"
		for (l in S.deviceLines) out += "device|$l" to "${SharpshooterHighlight.deviceCompleter(l)}"
		out += "petKeys" to "${PetKeybinds.TConfig.allOptions.keys} ${PetKeybinds.TConfig.allOptions.values.map { it.riskLevel }} ${PetKeybinds.Logic.petSlots.toList()} ${PetKeybinds.Logic.NEXT} ${PetKeybinds.Logic.PREVIOUS} ${PetKeybinds.Logic.CLOSE}"
		for (t in S.petTitles) out += "pets|$t" to "${PetKeybinds.Logic.page(t)}"
		for ((gi, g) in S.grids.withIndex()) for ((mx, my) in S.markers) out += "cell|$gi|$mx|$my" to Golden.run { "${RoomClear.cellOf(mx, my, g)}" }
		val ticks = listOf(null) + DungeonMap.Tick.entries
		for (o in ticks) for (n in ticks) for (m in DungeonExtras.ClearMode.entries) out += "alert|$o|$n|$m" to "${RoomClear.alertFor(o, n, m)}"
		out += "gyroKeys" to "${GyroWandRing.TConfig.allOptions.keys} ${GyroWandRing.TConfig.allOptions.values.map { it.riskLevel }} ${GyroWandRing.Logic.RADIUS} ${GyroWandRing.Logic.RANGE} ${GyroWandRing.Logic.COOLDOWN_MS}"
		for (a in S.actionBars) out += "cast|$a" to "${GyroWandRing.Logic.isCast(a)}"
		for (c in S.castTimes) for (n in S.nows) out += "cd|$c|$n" to "${GyroWandRing.Logic.onCooldown(c, n)}"
		for ((x, rad, seg) in S.ringArgs) out += "ring|$x|$rad|$seg" to Golden.run { GyroWandRing.Logic.ring(Vec3(x, 64.0, -x), rad, seg).joinToString(";") { "${it.x},${it.y},${it.z}" } }
		out += "ringDefault" to GyroWandRing.Logic.ring(Vec3(1.0, 2.0, 3.0)).joinToString(";") { "${it.x},${it.y},${it.z}" }
		val d = IceFillFloors.data!!
		out += "iceData" to "$d"
		for ((i, c) in S.iceCases.withIndex()) {
			val (corner, optimal, air) = c
			out += "icePat|$i" to "${IceFillFloors.patterns(d) { it in air }}"
			out += "iceFloors|$i" to "${IceFillFloors.floors(d, corner, optimal) { it in air }}"
			out += "iceSolve|$i" to "${IceFillFloors.solve(d, corner, !optimal) { it in air }}"
		}
		for (j in S.badJson) out += "iceParse|$j" to Golden.run { "${IceFillFloors.parse(j)}" }
		out += "iceShort" to Golden.run { "${IceFillFloors.patterns(IceFillFloors.parse("{\"identifier\":[[[{\"x\":1,\"y\":2,\"z\":3}]]],\"easy\":[],\"hard\":[]}")) { true }}" }
		val n = Golden.verify("batch23-kit", out, minCases = 1380)
		assertTrue(n >= 1380)
	}
}
