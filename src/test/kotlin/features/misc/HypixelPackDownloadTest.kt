// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.features.misc.HypixelPackApi
import dev.ashtonite365.apex.features.misc.HypixelPackCache
import dev.ashtonite365.apex.features.misc.HypixelPackFetcher
import dev.ashtonite365.apex.features.misc.PackHash
import dev.ashtonite365.apex.features.misc.PackHashMismatch
import dev.ashtonite365.apex.features.misc.PackNoticePolicy
import dev.ashtonite365.apex.features.misc.RetryGate

class HypixelPackDownloadTest {
	@TempDir lateinit var tmp: Path

	private val kept = listOf(
		"pack.mcmeta",
		"assets/minecraft/font/default.json",
		"assets/hypixel_skyblock/textures/gui/sprites/tooltip/legendary_frame.png",
		"assets/minecraft/shaders/core/text.vsh",
	)
	private val dropped = listOf(
		"assets/minecraft/items/paper.json",
		"assets/hypixel_skyblock/textures/item/island_relevant/winter/weapons/ice_spray_wand.png",
		"assets/hypixel_skyblock/sounds.json",
	)

	private fun zip(entries: Map<String, String>): ByteArray {
		val bo = ByteArrayOutputStream()
		ZipOutputStream(bo).use { z -> for ((n, t) in entries) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
		return bo.toByteArray()
	}

	private fun pack(tag: String = "a"): ByteArray = zip(
		(kept + dropped).associateWith { if (it == "pack.mcmeta") """{"pack":{"pack_format":97,"description":"orig"}}""" else "$tag:$it" }
	)

	private fun api(sha1: String, url: String = "https://resourcepacks.hypixel.net/SkyBlock/test/97.zip") = """
		{"success":true,"packs":[
		 {"id":"Other","versions":[{"packFormat":97,"hash":"${"0".repeat(40)}","url":"https://resourcepacks.hypixel.net/x.zip"}]},
		 {"id":"SkyBlock","lastUpdated":1,"deployId":"d","versions":[
		   {"packFormat":96,"hash":"${"1".repeat(40)}","url":"https://resourcepacks.hypixel.net/SkyBlock/test/96.zip"},
		   {"packFormat":97,"hash":"$sha1","url":"$url"}]}]}
	""".trimIndent()

	private class Http(val api: () -> String, val pack: () -> ByteArray) : HypixelPackFetcher.Http {
		val calls = mutableListOf<String>()
		override fun get(url: String, maxBytes: Long): ByteArray {
			calls += url
			return if (url == HypixelPackApi.PACKS_URL) api().toByteArray() else pack()
		}
	}

	private class Notes : HypixelPackFetcher.Notifier {
		val ready = mutableListOf<Pair<Path, Boolean>>(); val unavailable = mutableListOf<String>()
		override fun ready(root: Path, downloaded: Boolean) { ready += root to downloaded }
		override fun unavailable(reason: String) { unavailable += reason }
	}

	private fun files(root: Path) = Files.walk(root).use { s -> s.filter { Files.isRegularFile(it) }.map { root.relativize(it).toString().replace('\\', '/') }.toList() }

	@Test
	fun parsePicksSkyBlockFormat97() {
		val sha = "51855dd566e020d36ade5727104de86c1ceb6a37"
		val v = HypixelPackApi.parse(api(sha.uppercase()))
		assertNotNull(v); v!!
		assertEquals(97, v.packFormat); assertEquals(sha, v.sha1)
		assertTrue(v.url.endsWith("/97.zip"))
		assertNull(HypixelPackApi.parse(api(sha), packFormat = 95))
		assertNull(HypixelPackApi.parse(api("nothex")), "bad hash rejected")
		assertNull(HypixelPackApi.parse(api(sha, "http://resourcepacks.hypixel.net/a.zip")), "http rejected")
		assertNull(HypixelPackApi.parse(api(sha, "https://evil.example/a.zip")), "foreign host rejected")
		assertNull(HypixelPackApi.parse("{\"success\":false}"))
		assertNull(HypixelPackApi.parse("not json"))
		assertEquals("https://api.hypixel.net/v2/resources/packs", HypixelPackApi.PACKS_URL)
	}

	@Test
	fun hashCheck() {
		val b = pack()

		assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", PackHash.sha1("abc".toByteArray()))
		val cache = HypixelPackCache(tmp)
		val e = assertThrows(PackHashMismatch::class.java) { cache.store(b, "f".repeat(40)) }
		assertEquals(PackHash.sha1(b), e.actual)
		assertFalse(Files.exists(cache.zip), "nothing cached on mismatch")
		assertNull(cache.verified())
		val c = cache.store(b, PackHash.sha1(b).uppercase())
		assertEquals(PackHash.sha1(b), c.sha1)
		assertNotNull(cache.verified())

		Files.write(cache.zip, byteArrayOf(1, 2, 3))
		assertNull(cache.verified())
	}

	@Test
	fun freshDownloadKeepsOnlyTheKeepList() {
		val b = pack(); val notes = Notes()
		val http = Http({ api(PackHash.sha1(b)) }, { b })
		val r = HypixelPackFetcher(HypixelPackCache(tmp), http, notes).run()
		assertTrue(r is HypixelPackFetcher.Result.Fresh, "$r")
		r as HypixelPackFetcher.Result.Fresh
		assertTrue(r.downloaded)
		assertEquals(listOf(HypixelPackApi.PACKS_URL, "https://resourcepacks.hypixel.net/SkyBlock/test/97.zip"), http.calls)
		val got = files(r.root).filter { it != ".apex-complete" }.sorted()
		assertTrue(got.isNotEmpty())
		assertEquals(kept.sorted(), got)
		val meta = Files.readString(r.root.resolve("pack.mcmeta"))
		assertTrue(meta.contains("Downloaded from Hypixel, not distributed by SkyBlock Apex"), meta)
		assertTrue(meta.contains("(C) Hypixel Inc."))
		assertEquals(1, notes.ready.size); assertEquals(0, notes.unavailable.size)

		val again = Http({ api(PackHash.sha1(b)) }, { error("must not download again") })
		val r2 = HypixelPackFetcher(HypixelPackCache(tmp), again, Notes()).run()
		assertEquals(HypixelPackFetcher.Result.Fresh(r.root, false), r2)
		assertEquals(1, again.calls.size)
	}

	@Test
	fun cacheFallbackWhenOffline() {
		val b = pack()
		HypixelPackFetcher(HypixelPackCache(tmp), Http({ api(PackHash.sha1(b)) }, { b }), Notes()).run()
		val notes = Notes()
		val offline = Http({ throw IOException("timeout") }, { throw IOException("timeout") })
		val r = HypixelPackFetcher(HypixelPackCache(tmp), offline, notes).run()
		assertTrue(r is HypixelPackFetcher.Result.FromCache, "$r")
		assertTrue((r as HypixelPackFetcher.Result.FromCache).reason.contains("timeout"))
		assertTrue(files(r.root).size > 1)
		assertEquals(1, notes.ready.size); assertFalse(notes.ready[0].second); assertEquals(0, notes.unavailable.size)
	}

	@Test
	fun cacheFallbackWhenNewDownloadFailsItsHash() {
		val b = pack("old")
		HypixelPackFetcher(HypixelPackCache(tmp), Http({ api(PackHash.sha1(b)) }, { b }), Notes()).run()
		val newer = pack("new"); val notes = Notes()

		val r = HypixelPackFetcher(HypixelPackCache(tmp), Http({ api("e".repeat(40)) }, { newer }), notes).run()
		assertTrue(r is HypixelPackFetcher.Result.FromCache, "$r")
		assertTrue((r as HypixelPackFetcher.Result.FromCache).reason.contains("SHA-1 mismatch"))
		assertEquals(PackHash.sha1(b), HypixelPackCache(tmp).verified()!!.sha1, "old verified cache kept")
		assertEquals(0, notes.unavailable.size)
	}

	@Test
	fun noCacheShowsNoticeAndContinues() {
		val notes = Notes()
		val r = HypixelPackFetcher(HypixelPackCache(tmp), Http({ throw IOException("offline") }, { error("unreachable") }), notes).run()
		assertTrue(r is HypixelPackFetcher.Result.Unavailable, "$r")
		assertEquals(1, notes.unavailable.size)
		assertTrue(notes.unavailable[0].contains("offline"))
		assertEquals(0, notes.ready.size)

		assertEquals(0, Files.list(tmp).use { it.count() })

		val b = pack(); val n2 = Notes()
		val r2 = HypixelPackFetcher(HypixelPackCache(tmp), Http({ api("d".repeat(40)) }, { b }), n2).run()
		assertTrue(r2 is HypixelPackFetcher.Result.Unavailable)
		assertEquals(1, n2.unavailable.size); assertFalse(Files.exists(HypixelPackCache(tmp).zip))
	}

	@Test
	fun zipSlipAndOddNamesAreSkipped() {
		for (bad in listOf("../x", "/abs", "a/../../b", "a\\b", "C:/x", "a//b", "./pack.mcmeta", "")) assertFalse(HypixelPackCache.safeEntry(bad), bad)
		assertTrue(HypixelPackCache.safeEntry("assets/minecraft/font/default.json"))
		val b = zip(mapOf("pack.mcmeta" to "{}", "assets/minecraft/font/default.json" to "{}", "../../evil.txt" to "x"))
		val cache = HypixelPackCache(tmp.resolve("c"))
		val root = cache.extract(cache.store(b, PackHash.sha1(b)), "d")
		assertFalse(Files.exists(tmp.resolve("evil.txt"))); assertFalse(Files.exists(tmp.resolve("c").resolve("evil.txt")))
		assertEquals(listOf(".apex-complete", "assets/minecraft/font/default.json", "pack.mcmeta"), files(root).sorted())
	}

	@Test
	fun packWithoutKeptFilesIsRejected() {
		val b = zip(mapOf("assets/minecraft/items/paper.json" to "{}"))
		val notes = Notes()
		val r = HypixelPackFetcher(HypixelPackCache(tmp), Http({ api(PackHash.sha1(b)) }, { b }), notes).run()
		assertTrue(r is HypixelPackFetcher.Result.Unavailable, "$r")
		assertEquals(1, notes.unavailable.size)
	}

	@Test
	fun retryGateHasNoTightRetries() {
		var now = 1_000L
		val g = RetryGate(30_000) { now }
		assertTrue(g.tryAcquire())
		assertFalse(g.tryAcquire())
		now += 29_999; assertFalse(g.tryAcquire())
		now += 1; assertTrue(g.tryAcquire())
		assertFalse(g.tryAcquire())
	}

	@Test
	fun noticeWording() {
		val n = HypixelPackFetcher.NOTICE
		for (part in listOf("(C) Hypixel Inc.", "under its own licence", "Downloaded from Hypixel", "not distributed by SkyBlock Apex")) assertTrue(n.contains(part), part)
	}

	@Test
	fun hashMismatchFallsBackSilently() {
		val policy = PackNoticePolicy()
		val b = pack("old")
		val first = HypixelPackFetcher(HypixelPackCache(tmp), Http({ api(PackHash.sha1(b)) }, { b }), Notes()).run()
		assertEquals(PackNoticePolicy.Show.NOTICE, policy.onResult(first), "first download: licence notice")

		val r = HypixelPackFetcher(HypixelPackCache(tmp), Http({ api("e".repeat(40)) }, { pack("new") }), Notes()).run()
		assertTrue(r is HypixelPackFetcher.Result.FromCache, "$r")
		assertEquals(PackNoticePolicy.Show.NONE, policy.onResult(r))

		repeat(3) {
			val o = HypixelPackFetcher(HypixelPackCache(tmp), Http({ throw IOException("offline") }, { error("x") }), Notes()).run()
			assertEquals(PackNoticePolicy.Show.NONE, policy.onResult(o))
		}

		val same = HypixelPackFetcher(HypixelPackCache(tmp), Http({ api(PackHash.sha1(b)) }, { error("no re-download") }), Notes()).run()
		assertEquals(PackNoticePolicy.Show.NONE, policy.onResult(same))
	}

	@Test
	fun noCacheToastOncePerSession() {
		val policy = PackNoticePolicy()
		val shown = (1..5).map {
			val r = HypixelPackFetcher(HypixelPackCache(tmp), Http({ throw IOException("offline") }, { error("x") }), Notes()).run()
			assertTrue(r is HypixelPackFetcher.Result.Unavailable, "$r")
			policy.onResult(r)
		}
		assertEquals(listOf(PackNoticePolicy.Show.UNAVAILABLE) + List(4) { PackNoticePolicy.Show.NONE }, shown)

		val bad = HypixelPackFetcher(HypixelPackCache(tmp), Http({ api("d".repeat(40)) }, { pack() }), Notes()).run()
		assertEquals(PackNoticePolicy.Show.NONE, policy.onResult(bad))

		assertEquals(PackNoticePolicy.Show.UNAVAILABLE, PackNoticePolicy().onResult(bad))
	}
}
