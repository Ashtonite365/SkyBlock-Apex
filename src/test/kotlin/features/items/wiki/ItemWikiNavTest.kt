// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.features.items.wiki.ItemWikiMigration
import dev.ashtonite365.apex.features.items.wiki.PanelRect
import dev.ashtonite365.apex.features.items.wiki.WikiApi
import dev.ashtonite365.apex.features.items.wiki.WikiCache
import dev.ashtonite365.apex.features.items.wiki.WikiHistory
import dev.ashtonite365.apex.features.items.wiki.WikiHosts
import dev.ashtonite365.apex.features.items.wiki.WikiLinks
import dev.ashtonite365.apex.features.items.wiki.WikiLoaderCore
import dev.ashtonite365.apex.features.items.wiki.WikiScroll
import dev.ashtonite365.apex.features.items.wiki.WikiTarget

class ItemWikiNavTest {

	@TempDir
	lateinit var tempDir: Path

	@Test fun scrollIsClamped() {
		val s = WikiScroll(1000, 200)
		assertEquals(800, s.max)
		s.scrollTo(-50); assertEquals(0, s.offset)
		s.scrollTo(5000); assertEquals(800, s.offset)
		s.home(); assertEquals(0, s.offset)
		s.pageDown(); assertEquals(188, s.offset)
		s.pageUp(); assertEquals(0, s.offset)
		s.wheel(-1.0, 10); assertEquals(30, s.offset, "wheel down = 3 lines")
		s.wheel(5.0, 10); assertEquals(0, s.offset)
		s.end(); assertEquals(800, s.offset)
		s.resize(500, 200); assertEquals(300, s.offset, "content shrank: clamp")
		s.resize(100, 200); assertEquals(0, s.offset); assertNull(s.thumb(200), "everything fits: no scrollbar")
	}

	@Test fun scrollbarThumbAndDrag() {
		val s = WikiScroll(1000, 200)
		assertEquals(0 to 40, s.thumb(200))
		s.end(); assertEquals(160 to 40, s.thumb(200))
		s.dragThumbTo(80, 200); assertEquals(400, s.offset)
		s.dragThumbTo(-30, 200); assertEquals(0, s.offset)
		s.dragThumbTo(999, 200); assertEquals(800, s.offset)
		assertEquals(12, WikiScroll(100000, 10).thumb(100)!!.second, "minimum thumb")
	}

