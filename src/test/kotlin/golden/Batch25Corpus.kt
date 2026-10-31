// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.dungeon.Boulder
import dev.ashtonite365.apex.features.dungeon.PuzzleRoom
import net.minecraft.core.BlockPos
import kotlin.random.Random

object Batch25Misc {

	val boulderCases: List<Triple<BlockPos, Set<BlockPos>, Set<BlockPos>>> by lazy {
		val r = Random(2501)
		val keys = Boulder.table.keys.toList()
		val cells = buildList { for (z in 24 downTo 9 step 3) for (x in 24 downTo 6 step 3) add(BlockPos(x, 66, z)) }
		List(200) {
			val corner = BlockPos(r.nextInt(-200, 200), 0, r.nextInt(-200, 200))
			val rot = r.nextInt(4)
			val key = when (r.nextInt(5)) {
				0 -> String(CharArray(42) { if (r.nextInt(3) == 0) '0' else '1' })
				1 -> String(CharArray(42) { if (r.nextInt(8) == 0) '1' else '0' })
				else -> keys[r.nextInt(keys.size)].let { k -> if (r.nextInt(4) == 0) k.replaceRange(r.nextInt(42).let { it..it }, if (r.nextBoolean()) "0" else "1") else k }
			}
			val air = HashSet<BlockPos>(); val boulders = HashSet<BlockPos>()
			for ((i, c) in cells.withIndex()) {
				val p = PuzzleRoom.toReal(corner, c, rot)
				if (key[i] == '0') air.add(p) else if (r.nextInt(30) != 0) boulders.add(p)
			}
			Triple(corner, air, boulders)
		}
	}
	val chatLines = listOf("Party > [MVP+] Ashton: hello", "P > Ashton: hi", "Guild > [VIP] Bob [Officer]: yo", "From [MVP++] Alice: dm", "To [MVP++] Alice: dm", "Co-op > Carl: coop msg",
		"Party > [302] ♲ [MVP+] Ashton ⚒: with emblem", "Party > Ashton:no space", "Party > Ashton: ", "Party > Ashton:", "Guild > Bob ⚒ [Member]: x", "Party > [MVP+]Ashton: tight rank",
		"Party > ThisNameIsWayTooLongForMC: x", "Party > a b: x", "Party > [12345] Ashton: lvl5", "party > Ashton: lower", " Party > Ashton: lead space", "Party > Ashton: a: b: c",
		"Co-op > [123] Ⓑ Carl: emb", "From Ashton: !warp", "Guild > Ashton: :star:", "", "Party >", "Party > [YOUTUBE] _x_: hi", "Party > Ash-ton: dash", "From [ADMIN] Hypixel: msg\nnext")
	val emoteMsgs = listOf("<3", "hello <3 there", "ez", "EZ", "ez ez", ":star::star:", "/pc :star:", "/pc", "/w Bob o/", "/warp ez", "/msg Bob :shrug:", "/r h/", "/gc ez gg", "/cc :dab:",
		"/ac  :yes:", " :yes:", ":yes: ", "", "/", "/p ez", "no emotes here", ":tableflip: :tableflip:", "/pc\tez", "o/ o/", "/PC ez", "ez\nez")
}
