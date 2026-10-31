// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.buildcheck

import java.io.File
import java.util.zip.ZipFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JarContentsTest {
	private val jar = File(System.getProperty("apex.jar") ?: error("apex.jar not set"))

	private val names: List<String> by lazy {
		assertTrue(jar.isFile, "missing jar $jar")
		ZipFile(jar).use { z -> z.entries().asSequence().map { it.name }.toList() }
	}

	private fun text(name: String) = ZipFile(jar).use { z -> z.getInputStream(z.getEntry(name) ?: error("no $name")).readBytes().decodeToString() }

	@Test
	fun jarIsNotEmpty() {
		assertTrue(names.size > 1000, "only ${names.size} entries")
		assertTrue(names.count { it.endsWith(".class") } > 1000)

		assertEquals(0, names.count { it.startsWith("resourcepacks/transparent_overlay/") })
		assertTrue(names.count { it.startsWith("META-INF/apex/LICENSES/") } > 10)
	}

	@Test
	fun licence() {
		assertTrue(!names.contains("META-INF/apex/EDITION"))
		for (l in listOf("LICENSE", "META-INF/apex/LICENSE"))
			assertTrue(text(l).trimStart().startsWith("GNU GENERAL PUBLIC LICENSE"), "$l is not the GPL")
		assertTrue(names.contains("META-INF/apex/LICENSES/LibNinePatch-MPL-2.0-via-MoulConfig.txt") || names.contains("META-INF/apex/LICENSES/LICENSE-LibNinePatch-MPL-2.0.txt"))
		assertEquals(1, names.count { it == "LICENSE" })
		for (n in listOf("ATTRIBUTION-CC-BY-4.0.txt", "LICENSE-SkyblockAddons.txt", "LICENSE-RepoParser-BSD-2-Clause.txt"))
			assertTrue(names.contains("META-INF/apex/LICENSES/$n"), n)
	}

	@Test
	fun ownCodecOnlyNoAircompressor() {

		assertTrue(names.contains("dev/ashtonite365/apex/compress/Zstd.class"))
		assertTrue(names.contains("dev/ashtonite365/apex/compress/ApexPack.class"))
		assertEquals(listOf<String>(), names.filter { it.startsWith("io/airlift/") || it.contains("thirdparty/zstd") || it.contains("aircompressor", ignoreCase = true) })
	}

	@Test
	fun noHypixelPackButEverythingElse() {
		assertEquals(listOf<String>(), names.filter { it.contains("hypixel_skyblock") && !it.endsWith(".class") })
		assertEquals(listOf<String>(), names.filter { it.contains("LicenseRef-Hypixel") })
		val notice = text("META-INF/apex/NOTICE")
		assertTrue(!notice.contains("LicenseRef-Hypixel"))
		assertTrue(notice.contains("api.hypixel.net/v2/resources/packs"))

		assertEquals(listOf<String>(), names.filter { it.startsWith("moe/nea/lisp/") || it.endsWith(".lisp") })

		assertEquals(1, names.count { it.endsWith("fast_travel_map.png") })
		val packs = names.filter { it.startsWith("resourcepacks/") && it != "resourcepacks/" }
		assertTrue(packs.isEmpty(), packs.toString())

		assertTrue(names.none { it.contains("storageoverlay/classic_") || it.contains("styles/icon/") || it.contains("textures/socials/") || it.startsWith("legacy_data/") || it == "assets/apex/logo.png" })
	}
}
