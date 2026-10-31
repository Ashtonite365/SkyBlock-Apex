package dev.ashtonite365.apex.test.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.compress.ApexPack
import dev.ashtonite365.apex.compress.ApexPackEncoder
import dev.ashtonite365.apex.compress.Decompress
import dev.ashtonite365.apex.repo.BundleException
import dev.ashtonite365.apex.repo.BundleManifest
import dev.ashtonite365.apex.repo.RepoBundle

class RepoBundleAlternateTest {
	@TempDir
	lateinit var tempDir: Path

	private fun res(name: String): ByteArray = javaClass.getResourceAsStream("/apex-bundle/$name")!!.readAllBytes()
	private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

	private val zst = res("repo.json.zst")
	private val raw = Decompress.decode(zst)

	private val pack = ApexPackEncoder.compress(raw, 0, raw.size, ApexPackEncoder.Options.fast().also { it.checksum = false })

	private fun manifest(alternate: JsonObject?): BundleManifest {
		val o = JsonParser.parseString(String(res("manifest.json"))).asJsonObject
		if (alternate != null) o.add("alternates", JsonArray().apply { add(alternate) })
		return BundleManifest.parse(o.toString())
	}

	private fun alt(bytes: ByteArray, sha: String = sha(bytes), size: Long = bytes.size.toLong()) = JsonObject().apply {
		addProperty("bundle", "repo.json.apack"); addProperty("format", "apex-pack"); addProperty("format_version", 1)
		addProperty("bytes", size); addProperty("sha256", sha)
	}

	private class Server(val files: Map<String, ByteArray>) {
		val asked = ArrayList<String>()
		fun get(path: String, into: Path) { asked += path; into.writeBytes(files[path] ?: throw IOException("HTTP 404 for $path")) }
	}

	private fun target() = Files.createTempDirectory(tempDir, "t").resolve("repo")

	private fun assertTree(dir: Path) {
		assertEquals(4, Files.walk(dir).use { s -> s.filter { Files.isRegularFile(it) }.count() }.toInt())
		assertEquals("{\"a\":1}", dir.resolve("constants/misc.json").readText())
	}

	@Test
	fun packIsNoChecksumAndDecodes() {
		assertEquals(0, pack[5].toInt(), "flags byte: no checksum")
		assertTrue(ApexPack.decompress(pack).contentEquals(raw))
	}

	@Test
	fun manifestAlternateIsParsed() {
		val m = manifest(alt(pack))
		val a = m.apexPackAlternate!!
		assertEquals("repo.json.apack", a.bundle); assertEquals(1, a.formatVersion); assertEquals(pack.size.toLong(), a.bytes)
		assertNull(manifest(null).apexPackAlternate)
		assertNull(manifest(alt(pack).apply { addProperty("format_version", 2) }).apexPackAlternate, "unknown version ignored")
		assertNull(manifest(alt(pack).apply { addProperty("bundle", "../x.apack") }).apexPackAlternate, "unsafe path ignored")
	}

	@Test
	fun alternatePresentUsesApexPack() {
		val server = Server(mapOf("repo.json.apack" to pack, "repo.json.zst" to zst))
		val dir = target()
		val r = RepoBundle.install(manifest(alt(pack)), "repo.json.zst", dir, tempDir, server::get)
		assertEquals("apex-pack", r.format); assertNull(r.fallbackReason); assertEquals(4, r.files)
		assertEquals(listOf("repo.json.apack"), server.asked)
		assertTree(dir)
	}

	@Test
	fun alternateMissingUsesZst() {
		val server = Server(mapOf("repo.json.zst" to zst))
		val dir = target()
		val r = RepoBundle.install(manifest(null), "repo.json.zst", dir, tempDir, server::get)
		assertEquals("zst", r.format); assertNull(r.fallbackReason)
		assertEquals(listOf("repo.json.zst"), server.asked)
		assertTree(dir)

		val server2 = Server(mapOf("repo.json.zst" to zst))
		val r2 = RepoBundle.install(manifest(alt(pack)), "repo.json.zst", target(), tempDir, server2::get)
		assertEquals("zst", r2.format); assertTrue(r2.fallbackReason!!.contains("404"))
	}

	@Test
	fun alternateCorruptFallsBackToZst() {
		val flipped = pack.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x5A).toByte() }

		val truncated = pack.copyOf(pack.size - 7)
		val cases = listOf(
			alt(pack) to flipped,
			alt(truncated) to truncated,
			alt(pack, size = pack.size + 1L) to pack,
			alt(zst) to zst,
		)
		for ((i, c) in cases.withIndex()) {
			val (a, served) = c
			val server = Server(mapOf("repo.json.apack" to served, "repo.json.zst" to zst))
			val dir = target()
			val r = RepoBundle.install(manifest(a), "repo.json.zst", dir, tempDir, server::get)
			assertEquals("zst", r.format, "case $i"); assertNotNull(r.fallbackReason, "case $i")
			assertEquals(listOf("repo.json.apack", "repo.json.zst"), server.asked, "case $i")
			assertTree(dir)
		}
		assertTrue(tempDir.listDirectoryEntries("apex-repo*").isEmpty(), "temp files removed")
	}

	@Test
	fun badZstKeepsTheOldCache() {
		val dir = target()
		RepoBundle.install(manifest(null), "repo.json.zst", dir, tempDir, Server(mapOf("repo.json.zst" to zst))::get)
		val broken = zst.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
		assertThrows(BundleException::class.java) {
			RepoBundle.install(manifest(alt(pack, sha = "00")), "repo.json.zst", dir, tempDir, Server(mapOf("repo.json.zst" to broken))::get)
		}
		assertTree(dir)
	}
}
