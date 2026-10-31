/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: dungeon room corner / rotation lookup (pure, unit-tested). Every room has a blue terracotta marker
 * at one of the four roof corners (x/z +-15 from the cell centre). The old lookup trusted the client WORLD_SURFACE
 * heightmap at the room centre and required the marker at exactly that height, so an off-by-one heightmap, any
 * block above the roof (or a gold block on it) meant "no corner" and every solver built on it drew nothing.
 *
 * Now:
 *  1. Fast path (unchanged behaviour): the heightmap roof, marker at exactly that height.
 *  2. Fallback: scan the cell-centre column down from [SCAN_TOP] to [SCAN_BOTTOM] for the first block that is
 * neither air nor gold (skips gold blocks the same way, WorldScan.getRoomCore; idea only, no code
 *     copied), then accept the marker within +-[Y_TOLERANCE] of that height.
 *  Both paths check the player's cell first, then the other cells of the same multi-cell room.
 *
 * 18 Oct 2026 (secret finders / puzzle solvers "room corner not found" in big rooms and outside the map):
 *  - The other cells of the room used to be "the 8 neighbours with exactly the same roof", so the marker of a 1x3 /
 *    1x4 / L room 2-3 cells away was never reached, and a separate room next door with an equal roof height could
 *    be taken instead. Now [roomCells] walks the room: a 4-neighbour belongs to it when its roof is within
 *    [ROOF_TOLERANCE] AND the 1-block gap between the two cells is built at roof height (separate rooms leave that
 *    gap open; doors are lower). Up to [MAX_ROOM_CELLS] cells.
 *  - Last fallback [cornerColumns]: the marker is the top block of its corner column, so read the top block of each
 *    candidate corner column; exactly one blue terracotta wins. Covers a junk block high above the room centre
 *    (seen in the log: scanned roof y=146 with the heightmap at 255).
 *  - A position outside the 6x6 room grid (boss rooms, the lobby) is reported as such instead of scanned.
 *
 * 18 Oct 2026 (F7, Breakable Walls missing walls): the walk still joined separate rooms. Log 18:44:01-18:44:13
 * (player in cells 1,1 / 0,1): the Boulder lookup alternated between corner (-200,-200) (cell 0,0) and (-138,-170),
 * the marker of the Entrance in cell 1,0. Two markers means the walk had spanned two or more rooms. That gave the
 * wrong cell set, so the wrong shape for DungeonRoomTracker, so no room or the wrong room and no walls. Every room has
 * exactly ONE marker. [roomCells] now refuses a neighbour that has a marker of its own once the room already has one.
 */
package dev.ashtonite365.apex.features.dungeon

object RoomCornerFinder {
	const val SCAN_TOP = 255
	const val SCAN_BOTTOM = 12
	const val Y_TOLERANCE = 2
	const val GRID = 6
	const val MAX_ROOM_CELLS = 4

	const val ROOF_TOLERANCE = 4

	enum class Kind { AIR, GOLD, BLUE_TERRACOTTA, OTHER }

	val CORNER_OFFSETS = listOf(-15 to -15, 15 to -15, 15 to 15, -15 to 15)

	data class Found(val x: Int, val y: Int, val z: Int, val rotation: Int)

	sealed interface Result {
		data class Ok(val found: Found) : Result
		data class Missing(val reason: String) : Result
	}

	fun centre(i: Int) = -185 + 32 * i
	fun cell(v: Int) = Math.floorDiv(v + 200, 32)

	fun scanRoof(x: Int, z: Int, kind: (Int, Int, Int) -> Kind, top: Int = SCAN_TOP, bottom: Int = SCAN_BOTTOM): Int? {
		for (y in top downTo bottom) {
			val k = kind(x, y, z)
			if (k != Kind.AIR && k != Kind.GOLD) return y
		}
		return null
	}

	fun gapClosed(a: Pair<Int, Int>, b: Pair<Int, Int>, y: Int, kind: (Int, Int, Int) -> Kind): Boolean {
		val (gx, gz) = if (a.first != b.first) {

			(-200 + 32 * maxOf(a.first, b.first) - 1) to centre(a.second)
		} else centre(a.first) to (-200 + 32 * maxOf(a.second, b.second) - 1)
		return (y - 1..y + 1).any { kind(gx, it, gz).let { k -> k != Kind.AIR } }
	}

