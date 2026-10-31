// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.Rarity
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.PetPlaceholders
import dev.ashtonite365.apex.repo.PetVariants
import dev.ashtonite365.apex.repo.RepoBundle

class PetVariantsTest {

	private val temps = mutableListOf<Path>()

	private fun unpack(): Path {
		val zst = Files.createTempFile("petv", ".zst").also { temps.add(it) }
		zst.writeBytes(javaClass.getResourceAsStream("/apex-bundle/live/repo.json.zst")!!.readAllBytes())
		val dir = Files.createTempDirectory("petv").also { temps.add(it) }.resolve("repo")
		RepoBundle.unpackZst(zst, dir)
		return dir
	}

	@AfterEach fun deleteTemps() {
		for (p in temps) p.toFile().deleteRecursively()
		temps.clear()
	}
	private val dir by lazy { unpack() }
	private val repo: NEURepository by lazy { NEURepository.of(dir).also { it.reload() } }
	private val idx by lazy {
		PetVariants.index(repo.items.items.mapValues { it.value.displayName }, runCatching { repo.constants.petNumbers.keys }.getOrDefault(emptySet()))
	}

	@Test fun oneEntryPerBase() {
		val all = repo.items.items.keys
		val merged = idx.merged(all)
		assertTrue(idx.variants.size > 80, "bases: ${idx.variants.size}")
		val petEntries = merged.filter { idx.isPet(it) }
		assertEquals(idx.variants.size, petEntries.size)
		assertEquals(idx.variants.size, petEntries.map { idx.baseOf(it) }.toSet().size)
		val variantCount = idx.variants.values.sumOf { it.size }
		assertEquals(all.size - (variantCount - idx.variants.size), merged.size, "before ${all.size}, after ${merged.size}")
		assertTrue(all.filterNot { idx.isPet(it) }.all { it in merged }, "non-pets untouched")
		assertEquals("RAT;0".takeIf { "RAT;0" in all } ?: idx.variants["RAT"]!!.first(), idx.representative("RAT"))
	}

	@Test fun variantFocusMapsToTheMergedEntry() {
		for ((base, ids) in idx.variants) {
			val rep = idx.representative(base)!!
			for (id in ids) {
				assertEquals(idx.uidOf(rep), idx.uidOf(id), id)
				assertEquals(base, idx.baseOf(id))
			}
		}
		assertEquals("ENCHANTED_DIAMOND", idx.uidOf("ENCHANTED_DIAMOND"))
		assertFalse(idx.isPet("ENCHANTMENT_SHARPNESS;5"), "books are not pets")
	}

	@Test fun everyRecipeLinkToAPetVariantResolves() {
		val listed = idx.merged(repo.items.items.keys).toSet()
		val links = mutableSetOf<String>()
		for (item in repo.items.items.values) for (r in item.recipes) for (ing in r.allInputs + r.allOutputs) {
			val id = ing.itemId ?: continue
			if (idx.isPet(id)) links += id
		}

		if (Files.isDirectory(dir.resolve("mobs"))) Files.walk(dir.resolve("mobs")).use { s -> s.filter { it.toString().endsWith(".json") }.forEach { f ->
			Regex("\"([A-Z0-9_]+;\\d)(?::[\\d.]+)?\"").findAll(f.readText()).map { it.groupValues[1] }.filter { idx.isPet(it) }.forEach { links += it }
		} }
		assertTrue(links.size >= 28, "pet variant links: ${links.size}")
		for (id in links) {
			val rep = idx.representative(idx.baseOf(id)!!)
			assertNotNull(rep, id)
			assertTrue(rep in listed, "$id -> $rep is a JEI entry")
			assertEquals(idx.uidOf(rep!!), idx.uidOf(id), "$id opens the merged entry")
			assertTrue(id in idx.variantsOf(rep), "$id is selectable on the merged entry")
		}
	}

	@Test fun selectorCyclesThroughEveryVariant() {
		for ((_, ids) in idx.variants) {
			var cur = ids.first(); val seen = mutableListOf<String>()
			repeat(ids.size) { seen += cur; cur = idx.cycle(cur) }
			assertEquals(ids, seen); assertEquals(ids.first(), cur, "wraps")
			assertEquals(ids.last(), idx.cycle(ids.first(), -1))
		}
	}

	@Test fun searchFindsVariantRarities() {
		val names = idx.rarityNames("RAT;0".takeIf { idx.isPet(it) } ?: idx.variants["RAT"]!!.first())
		assertTrue(names.containsAll(idx.variants["RAT"]!!.mapNotNull { PetVariants.rarityOf(it)?.name }))
		assertTrue("LEGENDARY" in names || idx.variants["RAT"]!!.none { it.endsWith(";4") })
	}

	private val nums by lazy { runCatching { repo.constants.petNumbers }.getOrNull().orEmpty() }

	@Test fun noLeftoverPlaceholderForAnySelectedVariant() {
		for ((_, ids) in idx.variants) for (id in ids) {
			val item = repo.items.items[id]!!

			val rarity = PetVariants.rarityOf(id)
			val map = PetPlaceholders.stub(rarity?.let { r -> nums[idx.baseOf(id)]?.get(r) })
			(listOf(item.displayName) + item.lore).forEach { assertFalse("{" in PetPlaceholders.apply(it, map), "$id: $it") }
		}
	}

	@Test fun defaultLevelIsTheRepoMinimum() {

		val d = unpack()
		d.resolve("constants/petnums.json").writeText("""{"RAT":{"LEGENDARY":{"10":{"otherNums":[2.0],"statNums":{"HEALTH":10.0}},"100":{"otherNums":[7.0],"statNums":{"HEALTH":100.0}}}}}""")
		val r = NEURepository.of(d).also { it.reload() }
		val info = r.constants.petNumbers["RAT"]!![Rarity.LEGENDARY]!!

		io.github.moulberry.repo.constants.PetNumbers::class.java.getDeclaredField("levelingCurve").apply { isAccessible = true }.set(info, "10:100:1")
		io.github.moulberry.repo.constants.PetNumbers::class.java.getDeclaredField("minLevel").apply { isAccessible = true }.setInt(info, 0)
		assertEquals(10, PetPlaceholders.defaultLevel(info))
		val map = PetPlaceholders.stub(info)
		assertEquals("10", map["LVL"])
		assertEquals("§6[Lvl 10] Rat", PetPlaceholders.apply(r.items.items["RAT;4"]!!.displayName, map))
	}
}
