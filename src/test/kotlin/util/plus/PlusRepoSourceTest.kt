package dev.ashtonite365.apex.test.util.plus

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.ashtonite365.apex.util.plus.PlusRepoSource

class PlusRepoSourceTest {
	private fun zip(vararg entries: Pair<String, String>): ByteArray {
		val bo = ByteArrayOutputStream()
		ZipOutputStream(bo).use { z -> for ((n, c) in entries) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
		return bo.toByteArray()
	}

	@Test
	fun allowlist() {
		assertTrue(PlusRepoSource.isAllowedUrl(URI(PlusRepoSource.ZIP_URL)))
		assertTrue(PlusRepoSource.isAllowedUrl(URI("https://codeload.github.com/hannibal002/SkyHanni-REPO/zip/refs/heads/main")))
		assertFalse(PlusRepoSource.isAllowedUrl(URI("http://github.com/x")))
		assertFalse(PlusRepoSource.isAllowedUrl(URI("https://evil.com/x")))
		assertFalse(PlusRepoSource.isAllowedUrl(URI("https://github.com.evil.com/x")))
		assertFalse(PlusRepoSource.isAllowedUrl(URI("https://user@github.com/x")))
		assertFalse(PlusRepoSource.isAllowedUrl(URI("https://github.com:8443/x")))
	}

	@Test
	fun entryNames() {
		assertEquals("a/b.json", PlusRepoSource.safeEntryPath("a/./b.json"))
		assertNull(PlusRepoSource.safeEntryPath("../x"))
		assertNull(PlusRepoSource.safeEntryPath("a/../../x"))
		assertNull(PlusRepoSource.safeEntryPath("/etc/passwd"))
		assertNull(PlusRepoSource.safeEntryPath("C:\\x"))
	}

	@Test
	fun readZip() {
		val m = PlusRepoSource.readZip(ByteArrayInputStream(zip(
			"SkyHanni-REPO-main/README.md" to "x",
			"SkyHanni-REPO-main/constants/Skulls.json" to "{}",
			"SkyHanni-REPO-main/constants/rift/RiftRace.json" to "[]")))
		assertEquals(setOf("constants/Skulls.json", "constants/rift/RiftRace.json"), m.keys)
		assertThrows<IllegalStateException> { PlusRepoSource.readZip(ByteArrayInputStream(zip("SkyHanni-REPO-main/../../evil.json" to "{}"))) }
		assertThrows<IllegalStateException> { PlusRepoSource.readZip(ByteArrayInputStream(zip("r/constants/a.json" to "x".repeat(100))), maxTotal = 10) }
		assertThrows<IllegalStateException> { PlusRepoSource.readCapped(ByteArrayInputStream(ByteArray(100)), 10) }
	}

	@Test
	fun bundledFallback() {
		val skulls = PlusRepoSource.readText("constants/Skulls.json")
		assertNotNull(skulls)
		assertTrue("THUNDER_SPARK" in skulls!!)
		assertNotNull(PlusRepoSource.readText("constants/rift/RiftRace.json"))
		assertNull(PlusRepoSource.readText("../../apex.mixins.json"))
	}
}
