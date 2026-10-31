package dev.ashtonite365.apex.test.features.menus.garden

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.garden.GardenMenus
import dev.ashtonite365.apex.features.menus.garden.GardenDeskMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.support.EnchantLabel
import dev.ashtonite365.apex.features.menus.garden.GardenPageParser
import dev.ashtonite365.apex.features.menus.garden.GardenPageSpec
import dev.ashtonite365.apex.features.menus.garden.GardenPageSpecs
import dev.ashtonite365.apex.features.menus.garden.GardenPlotsParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class GardenDeskMenusTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	private fun page(spec: GardenPageSpec, fixture: String) = RealDumpFixtures.load(fixture).let { GardenPageParser.parse(spec, it.title, it.slots) }

	@Test
	fun deskMirrorsEveryButton() {
		val m = page(GardenPageSpecs.DESK, "garden-desk")!!
		assertEquals(listOf(4 to "Garden Level VIII", 19 to "Configure Plots", 21 to "Garden Upgrades", 23 to "SkyMart", 25 to "Garden Milestones", 31 to "Garden Skins"),
			m.tiles.map { it.slot to it.name })
		assertEquals(listOf(50 to "Garden Time", 51 to "Farming Toolkit"), m.controls.map { it.slot to it.name })
		assertEquals(listOf(49 to "Close"), m.nav.map { it.slot to it.name })

		val d = RealDumpFixtures.load("garden-desk")
		assertNull(GardenPageParser.parse(GardenPageSpecs.DESK, d.title, d.slots.filter { it.slot != 19 }))
		assertNull(GardenPageParser.parse(GardenPageSpecs.DESK, "Garden Upgrades", d.slots))
	}

	@Test
	fun fullLoreWrappedNeverTruncated() {
		val m = page(GardenPageSpecs.CROP_MILESTONES, "garden-crop-milestones")!!
		val wheat = m.tiles.first { it.name.startsWith("Wheat") }
		val body = GardenPageParser.body(wheat, 60) { it.length * 6 }
		assertEquals(wheat.lore.filter { it.isNotBlank() }.joinToString(" ").split(Regex("\\s+")).filter { it.isNotEmpty() },
			body.joinToString(" ").split(Regex("\\s+")).filter { it.isNotEmpty() })
		assertEquals(13, m.tiles.size)
		assertEquals(listOf("Go Back", "Close"), m.nav.map { it.name })
	}

	@Test
	fun otherPages() {
		assertEquals(13, page(GardenPageSpecs.CROP_UPGRADES, "garden-crop-upgrades")!!.tiles.size)
		assertEquals(listOf("Crop Milestones", "Visitor Milestones"), page(GardenPageSpecs.GARDEN_MILESTONES, "garden-milestones")!!.tiles.map { it.name })
		val vm = page(GardenPageSpecs.VISITOR_MILESTONES, "garden-visitor-milestones")!!
		assertEquals(listOf("Unique Visitors Served", "Offers Accepted"), vm.tiles.map { it.name })
		assertEquals(2, GardenPageSpecs.VISITOR_MILESTONES.columns)
		assertEquals(listOf("Visitor's Logbook"), vm.controls.map { it.name })
		val lb = page(GardenPageSpecs.VISITOR_LOGBOOK, "garden-visitor-logbook-1")!!
		assertTrue(lb.tiles.any { it.name == "Trevor" && it.lore.contains("Times Visited: 3") })
		assertEquals(listOf("Unlocked", "Sort", "Rarity", "Visited", "Offers Accepted"), lb.controls.map { it.name })
		assertEquals(listOf("Next Page", "Close"), lb.nav.map { it.name })
		assertEquals(listOf("Farming Essentials", "Farming Tools", "Barn Skins", "Greenhouse Skins", "Pests"), page(GardenPageSpecs.SKYMART, "garden-skymart")!!.tiles.map { it.name })
		assertEquals(listOf(11 to "Barn Skins", 15 to "Greenhouse Skins"), page(GardenPageSpecs.GARDEN_SKINS, "garden-skins")!!.tiles.map { it.slot to it.name })
		val tk = page(GardenPageSpecs.FARMING_TOOLKIT, "garden-farming-toolkit")!!
		assertEquals(12, tk.tiles.size); assertTrue(GardenPageSpecs.FARMING_TOOLKIT.showInventory)
	}

	@Test
	fun plotsGridCostAndPreset() {
		val d = RealDumpFixtures.load("garden-plots")
		val m = GardenPlotsParser.parse(d.title, d.slots)!!
		assertEquals(25, m.cells.size)
		fun c(n: String) = m.cells.first { it.name == n }
		assertEquals(listOf("Compost Bundle x8"), c("Plot - 21").cost)
		assertEquals(listOf("✔ Garden Level 7"), c("Plot - 21").requirements)
		assertEquals(0 to 0, c("Plot - 21").row to c("Plot - 21").col)
		assertEquals(listOf("Compost x32"), c("Plot - 5").cost)
		assertEquals("Melon", c("Plot - 4").preset)
		assertEquals("Cactus", c("Plot - 8").preset)
		assertNull(c("Plot - 2").preset)
		assertTrue(GardenPlotsParser.lines(c("Plot - 2")).any { it.contains("unknown") })
		assertTrue(!c("The Barn").locked)
		assertEquals(2 to 2, c("The Barn").row to c("The Barn").col)
		assertEquals(listOf("Go Back", "Close"), m.nav.map { it.name })

		assertNull(GardenPlotsParser.parse(d.title, d.slots + SlotText(45, "Mystery Button", listOf("x"))))
		assertNull(GardenPlotsParser.parse("Desk", d.slots))
	}

	@Test
	fun titlesRouteToTheirMenus() {
		MenuRegistry.clearForTests(); GardenMenus.ALL.forEach(MenuRegistry::register)
		assertEquals("garden-desk", MenuRegistry.match("Desk")?.id)
		assertEquals("garden-plots", MenuRegistry.match("Configure Plots")?.id)
		assertEquals("garden-visitor-logbook", MenuRegistry.match("(3/5) Visitor's Logbook")?.id)
		assertEquals("garden-skymart", MenuRegistry.match("SkyMart")?.id)
		assertNull(MenuRegistry.match("SkyMart Barn Skins")?.takeIf { it.id.startsWith("garden-") })
		assertNull(MenuRegistry.match("Greenhouse Upgrades")?.takeIf { it.id.startsWith("garden-") })
		assertNotNull(MenuRegistry.match("Farming Toolkit"))
	}

	private val measure: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }

	private val screens = listOf(960 to 540, 640 to 360, 480 to 270, 320 to 240)

	private fun fitPage(spec: GardenPageSpec, fixture: String, menu: dev.ashtonite365.apex.features.menus.CustomMenu) {
		val m = page(spec, fixture)!!
		val all = m.tiles + m.controls
		val tiles = all.map { dev.ashtonite365.apex.features.menus.garden.FitTile(it.name, it.lore) }
		for ((sw, sh) in screens) {
			val b = MenuBounds.centred(sw, sh, menu.defaultSize.first, menu.defaultSize.second)
			val viewH = b.h - 40 - (if (spec.showInventory) dev.ashtonite365.apex.features.menus.support.InventoryPanel.HEIGHT + 6 else 0)
			val fixed = if (spec == GardenPageSpecs.VISITOR_MILESTONES) 2 else null
			val p = dev.ashtonite365.apex.features.menus.garden.GardenFit.plan(tiles, b.w - 8, viewH, measure, fixed, prefer = spec.columns)
			println("FIT ${spec.id} ${sw}x$sh menu=${b.w}x${b.h} -> ${p?.let { "cols=${it.cols} tile=${it.tileW}x${it.rowHeights.maxOrNull()} scale=${it.scale} body=${it.showBody} h=${it.height}/$viewH" }}")
			assertNotNull(p, "${spec.id} ${sw}x$sh")
			assertTrue(p!!.height <= viewH && p.cols * p.tileW <= b.w - 8)
		}
	}

	@Test fun deskFits() = fitPage(GardenPageSpecs.DESK, "garden-desk", GardenDeskMenu)
	@Test fun cropUpgradesFits() = fitPage(GardenPageSpecs.CROP_UPGRADES, "garden-crop-upgrades", dev.ashtonite365.apex.features.menus.garden.GardenCropUpgradesMenu)
	@Test fun gardenMilestonesFits() = fitPage(GardenPageSpecs.GARDEN_MILESTONES, "garden-milestones", dev.ashtonite365.apex.features.menus.garden.GardenMilestonesMenu)
	@Test fun cropMilestonesFits() = fitPage(GardenPageSpecs.CROP_MILESTONES, "garden-crop-milestones", dev.ashtonite365.apex.features.menus.garden.GardenCropMilestonesMenu)
	@Test fun visitorMilestonesFits() = fitPage(GardenPageSpecs.VISITOR_MILESTONES, "garden-visitor-milestones", dev.ashtonite365.apex.features.menus.garden.GardenVisitorMilestonesMenu)
	@Test fun skyMartFits() = fitPage(GardenPageSpecs.SKYMART, "garden-skymart", dev.ashtonite365.apex.features.menus.garden.GardenSkyMartMenu)
	@Test fun gardenSkinsFits() = fitPage(GardenPageSpecs.GARDEN_SKINS, "garden-skins", dev.ashtonite365.apex.features.menus.garden.GardenSkinsMenu)
	@Test fun toolkitFits() = fitPage(GardenPageSpecs.FARMING_TOOLKIT, "garden-farming-toolkit", dev.ashtonite365.apex.features.menus.garden.GardenFarmingToolkitMenu)

	@Test
	fun plotsFit() {
		val d = RealDumpFixtures.load("garden-plots")
		val m = GardenPlotsParser.parse(d.title, d.slots)!!
		val grid = (0 until 25).map { i -> m.cells.firstOrNull { it.row == i / 5 && it.col == i % 5 }?.let { dev.ashtonite365.apex.features.menus.garden.FitTile(it.name, GardenPlotsParser.lines(it)) } }
		val menu = dev.ashtonite365.apex.features.menus.garden.GardenPlotsMenu
		for ((sw, sh) in screens) {
			val b = MenuBounds.centred(sw, sh, menu.defaultSize.first, menu.defaultSize.second)
			val p = menu.plan(grid, b.w - 6, b.h - 40, measure)
			println("FIT garden-plots ${sw}x$sh menu=${b.w}x${b.h} -> tile=${p.tileW}x${p.rowHeights.maxOrNull()} scale=${p.scale} icon=${p.iconW} h=${p.height}/${b.h - 40}")

			val icon = dev.ashtonite365.apex.features.menus.garden.GardenFit.iconSize(p.iconW)
			assertTrue(p.iconW > 0 && icon >= 8, "icon drawn ${sw}x$sh: iconW=${p.iconW}")
			assertTrue(p.rowHeights.all { it == 0 || it >= icon + 1 }, "rows fit the icon ${sw}x$sh")
			assertTrue(p.textW > 0 && p.cols * p.tileW + (p.cols - 1) * p.gap <= b.w - 6)
			if (sw >= 480) assertTrue(p.height <= b.h - 40, "one page ${sw}x$sh")
		}
	}

	@Test
	fun wrapNeverTruncates() {
		val t = dev.ashtonite365.apex.features.menus.garden.FitTile("Plot - 21", listOf("Compost Bundle x8", "You need to unlock an adjacent plot"))
		val l = dev.ashtonite365.apex.features.menus.garden.GardenFit.lines(t, 30, true, measure)
		assertEquals("Plot - 21 Compost Bundle x8 You need to unlock an adjacent plot".split(" "), l.joinToString(" ").split(" ").filter { it.isNotEmpty() })
	}

	@Test
	fun cropMilestonesCardsFitAtGuiScale3() {
		val m = page(GardenPageSpecs.CROP_MILESTONES, "garden-crop-milestones")!!
		val tiles = m.tiles.map { dev.ashtonite365.apex.features.menus.garden.GardenTile(it.slot, it.name, it.lore) }
		val controls = m.controls.map { dev.ashtonite365.apex.features.menus.garden.GardenTile(it.slot, it.name, it.lore) }
		val cells = dev.ashtonite365.apex.features.menus.garden.CropCards.cells(tiles, controls)
		val names = cells.map { it?.name }
		assertEquals(listOf("Wheat", "Carrot", "Potato", "Pumpkin", "Sugar Cane", "Melon Slice", "Cactus"), names.take(7).map { n -> n!!.replace(Regex(" [IVXLC]+$"), "") })
		assertEquals(13, tiles.size)
		assertEquals(null, names[13], "row 2 holds the last 6 crops")
		val melon = dev.ashtonite365.apex.features.menus.garden.CropCards.body(tiles.first { it.name.startsWith("Melon Slice") }.lore)
		assertTrue(melon.any { it.startsWith("Total:") } && melon.any { it.contains("Progress to Tier") } && melon.any { it.contains("Garden XP") }, "$melon")
		val fit = cells.map { t -> t?.let { dev.ashtonite365.apex.features.menus.garden.FitTile(it.name, dev.ashtonite365.apex.features.menus.garden.CropCards.body(it.lore)) } }
		val menu = dev.ashtonite365.apex.features.menus.garden.GardenCropMilestonesMenu
		val (sw, sh) = 640 to 360
		val b = MenuBounds.centred(sw, sh, menu.defaultSize.first, menu.defaultSize.second)
		val viewH = b.h - 40
		val p = dev.ashtonite365.apex.features.menus.garden.GardenFit.plan(fit, b.w - 8, viewH, measure, 7, allowHideBody = false)
		println("FIT crop-cards ${sw}x$sh menu=${b.w}x${b.h} -> ${p?.let { "tile=${it.tileW} scale=${it.scale} rows=${it.rowHeights} h=${it.height}/$viewH" }}")
		assertNotNull(p, "fits one screen at GUI scale 3 with Back/Close")
		assertEquals(7, p!!.cols); assertTrue(p.showBody, "every value shown"); assertEquals(2 + (if (controls.isEmpty()) 0 else 1), p.rowHeights.size)
		assertTrue(p.height <= viewH && p.cols * p.tileW <= b.w - 8)

		fit.filterNotNull().forEach { t ->
			val drawn = dev.ashtonite365.apex.features.menus.garden.GardenFit.lines(t, p.textW, true, measure).joinToString(" ").replace(Regex("§."), "")
			(listOf(t.name) + t.body).joinToString(" ").split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { w -> assertTrue(drawn.contains(w.replace(Regex("§."), "")), "$w in ${t.name}") }
		}
	}
}
