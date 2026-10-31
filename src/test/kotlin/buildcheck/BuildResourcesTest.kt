// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.buildcheck

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.wiki.WikiAttribution

class BuildResourcesTest {
	private val loader = javaClass.classLoader
	private val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))

	@Test
	fun noBundledHypixelPack() {
		assertNull(loader.getResource("resourcepacks/hypixel_skyblock/pack.mcmeta"))
		assertNull(loader.getResource("resourcepacks/hypixel_skyblock/LICENSE"))
		assertNull(loader.getResource("META-INF/apex/LICENSES/LicenseRef-Hypixel-SkyBlock-Pack.txt"))
		assertFalse(File(project, "src/main/resources/resourcepacks/hypixel_skyblock").exists())

		assertTrue(File(project, "LICENSES/LicenseRef-Hypixel-SkyBlock-Pack.txt").isFile)

		assertNotNull(loader.getResource("assets/apex/textures/gui/apex/fast_travel_map.png"))
		assertNull(loader.getResource("resourcepacks/transparent_overlay/pack.mcmeta"))
	}

	@Test
	fun licencesAndCreditsReachTheJar() {
		val names = listOf(
			"LICENSE-SkyblockAddons.txt", "LICENSE-minecraft-data.txt", "LICENSE-MixinExtras.txt", "LICENSE-HypixelModAPI.txt",
			"LICENSE-RepoParser-BSD-2-Clause.txt", "LICENSE-LibNinePatch-MPL-2.0.txt", "LICENSE-ManninghamMills-MPL-2.0.txt",
			"LICENSE-cloth-basic-math-Unlicense.txt", "ATTRIBUTION-CC-BY-4.0.txt", "GPL-3.0-or-later.txt",
		)
		for (n in names) assertNotNull(loader.getResource("META-INF/apex/LICENSES/$n"), n)
		val sba = loader.getResource("META-INF/apex/LICENSES/LICENSE-SkyblockAddons.txt")!!.readText()
		assertTrue(sba.contains("Copyright (c) 2021 Biscuit Development"))
		val ccby = loader.getResource("META-INF/apex/LICENSES/ATTRIBUTION-CC-BY-4.0.txt")!!.readText()
		for (who in listOf("june_hibiscus", "ic22487", "kathund", "Tendan")) assertTrue(ccby.contains(who), who)
		val notice = loader.getResource("META-INF/apex/NOTICE")!!.readText()
		assertTrue(notice.contains(S5A), "section 5(a) notice in NOTICE")
		assertTrue(notice.contains("CC BY-NC-SA 3.0"))
		assertFalse(notice.contains("LicenseRef-Hypixel"))
		assertTrue(notice.contains("api.hypixel.net/v2/resources/packs") && notice.contains("Not included in any SkyBlock Apex jar"))
		val credits = loader.getResource("META-INF/apex/credits.json")!!.readText()
		assertTrue(credits.contains(S5A))
		assertFalse(credits.contains("LicenseRef"), "credits.json")
		for (n in listOf("LibNinePatch", "Manningham Mills", "MixinExtras", "Hypixel Mod API", "NEU RepoParser", "PrismarineJS minecraft-data",
			"Noto Sans", "Hypixel SkyBlock Wiki", "Cloth BasicMath")) assertTrue(credits.contains("\"name\": \"$n"), n)
		val gpl = loader.getResource("META-INF/apex/LICENSE")!!.readText()
		assertTrue(gpl.trimStart().startsWith("GNU GENERAL PUBLIC LICENSE"), gpl.take(60))
	}

	@Test
	fun creditsMarkdownIsInSync() {
		val root = File(project, "CREDITS.md").readText()
		val jar = loader.getResource("META-INF/apex/CREDITS.md")!!.readText()
		assertTrue(root.length > 1000)
		assertEquals(root, jar, "src/main/resources/META-INF/apex/CREDITS.md must equal CREDITS.md")
		assertTrue(root.contains(S5A))
		assertFalse(root.contains("LicenseRef"))
		for (s in listOf("README.md", "NOTICE")) assertTrue(File(project, s).readText().contains(S5A), s)
	}

	@Test
	fun fabricModJsonAuthors() {
		val fmj = File(project, "src/main/resources/fabric.mod.json").readText()
		assertFalse(fmj.contains("nea@nea.moe"))
		assertTrue(fmj.contains("\"Ashtonite365\""))
		assertTrue(fmj.contains("Linnea Gräf"))
		assertTrue(fmj.contains("https://github.com/Ashtonite365/SkyBlock-Apex"))
	}

	@Test
	fun wikiAttribution() {
		val l = WikiAttribution.line("Aspect of the End")
		assertTrue(l.contains("Hypixel SkyBlock Wiki") && l.contains("CC BY-NC-SA 3.0") && l.contains("Aspect of the End"), l)
		assertEquals("https://creativecommons.org/licenses/by-nc-sa/3.0/", WikiAttribution.LICENCE_URL)
		assertFalse(WikiAttribution.line(null).contains("null"))
	}

	@Test
	fun odinLicencePathExists() {
		val json = loader.getResource("assets/apex/kit/kuudra/pearl-lineups.json")!!.readText()
		val m = Regex("assets/apex/[A-Za-z/]+LICENSE-Odin\\.txt").find(json)
		assertNotNull(m)
		assertNotNull(loader.getResource(m!!.value), m.value)
	}

	companion object {
		const val S5A = "SkyBlock Apex is a modified version of Firmament, modified by Ashton (Ashtonite365), 2026"
	}
}
