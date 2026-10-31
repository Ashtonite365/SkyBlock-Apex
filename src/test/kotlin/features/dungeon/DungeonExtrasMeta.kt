// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.util.data.ManagedConfig

object DungeonExtrasMeta {
	private fun projectFile(path: String): File =
		generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, path) }.first { it.isFile }

	val migration: Map<String, List<String>> by lazy {
		MigrationMap::class.java.getResourceAsStream(MigrationMap.RESOURCE)!!.bufferedReader().readLines()
			.filter { it.isNotBlank() && !it.startsWith("#") }.associate { l -> l.split('\t').let { it[0] to it } }
	}
	val inventory: Map<Pair<String, String>, List<String>> by lazy {
		projectFile("docs/rewrite/inventory.tsv").readLines().drop(1).filter { it.isNotBlank() }
			.associate { l -> l.split('\t').let { (it[0] to it[1]) to it } }
	}
	val translations: String by lazy { projectFile("translations/en_us.json").readText() }

	fun assertRows(config: ManagedConfig, key: String, kind: String, risky: Boolean, default: String = if (risky) "off" else "") {
		val id = "${config.name}.$key"
		val mig = migration[id] ?: error("no migration-map row for $id")

		assertEquals(dev.ashtonite365.apex.settings.LayoutMoves.bundled["dungeons.$id"] ?: "dungeons.$id", mig[1], id)
		assertEquals(kind, mig[2], id)
		val inv = inventory[config.name to key] ?: error("no inventory row for $id")
		assertEquals(kind, inv[2], id)
		assertEquals("DUNGEONS", inv[3], id)
		assertEquals("Dungeons", inv[4], id)
		assertEquals("dungeons.$id", inv[6], id)
		assertEquals(if (risky) "risky" else "", inv[7], id)
		assertEquals(default, inv[8], id)
		assertTrue("\"apex.config.$id\":" in translations, "no translation for $id")
		assertTrue("\"apex.config.$id.description\":" in translations, "no description for $id")
	}

	fun assertSecretsRows(config: ManagedConfig, key: String, kind: String, risky: Boolean, path: String) {
		val id = "${config.name}.$key"
		val mig = migration[id] ?: error("no migration-map row for $id")
		assertEquals(path, mig[1], id)
		assertEquals(kind, mig[2], id)
		val inv = inventory[config.name to key] ?: error("no inventory row for $id")
		assertEquals(kind, inv[2], id)
		assertEquals("Secrets", inv[4], id)
		assertEquals(path, inv[6], id)
		assertEquals(if (risky) "risky" else "", inv[7], id)
		assertEquals(if (risky) "off" else "", inv[8], id)
		assertTrue("\"apex.config.$id\":" in translations, "no translation for $id")
		assertTrue("\"apex.config.$id.description\":" in translations, "no description for $id")
	}
}
