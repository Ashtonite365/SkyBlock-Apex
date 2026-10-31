// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.compress.ApexPack
import dev.ashtonite365.apex.compress.Decompress
import dev.ashtonite365.apex.repo.BundleManifest
import dev.ashtonite365.apex.repo.RepoBundle

class RepoBundleRealFixtureTest {
	@TempDir
	lateinit var tempDir: Path

	private fun res(name: String): ByteArray = javaClass.getResourceAsStream("/apex-bundle-1036/$name")!!.readAllBytes()
	private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
	private val manifestText = String(res("manifest.json"))
	private val manifest = BundleManifest.parse(manifestText)

	private fun serve(path: String, into: Path) { Files.write(into, res(path)) }

	private fun treeDigest(dir: Path): Pair<Int, String> {
		val md = MessageDigest.getInstance("SHA-256")
		val files = Files.walk(dir).use { s -> s.filter { Files.isRegularFile(it) }.map { dir.relativize(it).toString().replace('\\', '/') }.sorted().toList() }
		for (f in files) { md.update(f.toByteArray()); md.update(0); md.update(Files.readAllBytes(dir.resolve(f))); md.update(0) }
		return files.size to md.digest().joinToString("") { "%02x".format(it) }
	}

	@Test
	fun packAndZstDecodeToTheSameBytes() {
		val m = JsonParser.parseString(manifestText).asJsonObject
		val pack = res("repo.json.apack")
		val zst = res("repo.json.zst")
		val alt = manifest.apexPackAlternate!!
		assertEquals(alt.sha256, sha(pack)); assertEquals(alt.bytes, pack.size.toLong())
		assertEquals(m["sha256"].asString, sha(zst))
		assertTrue(ApexPack.isApexPack(pack, 0, pack.size))
		assertEquals(0, pack[5].toInt(), "flags byte: the repo writes no-checksum frames")
		val fromPack = ApexPack.decompress(pack)
		val fromZst = Decompress.decode(zst)
		assertEquals(m["uncompressed_bytes"].asLong, fromPack.size.toLong())
		assertTrue(fromPack.size > 4 * 1024 * 1024, "more than one 4 MiB block")
		assertEquals(sha(fromZst), sha(fromPack))
	}

	@Test
	fun installFromAlternateMatchesInstallFromZst() {
		val viaPack = tempDir.resolve("pack/repo")
		val r1 = RepoBundle.install(manifest, "repo.json.zst", viaPack, tempDir, ::serve)
		assertEquals("apex-pack", r1.format); assertNull(r1.fallbackReason)
		val noAlt = JsonParser.parseString(manifestText).asJsonObject.apply { remove("alternates") }
		val viaZst = tempDir.resolve("zst/repo")
		val r2 = RepoBundle.install(BundleManifest.parse(noAlt.toString()), "repo.json.zst", viaZst, tempDir, ::serve)
		assertEquals("zst", r2.format)
		val fileCount = JsonParser.parseString(manifestText).asJsonObject["file_count"].asInt
		assertEquals(fileCount, r1.files); assertEquals(fileCount, r2.files)
		assertEquals(treeDigest(viaZst), treeDigest(viaPack))
	}
}
