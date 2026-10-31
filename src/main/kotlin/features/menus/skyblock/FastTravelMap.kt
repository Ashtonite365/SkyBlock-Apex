// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

data class WarpRow(
	val id: String, val command: String, val island: String, val fx: Double?, val fy: Double?,
	val aliases: List<String> = emptyList(), val bottomRow: Boolean = false,

	val verified: Boolean = true,
) {
	val onImage get() = fx != null && fy != null && !bottomRow
}

object FastTravelWarpTable {

	const val IMAGE_W = 1280.0
	const val IMAGE_H = 720.0
	fun fx(px: Int) = px / IMAGE_W
	fun fy(py: Int) = py / IMAGE_H

	private fun row(id: String, island: String, px: Int, py: Int, vararg aliases: String) =
		WarpRow(id, "/warp $id", island, fx(px), fy(py), aliases.toList())
	private fun unverified(id: String, island: String, px: Int, py: Int) =
		WarpRow(id, "/warp $id", island, fx(px), fy(py), verified = false)
	private fun bottom(id: String, island: String, px: Int, py: Int, vararg aliases: String) =
		WarpRow(id, "/warp $id", island, fx(px), fy(py), aliases.toList(), bottomRow = true)

	val ROWS: List<WarpRow> = listOf(
		row("hub", "Hub", 621, 425),
		row("elizabeth", "Hub", 614, 484),
		row("stonks", "Hub", 588, 416),
		row("taylor", "Hub", 652, 444),
		row("carnival", "Hub", 559, 471),
		row("castle", "Hub", 436, 504),
		row("crypts", "Hub", 502, 400, "crypt"),
		row("museum", "Hub", 645, 475),
		row("da", "Hub", 692, 591),
		row("wizard", "Hub", 722, 516),

		row("rift", "The Rift", 723, 476),
		row("dungeon_hub", "Dungeon Hub", 662, 269),
		row("home", "Home", 971, 550),
		row("garden", "Garden", 1106, 550),
		row("barn", "The Barn", 931, 338),
		row("desert", "Mushroom Desert", 1100, 212),
		row("trapper", "Mushroom Desert", 1188, 175),
		row("park", "The Park", 300, 512),
		row("jungle", "The Park", 325, 481),
		row("howl", "The Park", 269, 544),
		row("gold", "Gold Mine", 781, 219),
		row("deep", "Deep Caverns", 919, 75),

		row("mines", "Dwarven Mines", 885, 132, "dwarves"),
		row("forge", "Dwarven Mines", 925, 132),
		row("basecamp", "Dwarven Mines", 965, 132),
		row("crystals", "Crystal Hollows", 925, 178),
		row("spider", "Spider's Den", 338, 294),
		row("nest", "Spider's Den", 412, 256),
		row("arachne", "Spider's Den", 281, 269),
		row("end", "The End", 150, 131),
		row("drag", "The End", 200, 112),
		row("void", "The End", 100, 156, "sepulture"),
		row("nether", "Crimson Isle", 500, 119),
		row("kuudra", "Crimson Isle", 588, 50),
		row("smold", "Crimson Isle", 650, 94, "smoldering"),

		unverified("wasteland", "Crimson Isle", 602, 101),
		unverified("dragontail", "Crimson Isle", 461, 86),
		unverified("scarleton", "Crimson Isle", 704, 108),
		row("winter", "Jerry's Workshop", 138, 419, "jerry"),
		row("bayou", "Backwater Bayou", 88, 594),
		row("lotus", "Lotus Atoll", 170, 686, "atoll"),
		row("galatea", "Galatea", 128, 307, "moonglade"),
		row("murkwater", "Galatea", 94, 311),
		row("torrhus", "Galatea", 162, 302),
		row("spring", "Galatea", 166, 268),

	)

	fun find(id: String): WarpRow? { val k = id.trim().lowercase(); return ROWS.firstOrNull { it.id == k || k in it.aliases } }
}

data class PlacedPortal(val row: WarpRow, val x: Int, val y: Int, val live: FancyWarpEntry?)

data class FastTravelMapLayout(
	val image: DrawRect, val portals: List<PlacedPortal>,

	val listed: List<FancyWarpEntry>, val listedLines: List<String>, val listY: Int, val navY: Int,
)

fun interface CommandSink { fun send(command: String) }

object FastTravelMap {
	const val TEXTURE_PATH = "textures/gui/apex/fast_travel_map.png"
	const val MARKER = 3
	const val NAV_H = 14

	fun layout(x: Int, y: Int, w: Int, h: Int, islands: List<FancyIsland>, measure: (String) -> Int, lineH: Int = 9): FastTravelMapLayout {
		val live = islands.flatMap { it.warps }
		val listed = live.filter { (FastTravelWarpTable.find(it.warp) ?: FastTravelWarpTable.find(it.label)) == null }.distinctBy { it.warp }
		val textW = w - 12
		val lines = if (listed.isEmpty()) emptyList() else wrap("Other warps: " + listed.joinToString(", ") { "${it.label} (/warp ${it.warp})" }, textW, measure)
		val navY = y + h - NAV_H - 4
		val listY = navY - 2 - lines.size * lineH
		val top = y + 20
		val img = FancyWarp.fit(x + 4, top, w - 8, (listY - 2 - top).coerceAtLeast(1))
		val portals = FastTravelWarpTable.ROWS.filter { it.fx != null && it.fy != null }.map { r ->
			val (px, py) = toScreen(img, r.fx!!, r.fy!!)
			PlacedPortal(r, px, py, live.firstOrNull { FastTravelWarpTable.find(it.warp) == r })
		}
		return FastTravelMapLayout(img, portals, listed, lines, listY, navY)
	}

	fun toScreen(img: DrawRect, fx: Double, fy: Double) = img.map(NPoint(fx, fy))

	fun click(p: PlacedPortal, sink: CommandSink): Boolean {

		if (!p.row.verified) return false
		sink.send(p.row.command); return true
	}

	fun wrap(text: String, width: Int, measure: (String) -> Int): List<String> {
		val out = mutableListOf<String>(); var cur = ""
		for (word in text.split(' ')) {
			val cand = if (cur.isEmpty()) word else "$cur $word"
			if (measure(cand) <= width) { cur = cand; continue }
			if (cur.isNotEmpty()) out += cur
			cur = word
			while (measure(cur) > width && cur.length > 1) {
				var n = cur.length - 1
				while (n > 1 && measure(cur.take(n)) > width) n--
				out += cur.take(n); cur = cur.drop(n)
			}
		}
		if (cur.isNotEmpty()) out += cur
		return out
	}

	object Texture {
		const val NAVY = 0xFF11131C.toInt()
		var loggedMissing = false; private set
		fun available(exists: () -> Boolean, log: (String) -> Unit): Boolean {
			val ok = runCatching(exists).getOrDefault(false)
			if (!ok && !loggedMissing) { loggedMissing = true; log("[Apex] fast travel map texture missing: apex:$TEXTURE_PATH (plain navy fallback)") }
			return ok
		}
		internal fun resetForTests() { loggedMissing = false }
	}
}
