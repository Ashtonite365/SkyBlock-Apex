package dev.ashtonite365.apex.test.repo

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.repo.BundleException
import dev.ashtonite365.apex.repo.BundleManifest
import dev.ashtonite365.apex.repo.RepoBundle
import dev.ashtonite365.apex.repo.RepoDownloadManager

class RepoBundleTest {

	@TempDir
	lateinit var tempDir: Path

	private fun res(name: String): Path {
		val tmp = Files.createTempFile(tempDir, "bundle-res", name.substringAfterLast('/'))
		tmp.writeBytes(javaClass.getResourceAsStream("/apex-bundle/$name")!!.readAllBytes())
		return tmp
	}

	@Test
	fun manifestIsTolerant() {
		val m = BundleManifest.parse(res("manifest.json").readText())
		assertEquals("test-1", m.version); assertEquals(1, m.itemCount); assertEquals("repo.json.zst", m.bundlePath)
		val alt = BundleManifest.parse("""{"sha":"abc","hash":"DEADBEEF","item_count":"12","extra":[1]}""")
		assertEquals("abc", alt.version); assertEquals("deadbeef", alt.sha256); assertEquals(12, alt.itemCount); assertNull(alt.bundlePath)
		assertEquals("abc@deadbeef", alt.cacheKey)
	}

	@Test
	fun verifiesAndUnpacksTheSyntheticBundleLosslessly() {
		val zst = res("repo.json.zst")
		val manifest = BundleManifest.parse(res("manifest.json").readText())
		RepoBundle.verify(zst, manifest)
		val out = Files.createTempDirectory(tempDir, "unpack").resolve("repo-extracted")
		assertEquals(4, RepoBundle.unpackZst(zst, out))
		assertTrue(out.resolve("items/TOPAZ_GEM.json").readText().startsWith("{\n  \"internalname\": \"TOPAZ_GEM\""))
		assertEquals("{\n  \"entity\": \"Silverfish\"\n}", out.resolve("mobs/scatha.json").readText())
		assertArrayEquals(ByteArray(16) { it.toByte() }, out.resolve("mobs/scatha.png").readBytes())
		assertEquals("{\"a\":1}", out.resolve("constants/misc.json").readText())

		val out2 = Files.createTempDirectory(tempDir, "unpack2").resolve("r")
		assertEquals(4, RepoBundle.unpackZst(res("toplevel.json.zst"), out2))
	}

	@Test
	fun shaMismatchAndBadBundlesKeepThePreviousCache() {
		val zst = res("repo.json.zst")
		val bad = BundleManifest("v", "00".repeat(32), null, null)
		assertThrows(BundleException::class.java) { RepoBundle.verify(zst, bad) }
		val target = Files.createTempDirectory(tempDir, "keep").resolve("repo-extracted")
		Files.createDirectories(target.resolve("items")); target.resolve("items/OLD.json").writeText("old")
		val garbage = Files.createTempFile(tempDir, "garbage", ".zst"); garbage.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
		assertThrows(Exception::class.java) { RepoBundle.unpackZst(garbage, target) }
		assertEquals("old", target.resolve("items/OLD.json").readText())
		assertThrows(BundleException::class.java) {
			RepoBundle.unpack("""{"files":{"../evil.json":"x"}}""".byteInputStream(), target)
		}
		assertEquals("old", target.resolve("items/OLD.json").readText())
	}

	@Test
	fun downloadTempFileIsDeletedOnSuccessAndFailure() = runBlocking {
		var seen: Path? = null
		val r = RepoDownloadManager.withTempDownload("apex-test", ".zst", { seen = it; it.writeText("data") }) { Files.size(it) }
		assertEquals(4L, r)
		assertFalse(seen!!.exists())
		var seen2: Path? = null
		assertThrows(IllegalStateException::class.java) {
			runBlocking {
				RepoDownloadManager.withTempDownload("apex-test", ".zip", { seen2 = it; it.writeText("partial") }) { error("extract failed") }
			}
		}
		assertFalse(seen2!!.exists())
		var seen3: Path? = null
		assertThrows(java.io.IOException::class.java) {
			runBlocking { RepoDownloadManager.withTempDownload<Unit>("apex-test", ".zip", { seen3 = it; throw java.io.IOException("network") }) { } }
		}
		assertFalse(seen3!!.exists())
	}
}
