// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.skyblock.CommandSink
import dev.ashtonite365.apex.features.menus.skyblock.FancyWarp
import dev.ashtonite365.apex.features.menus.skyblock.FastTravelMap
import dev.ashtonite365.apex.features.menus.skyblock.FastTravelParser
import dev.ashtonite365.apex.features.menus.skyblock.FastTravelWarpTable
import dev.ashtonite365.apex.features.misc.WarpMenu
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class FastTravelMapTest {
	private val d = RealDumpFixtures.load("fast-travel-2119")
	private val model = FastTravelParser.parse(d.title, d.slots)!!
	private fun e(warp: String, mode: String) = WarpMenu.Entry(warp, mode, emptyList(), BlockPos.ZERO)

	private val repo = listOf("hub", "elizabeth", "stonks", "taylor", "castle").map { e(it, "hub") }
	private val islands = FancyWarp.build(model, repo)
	private val measure: (String) -> Int = { it.length * 6 }

	private val expected = mapOf(
		"hub" to (621 to 425), "elizabeth" to (614 to 484), "stonks" to (588 to 416), "taylor" to (652 to 444),
		"carnival" to (559 to 471), "castle" to (436 to 504), "crypts" to (502 to 400), "museum" to (645 to 475),
		"da" to (692 to 591), "wizard" to (722 to 516), "dungeon_hub" to (662 to 269), "home" to (971 to 550),
		"garden" to (1106 to 550), "barn" to (931 to 338), "desert" to (1100 to 212), "trapper" to (1188 to 175),
		"park" to (300 to 512), "jungle" to (325 to 481), "howl" to (269 to 544), "gold" to (781 to 219), "deep" to (919 to 75),
		"spider" to (338 to 294), "nest" to (412 to 256), "arachne" to (281 to 269), "end" to (150 to 131), "drag" to (200 to 112),
		"void" to (100 to 156), "nether" to (500 to 119), "kuudra" to (588 to 50), "smold" to (650 to 94), "winter" to (138 to 419),
		"bayou" to (88 to 594), "galatea" to (128 to 307), "murkwater" to (94 to 311), "torrhus" to (162 to 302), "spring" to (166 to 268),
		"mines" to (885 to 132), "forge" to (925 to 132), "basecamp" to (965 to 132), "crystals" to (925 to 178),
		"rift" to (723 to 476), "lotus" to (170 to 686),
	)

	@Test
	fun tableHasEveryWarpWithFractionAndCommand() {
		for ((id, p) in expected) {
			val r = FastTravelWarpTable.find(id) ?: error("missing $id")
			assertEquals(id, r.id); assertEquals("/warp $id", r.command)
			assertEquals(p.first / 1280.0, r.fx!!, 1e-9, id); assertEquals(p.second / 720.0, r.fy!!, 1e-9, id)
		}
		for (k in listOf("wasteland", "dragontail", "scarleton")) assertTrue(FastTravelWarpTable.find(k)!!.onImage, "kept $k")
		assertEquals(FastTravelWarpTable.ROWS.size, FastTravelWarpTable.ROWS.map { it.id }.toSet().size, "no duplicate rows")
		assertEquals(0.5, FastTravelWarpTable.fx(640)); assertEquals(0.5, FastTravelWarpTable.fy(360))
	}

	@Test
	fun aliases() {
		for ((a, id) in listOf("crypt" to "crypts", "jerry" to "winter", "moonglade" to "galatea", "smoldering" to "smold", "dwarves" to "mines", "atoll" to "lotus"))
			assertEquals(id, FastTravelWarpTable.find(a)?.id, a)
	}

	@Test
	fun theSixLoggedWarpsGetPortals() {
		val live = islands.flatMap { it.warps }.map { it.warp }
		for (w in listOf("elizabeth", "stonks", "taylor", "dwarves", "crystals", "rift")) {
			assertTrue(w in live, "$w read live from the dump")
			val r = FastTravelWarpTable.find(w)!!
			assertTrue(r.onImage || r.bottomRow, w)
		}
		assertEquals(emptyList<String>(), FancyWarp.unmarked(islands))
	}

	@Test
	fun bottomRowEmpty() {
		assertEquals(emptyList<String>(), FastTravelWarpTable.ROWS.filter { it.bottomRow }.map { it.id })
		for (scale in listOf(3, 4)) {
			val b = bounds(scale)
			val lay = FastTravelMap.layout(b.x, b.y, b.w, b.h, islands, measure)

			assertTrue(lay.portals.none { it.row.bottomRow }, "no bottom-row markers at $scale")
			assertTrue(lay.portals.all { it.row.onImage }, "every marker on the image at $scale")
			assertEquals(FastTravelWarpTable.ROWS.size, lay.portals.size)
		}
	}

	@Test
	fun minesForgeBasecampRowCrystalsBelowOnDeepCavernsAndRiftOnWizardTower() {

		for (id in listOf("mines", "forge", "basecamp", "crystals")) {
			val r = FastTravelWarpTable.find(id)!!
			assertTrue(r.onImage && !r.bottomRow, id)
			assertTrue(r.fx!! * 1280 in 843.0..992.0 && r.fy!! * 720 in 22.0..213.0, "$id on Deep Caverns")
		}
		val mines = FastTravelWarpTable.find("mines")!!
		val crystals = FastTravelWarpTable.find("crystals")!!
		val forge = FastTravelWarpTable.find("forge")!!
		val basecamp = FastTravelWarpTable.find("basecamp")!!
		assertEquals(mines.fy!!, forge.fy!!, 1e-9, "Mines and Forge in one row")
		assertEquals(forge.fy!!, basecamp.fy!!, 1e-9, "Forge and Base Camp in one row")
		assertTrue(mines.fx!! < forge.fx!! && forge.fx!! < basecamp.fx!!, "row order Mines, Forge, Base Camp")
		assertTrue(crystals.fy!! > forge.fy!!, "Crystal Hollows below the row")
		assertTrue(crystals.fx!! > mines.fx!! && crystals.fx!! < basecamp.fx!!, "Crystal Hollows under the row")
		assertTrue(Math.abs(crystals.fx!! - (mines.fx!! + basecamp.fx!!) / 2) * 1280 <= 10, "Crystal Hollows roughly centred")
		val rift = FastTravelWarpTable.find("rift")!!
		assertTrue(rift.onImage && !rift.bottomRow)

		assertTrue(rift.fx!! * 1280 in 699.0..744.0 && rift.fy!! * 720 in 453.0..492.0, "rift on the blue tower")

		val wizard = FastTravelWarpTable.find("wizard")!!
		assertTrue(wizard.onImage && !wizard.bottomRow)
		assertTrue(wizard.fx!! * 1280 in 712.0..732.0 && wizard.fy!! * 720 in 493.0..526.0, "wizard on the Wizard Tower")
		assertTrue(wizard.fy!! > rift.fy!!, "wizard below the Rift roof pin")
		for (scale in listOf(3, 4)) {
			val b = bounds(scale)
			val lay = FastTravelMap.layout(b.x, b.y, b.w, b.h, islands, measure)
			val ps = lay.portals
			val gap = 2 * (FastTravelMap.MARKER + 1)
			for (id in listOf("mines", "forge", "basecamp", "crystals", "rift", "wizard")) {
				val p = ps.first { it.row.id == id }
				for (o in ps) if (o !== p) assertTrue(Math.abs(o.x - p.x) > gap || Math.abs(o.y - p.y) > gap, "$id overlaps ${o.row.id} at $scale")
				assertTrue(p.y + FastTravelMap.MARKER + 1 < lay.navY, "$id clear of the bottom buttons at $scale")
			}
		}
	}

	@Test
	fun lotusPinOnAtollArtBelowBayou() {
		val lotus = FastTravelWarpTable.find("lotus")!!
		val bayou = FastTravelWarpTable.find("bayou")!!
		assertTrue(lotus.onImage && !lotus.bottomRow, "lotus is a map pin")
		assertEquals("/warp lotus", lotus.command)
		assertTrue(lotus.fy!! > bayou.fy!!, "Lotus below Bayou")

		assertTrue(lotus.fx!! * 1280 in 136.0..204.0 && lotus.fy!! * 720 in 644.0..715.0, "Lotus on the Lotus Atoll art")
		for (scale in listOf(3, 4)) {
			val b = bounds(scale)
			val lay = FastTravelMap.layout(b.x, b.y, b.w, b.h, islands, measure)
			val p = lay.portals.first { it.row.id == "lotus" }
			val gap = 2 * (FastTravelMap.MARKER + 1)
			for (o in lay.portals) if (o !== p) assertTrue(Math.abs(o.x - p.x) > gap || Math.abs(o.y - p.y) > gap, "lotus overlaps ${o.row.id} at $scale")
			assertTrue(p.y + FastTravelMap.MARKER < lay.navY, "lotus clear of the bottom buttons at $scale")
			val sent = mutableListOf<String>(); FastTravelMap.click(p, CommandSink { sent += it }); assertEquals(listOf("/warp lotus"), sent)
		}
	}

	private fun bounds(scale: Int) = MenuBounds.centred(1920 / scale, 1080 / scale, 520, 330)

	@Test
	fun noPinOverlapsAnyOtherOrTheBottomButtonsAtScale3And4() {
		for (scale in listOf(3, 4)) {
			val b = bounds(scale)
			val lay = FastTravelMap.layout(b.x, b.y, b.w, b.h, islands, measure)
			val ps = lay.portals
			val gap = 2 * (FastTravelMap.MARKER + 1)
			for ((i, p) in ps.withIndex()) {
				for (o in ps.drop(i + 1)) assertTrue(Math.abs(o.x - p.x) > gap || Math.abs(o.y - p.y) > gap, "${p.row.id} overlaps ${o.row.id} at $scale")
				assertTrue(p.y + FastTravelMap.MARKER + 1 < lay.navY, "${p.row.id} clear of the bottom buttons at $scale")
			}
		}
	}

	@Test
	fun spreadClustersKeepTheirRealLayout() {
		fun at(id: String) = FastTravelWarpTable.find(id)!!.let { it.fx!! * 1280 to it.fy!! * 720 }

		val (gx, gy) = at("galatea"); val (mx, my) = at("murkwater"); val (tx, ty) = at("torrhus"); val (sx, sy) = at("spring")
		assertTrue(mx < gx && my > gy, "murkwater west-south-west of galatea")
		assertTrue(tx > gx && ty < gy, "torrhus east-north-east of galatea")
		assertTrue(sy < ty && sx >= tx, "spring north(-north-east) of torrhus")

		for (id in listOf("galatea", "murkwater", "torrhus", "spring")) { val (x, y) = at(id); assertTrue(x in 82.0..181.0 && y in 257.0..337.0, "$id on Galatea") }

		val (hx, hy) = at("hub")
		assertTrue(at("stonks").first < hx && at("taylor").first > hx && at("museum").first > hx, "hub west/east neighbours")
		assertTrue(at("elizabeth").second > at("museum").second && at("museum").second > at("taylor").second && at("taylor").second > hy, "hub north-south order")
	}

	@Test
	fun imageFitsAndBottomRowsVisibleAtScale3And4() {
		for (scale in listOf(3, 4)) {
			val b = bounds(scale)
			val lay = FastTravelMap.layout(b.x, b.y, b.w, b.h, islands, measure)
			val i = lay.image
			assertTrue(i.x >= b.x && i.y >= b.y && i.x + i.w <= b.x + b.w && i.y + i.h <= lay.listY, "image inside panel at $scale")
			assertEquals(16.0 / 9.0, i.w.toDouble() / i.h, 0.02, "aspect kept")
			assertTrue(lay.navY >= i.y + i.h && lay.navY + FastTravelMap.NAV_H <= b.y + b.h, "nav row visible at $scale")
			for (p in lay.portals) assertTrue(p.x in i.x..(i.x + i.w) && p.y in i.y..(i.y + i.h), "${p.row.id} on image")

			val hub = lay.portals.first { it.row.id == "hub" }
			assertEquals(i.x + Math.round(621 / 1280.0 * i.w).toInt(), hub.x); assertEquals(i.y + Math.round(425 / 720.0 * i.h).toInt(), hub.y)
		}
	}

	@Test
	fun noPerItemBoxes() {

		val b = bounds(3)
		val full = FastTravelMap.layout(b.x, b.y, b.w, b.h, islands, measure)
		val none = FastTravelMap.layout(b.x, b.y, b.w, b.h, emptyList(), measure)
		assertEquals(none.portals.map { it.row.id to (it.x to it.y) }, full.portals.map { it.row.id to (it.x to it.y) })
		assertEquals(FastTravelWarpTable.ROWS.size, full.portals.size)
		assertTrue(full.listed.isEmpty() && full.listedLines.isEmpty(), "nothing left to list from the real dump")
		assertTrue(full.portals.first { it.row.id == "rift" }.live!!.locked, "live lock state attached")
	}

	@Test
	fun portalClickSendsExactlyItsCommand() {
		val b = bounds(4)
		val lay = FastTravelMap.layout(b.x, b.y, b.w, b.h, islands, measure)
		for (p in lay.portals) {
			val sent = mutableListOf<String>()
			val ok = FastTravelMap.click(p, CommandSink { sent += it })

			if (p.row.verified) { assertTrue(ok); assertEquals(listOf("/warp ${p.row.id}"), sent) }
			else { assertFalse(ok); assertTrue(sent.isEmpty(), p.row.id) }
		}
		assertEquals(setOf("wasteland", "dragontail", "scarleton"), FastTravelWarpTable.ROWS.filter { !it.verified }.map { it.id }.toSet())
		assertEquals("/warp hub", lay.portals.first { it.row.id == "hub" }.row.command)
	}

	@Test
	fun wrapNeverTruncates() {
		val t = "Other warps: Undiscovered Island (/warp sepulture), Jerry's Workshop (/warp winter) Supercalifragilisticexpialidocious"
		for (w in listOf(40, 90, 200)) {
			val lines = FastTravelMap.wrap(t, w, measure)
			assertTrue(lines.all { measure(it) <= w }, "fits $w")
			assertEquals(t.replace(" ", ""), lines.joinToString("").replace(" ", ""), "no text lost at $w")
		}
	}

	@Test
	fun missingTextureFallsBackAndLogsOnce() {
		FastTravelMap.Texture.resetForTests()
		val logs = mutableListOf<String>()
		assertFalse(FastTravelMap.Texture.available({ false }) { logs += it })
		assertFalse(FastTravelMap.Texture.available({ error("no resource manager") }) { logs += it })
		assertEquals(1, logs.size); assertTrue(logs[0].contains("fast_travel_map.png"))
		assertTrue(FastTravelMap.Texture.available({ true }) { logs += it })
		assertTrue(javaClass.getResource("/assets/apex/" + FastTravelMap.TEXTURE_PATH) != null, "image shipped")
	}
}