	@Test fun overlayPanelFitsTheScreen() {
		for ((w, h) in listOf(320 to 240, 640 to 360, 1920 to 1080, 100 to 80)) {
			val r = PanelRect.centred(w, h)
			assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.w <= w && r.y + r.h <= h, "$r in ${w}x$h")
		}
	}

	@Test fun linksAreClassified() {
		assertEquals(WikiLinks.Kind.Internal("Necron's Handle"), WikiLinks.classify("/w/Necron%27s_Handle"))
		assertEquals(WikiLinks.Kind.Internal("Wither Impact", "Effect"), WikiLinks.classify("https://hypixelskyblock.minecraft.wiki/w/Wither_Impact#Effect"))
		assertEquals(WikiLinks.Kind.Internal("Rarity", "Rare"), WikiLinks.classify("/w/Rarity#Rare"))
		assertEquals(WikiLinks.Kind.Internal("Hyperion"), WikiLinks.classify("./Hyperion"))
		assertEquals(WikiLinks.Kind.Anchor("Obtaining"), WikiLinks.classify("#Obtaining"))
		assertEquals(WikiLinks.Kind.External("https://www.youtube.com/watch?v=x"), WikiLinks.classify("https://www.youtube.com/watch?v=x"))
		assertEquals(WikiLinks.Kind.External("https://minecraft.wiki/w/Stone"), WikiLinks.classify("https://minecraft.wiki/w/Stone"))
		assertEquals(WikiLinks.Kind.External(null), WikiLinks.classify("/index.php?title=Nope&action=edit&redlink=1"))
		assertEquals(WikiLinks.Kind.External(null), WikiLinks.classify("/w/File:Hyperion.png"))
		assertEquals(WikiLinks.Kind.External(null), WikiLinks.classify(null))
	}

	@Test fun hostAndUrls() {
		assertEquals(setOf("hypixelskyblock.minecraft.wiki"), WikiHosts.HOSTS)
		val api = WikiHosts.apiUrl("Necron's Handle")
		assertTrue(api.startsWith("https://hypixelskyblock.minecraft.wiki/api.php?action=parse"))
		assertTrue("prop=text%7Csections" in api && "redirects=1" in api)
		assertTrue(WikiHosts.allowed(api)); assertFalse(WikiHosts.allowed("https://evil.example/api.php"))
		assertEquals("https://hypixelskyblock.minecraft.wiki/w/Trophy_Fish#Obfuscated_1", WikiHosts.pageUrl("Trophy Fish", "Obfuscated 1"))
		assertTrue(WikiHosts.userAgent("1.0.0").startsWith("SkyBlockApex/1.0.0"))
	}

	@Test fun backHistoryKeepsScroll() {
		val h = WikiHistory()
		h.open("Aspect of the End"); assertFalse(h.canGoBack)
		h.current!!.scroll = 140
		h.open("Ender Pearl"); h.open("Ender Pearl")
		assertEquals(1, h.depth, "same page again is not a new step")
		h.open("Ender Pearl", "VIII"); assertEquals(2, h.depth, "an anchor is")
		h.retitle("Ender Pearl (redirect)"); assertEquals("Ender Pearl (redirect)", h.current!!.title); assertEquals(2, h.depth)
		h.goBack()
		val back = h.goBack()!!
		assertEquals("Aspect of the End", back.title); assertEquals(140, back.scroll)
		assertNull(h.goBack()); assertEquals("Aspect of the End", h.current!!.title)
	}

	@Test fun apiResponsesAndErrors() {
		val ok = WikiApi.fromResponse(200, "{\"parse\":{\"title\":\"Hyperion\",\"text\":\"<p>Hi</p>\",\"sections\":[{\"toclevel\":1,\"level\":\"2\",\"line\":\"Obtaining\",\"anchor\":\"Obtaining\"}]}}", "hyperion")
		assertTrue(ok is WikiApi.Outcome.Page); ok as WikiApi.Outcome.Page
		assertEquals("Hyperion", ok.title); assertEquals("<p>Hi</p>", ok.html); assertEquals("Obtaining", ok.sections.single().anchor)
		assertEquals("<p>v1</p>", (WikiApi.fromResponse(200, "{\"parse\":{\"title\":\"A\",\"text\":{\"*\":\"<p>v1</p>\"}}}", "A") as WikiApi.Outcome.Page).html)
		assertTrue(WikiApi.fromResponse(200, "{\"error\":{\"code\":\"missingtitle\"}}", "Nope") is WikiApi.Outcome.Missing)
		assertTrue(WikiApi.fromResponse(404, "", "Nope") is WikiApi.Outcome.Missing)
		val e502 = WikiApi.fromResponse(502, "<html>502 Bad Gateway</html>", "Hyperion") as WikiApi.Outcome.Error
		assertTrue("502" in e502.message && "maintenance" in e502.message)
		assertTrue(WikiApi.errorFor(null, java.net.http.HttpTimeoutException("t"), "X").message.contains("did not respond"))
		assertTrue(WikiApi.errorFor(null, java.net.ConnectException("c"), "X").message.contains("offline"))
		assertTrue(WikiApi.fromResponse(200, "not json", "X") is WikiApi.Outcome.Error)
		assertEquals(listOf("Hyperion", "Hyperion (Item)"), WikiApi.searchTitles("[\"hyp\",[\"Hyperion\",\"Hyperion (Item)\"],[],[]]"))
		assertEquals(emptyList<String>(), WikiApi.searchTitles("garbage"))
		val secs = listOf(WikiApi.Section(2, "Etherwarp Upgrade", "Etherwarp_Upgrade"))
		assertEquals("Etherwarp_Upgrade", WikiApi.sectionAnchor(secs, "Etherwarp Upgrade"))
		assertNull(WikiApi.sectionAnchor(secs, "Tier I"), "only real anchors")
	}

	private fun pageOf(title: String, html: String = "<p>$title</p>") = WikiApi.Outcome.Page(title, html, listOf(WikiApi.Section(2, "Obtaining", "Obtaining")))

	@Test fun cacheIsSharedByRequestAndRealTitleAndExpires() {
		val dir = Files.createTempDirectory(tempDir, "apex-wiki-cache")
		var now = 1_000L
		val c = WikiCache(dir, ttlMs = 10_000, clock = { now })
		c.put("Tater Helmet", pageOf("Tater Armor"))
		assertEquals("Tater Armor", c.get("tater_helmet")!!.title)
		assertEquals("Tater Armor", c.get("Tater Armor")!!.title)
		val disk = WikiCache(dir, ttlMs = 10_000, clock = { now })
		val e = disk.get("Tater Armor")!!
		assertEquals("<p>Tater Armor</p>", e.html); assertEquals("Obtaining", e.sections.single().anchor)
		now += 20_000
		assertNull(disk.get("Tater Armor"), "expired")
		assertNotNull(disk.get("Tater Armor", allowStale = true), "kept for offline use")
		assertTrue(dir.toFile().listFiles()!!.none { it.name.endsWith(".tmp") })
		dir.toFile().deleteRecursively()
	}

	@Test fun loaderTriesAlternatesThenSearchThenFails() {
		val fetched = mutableListOf<String>()
		val pages = mapOf("Pet Skins" to pageOf("Pet Skins"), "Mob" to pageOf("Mob"))
		val l = WikiLoaderCore(WikiCache(null), { t -> fetched += t; pages[t] ?: WikiApi.Outcome.Missing(t) }, { q -> if (q == "Zombie") listOf("Mob") else emptyList() })
		val skin = l.load(WikiTarget("IvoryBlack Cat", alternates = listOf("Ivory Black Cat Skin", "Pet Skins"), rule = "repo")) as WikiLoaderCore.Result.Ok
		assertEquals("Pet Skins", skin.loaded.page.title)
		assertEquals(listOf("IvoryBlack Cat", "Ivory Black Cat Skin", "Pet Skins"), fetched)
		val viaSearch = l.load(WikiTarget("Zombie (Monster)", search = "Zombie", rule = "name")) as WikiLoaderCore.Result.Ok
		assertEquals("Mob", viaSearch.loaded.page.title)
		val none = l.load(WikiTarget("Syphoner", rule = "name")) as WikiLoaderCore.Result.Fail
		assertTrue("no wiki page" in none.message)
		fetched.clear(); l.load(WikiTarget("Pet Skins", rule = "x")); assertEquals(emptyList<String>(), fetched, "served from the cache")
	}

	@Test fun loaderKeepsOnlyRealAnchorsAndServesStaleOffline() {
		var now = 0L
		val cache = WikiCache(null, ttlMs = 100, clock = { now })
		var online = true
		val l = WikiLoaderCore(cache, { t -> if (online) pageOf(t) else WikiApi.errorFor(null, java.net.ConnectException(), t) }, { emptyList() })
		val a = l.load(WikiTarget("Trophy Fish", "Obtaining", rule = "x")) as WikiLoaderCore.Result.Ok
		assertEquals("Obtaining", a.loaded.anchor)
		val b = l.load(WikiTarget("Perfect Armor", "Tier I", rule = "x")) as WikiLoaderCore.Result.Ok
		assertNull(b.loaded.anchor, "missing section -> top of the page")
		online = false; now = 1_000
		val stale = l.load(WikiTarget("Trophy Fish", rule = "x")) as WikiLoaderCore.Result.Ok
		assertTrue(stale.loaded.stale)
		val fail = l.load(WikiTarget("Never Seen", rule = "x")) as WikiLoaderCore.Result.Fail
		assertTrue("offline" in fail.message)
	}

	private fun migrate(json: String) = JsonParser.parseString(json).asJsonObject.also { ItemWikiMigration.migrate(it) }

	@Test fun migrationMovesTheKeyAndHoldTime() {
		val d = migrate("{\"auto-completions\":{\"warp-complete\":true,\"wiki-key\":{\"button\":{\"keyCode\":75}},\"wiki-hold-tenths\":20,\"wiki-viewer-x\":5,\"wiki-viewer-w\":300}}")
		assertEquals(JsonParser.parseString("{\"warp-complete\":true}"), d.get("auto-completions"))
		assertEquals(75, d.getAsJsonObject("item-wiki").getAsJsonObject("key").getAsJsonObject("button").get("keyCode").asInt)
		assertEquals(20, d.getAsJsonObject("item-wiki").get("hold-tenths").asInt)
		val dflt = migrate("{\"auto-completions\":{\"wiki-hold-tenths\":10}}")
		assertFalse(dflt.has("item-wiki"), "the old default hold becomes the new default (1.5 s)")
		val kept = migrate("{\"auto-completions\":{\"wiki-key\":{\"button\":{\"keyCode\":75}}},\"item-wiki\":{\"key\":{\"button\":{\"keyCode\":74}}}}")
		assertEquals(74, kept.getAsJsonObject("item-wiki").getAsJsonObject("key").getAsJsonObject("button").get("keyCode").asInt)
		assertEquals(JsonParser.parseString("{}"), migrate("{}"))
	}

	private fun dir(p: String) = listOf(p, "../$p").map(::File).firstOrNull { it.exists() }

	@Test fun wikiFeatureIsSilent() {
		val d = dir("src/main/kotlin/features/items/wiki") ?: return
		val sound = Regex("playSound|SoundEvents|SoundInstance|playNotifySound|SimpleSoundInstance|\\.ding\\(")
		d.listFiles()!!.filter { it.extension == "kt" }.forEach { f -> assertFalse(sound.containsMatchIn(f.readText()), "${f.name} must not play sounds") }
	}

	@Test fun neutralNamesOnly() {
		val d = dir("src/main/kotlin/features/items/wiki") ?: return
		val banned = Regex("ponder|\\bcreate\\b", RegexOption.IGNORE_CASE)
		d.listFiles()!!.filter { it.extension == "kt" }.forEach { f -> assertFalse(banned.containsMatchIn(f.readText()), "${f.name}: neutral names only") }
		val lang = dir("translations/en_us.json") ?: return
		val keys = JsonParser.parseString(lang.readText()).asJsonObject.entrySet().filter { "wiki" in it.key }
		assertTrue(keys.any { it.key == "apex.config.item-wiki.key" })
		keys.forEach { assertFalse(banned.containsMatchIn(it.key + " " + it.value.asString), it.key) }
	}
}