	fun roomCells(
		ix: Int, iz: Int, roofOf: (Int, Int) -> Int?, kind: (Int, Int, Int) -> Kind,
		hasMarker: (Pair<Int, Int>) -> Boolean = { cellHasMarker(it, kind) },
	): List<Pair<Int, Int>> {
		val own = roofOf(centre(ix), centre(iz)) ?: return listOf(ix to iz)
		val out = mutableListOf(ix to iz)
		var marked = hasMarker(ix to iz)
		var i = 0
		while (i < out.size && out.size < MAX_ROOM_CELLS) {
			val c = out[i++]
			for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
				val n = c.first + dx to c.second + dz
				if (n in out || n.first !in 0 until GRID || n.second !in 0 until GRID) continue
				val roof = roofOf(centre(n.first), centre(n.second)) ?: continue
				if (Math.abs(roof - own) > ROOF_TOLERANCE) continue
				if (!gapClosed(c, n, own, kind) && (roof == own || !gapClosed(c, n, roof, kind))) continue

				val nMarked = hasMarker(n)
				if (marked && nMarked) continue
				marked = marked || nMarked
				out += n
				if (out.size >= MAX_ROOM_CELLS) break
			}
		}
		return out
	}

	fun cellHasMarker(cell: Pair<Int, Int>, kind: (Int, Int, Int) -> Kind): Boolean =
		CORNER_OFFSETS.any { (ox, oz) ->
			val x = centre(cell.first) + ox; val z = centre(cell.second) + oz
			scanRoof(x, z, kind)?.let { kind(x, it, z) == Kind.BLUE_TERRACOTTA } == true
		}

	fun cornerColumns(cells: List<Pair<Int, Int>>, kind: (Int, Int, Int) -> Kind): Found? {
		val hits = mutableListOf<Found>()
		for ((cx, cz) in cells) CORNER_OFFSETS.forEachIndexed { i, (ox, oz) ->
			val x = centre(cx) + ox; val z = centre(cz) + oz
			val y = scanRoof(x, z, kind) ?: return@forEachIndexed
			if (kind(x, y, z) == Kind.BLUE_TERRACOTTA) hits += Found(x, y, z, i * 90)
		}
		return hits.singleOrNull()
	}

	fun find(px: Int, pz: Int, kind: (Int, Int, Int) -> Kind, heightmapRoof: (Int, Int) -> Int?): Result {
		val ix = cell(px); val iz = cell(pz)
		if (ix !in 0 until GRID || iz !in 0 until GRID)
			return Result.Missing("outside the dungeon room grid (cell $ix, $iz; boss room or lobby)")

		fun search(roofOf: (Int, Int) -> Int?, tolerance: Int): Found? {
			val own = roofOf(centre(ix), centre(iz)) ?: return null
			for ((cx0, cz0) in roomCells(ix, iz, roofOf, kind)) {
				val cx = centre(cx0); val cz = centre(cz0)

				for (base in listOfNotNull(own, roofOf(cx, cz)).distinct())
					for (dy in (0..tolerance).flatMap { if (it == 0) listOf(0) else listOf(it, -it) }) {
						CORNER_OFFSETS.forEachIndexed { i, (ox, oz) ->
							if (kind(cx + ox, base + dy, cz + oz) == Kind.BLUE_TERRACOTTA) return Found(cx + ox, base + dy, cz + oz, i * 90)
						}
					}
			}
			return null
		}

		search(heightmapRoof, 0)?.let { return Result.Ok(it) }
		val scanCache = HashMap<Pair<Int, Int>, Int?>()
		val scanned = { x: Int, z: Int -> scanCache.getOrPut(x to z) { scanRoof(x, z, kind) } }
		if (scanned(centre(ix), centre(iz)) == null)
			return Result.Missing("no roof above the cell centre (${centre(ix)}, ${centre(iz)}); chunk not loaded?")
		search(scanned, Y_TOLERANCE)?.let { return Result.Ok(it) }
		cornerColumns(roomCells(ix, iz, scanned, kind), kind)?.let { return Result.Ok(it) }
		val hm = heightmapRoof(centre(ix), centre(iz))
		return Result.Missing("no blue terracotta at the +-15 corners within +-$Y_TOLERANCE of roof y=${scanned(centre(ix), centre(iz))} (heightmap y=$hm, cell $ix, $iz)")
	}
}

class RoomCornerLog(private val sink: (String) -> Unit) {
	private val seen = HashSet<Triple<String, Int, Int>>()

	fun missing(solver: String, cellX: Int, cellZ: Int, reason: String): Boolean {
		if (seen.size >= MAX_PER_WORLD) return false
		if (!seen.add(Triple(solver, cellX, cellZ))) return false
		sink("[Apex] $solver: room corner not found ($reason)")
		return true
	}

	fun clear() = seen.clear()

	companion object {
		const val MAX_PER_WORLD = 40
	}
}
