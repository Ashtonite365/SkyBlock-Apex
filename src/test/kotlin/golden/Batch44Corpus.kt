// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

object Batch44Repo {
	val r = Random(4444)
	val uris = listOf(
		"https://github.com/x", "HTTPS://GitHub.com/x", "http://github.com/x", "https://codeload.github.com/a.zip", "https://objects.githubusercontent.com/q?x=1",
		"https://evil.com/", "https://github.com.evil.com/", "https://user@github.com/", "https://user:pw@github.com/", "https://github.com:443/a",
		"https://github.com:8443/a", "https://github.com:0/a", "ftp://github.com/", "/relative/path", "mailto:a@github.com", "https:///nohost",
		"https://[::1]/", "https://GITHUB.COM./", "https://raw.githubusercontent.com/x", "file:///etc/passwd", "https://github.com", "//github.com/x",
	)
	private val segs = listOf("a", "", ".", "..", "constants", "x.json", "b", "...", "c:d", "e\\f", "\u0000", " ", "ä")
	val names: List<String> = listOf("", "/", "//", "/a", "a/", "./a", "a/./b", "a//b", "a/../b", "..", "...", "C:/x", "a\\b", "a\u0000b", "SkyHanni-REPO-main/constants/a.json") +
		List(1200) { val n = r.nextInt(1, 6); val s = (0 until n).joinToString("/") { segs[r.nextInt(segs.size)] }; if (r.nextInt(5) == 0) "/$s" else s }

	data class Entry(val name: String, val dir: Boolean, val size: Int)
	private val entryNames = listOf("Repo-main/constants/a.json", "Repo-main/constants/rift/b.json", "Repo-main/constants/a.txt", "Repo-main/other/c.json",
		"Repo-main/constants/", "constants/top.json", "Repo-main/./constants/d.json", "Repo-main//constants/e.json", "Repo-main/../evil.json",
		"/abs/constants/x.json", "Repo-main/constants/a.json", "R/constants/sub/deep/f.JSON", "R/constants/g.json", "R/Constants/h.json", "R/constants/.json", "x\\y.json")
	val zips: List<List<Entry>> = List(400) {
		List(r.nextInt(0, 7)) { val nm = entryNames[r.nextInt(entryNames.size)]; Entry(nm, nm.endsWith("/") || r.nextInt(12) == 0, listOf(0, 1, 100, 8192, 8193, 20000)[r.nextInt(6)]) }
	}
	val caps = listOf(0L, 1L, 100L, 8192L, 8193L, 20000L, 40000L, Long.MAX_VALUE)

	fun zipBytes(entries: List<Entry>, seed: Int): ByteArray {
		val rr = Random(seed)
		val bo = ByteArrayOutputStream()
		ZipOutputStream(bo).use { z ->
			val seen = HashSet<String>()
			for (e in entries) {
				val name = if (e.dir && !e.name.endsWith("/")) e.name + "/" else e.name
				if (!seen.add(name)) continue
				z.putNextEntry(ZipEntry(name))
				if (!e.dir) z.write(ByteArray(e.size) { rr.nextInt(32, 127).toByte() })
				z.closeEntry()
			}
		}
		return bo.toByteArray()
	}
	val streamSizes = listOf(0, 1, 16383, 16384, 16385, 50000, 1024 * 1024 + 1)
	val readSteps = listOf(1, 7, 4096, 16384, 100000)
	val texts = listOf("constants/Skulls.json", "constants/island_graphs/THE_RIFT.json", "./constants/Skulls.json", "constants//TrophyFish.json", "constants/Garden.json", "../x", "", "/constants/Garden.json", "constants//Garden.json", "constants/nope.json")
}
