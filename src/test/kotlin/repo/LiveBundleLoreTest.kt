// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import io.github.moulberry.repo.NEURepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.RepoBundle

class LiveBundleLoreTest {
	companion object {

		private var tmp: Path? = null
		private var loaded: NEURepository? = null
		private val repo: NEURepository get() = loaded ?: error("repo not unpacked")

		@JvmStatic @BeforeAll
		fun unpack() {
			val root = Files.createTempDirectory("lore")
			tmp = root
			val zst = root.resolve("repo.json.zst")
			zst.writeBytes(javaClass.getResourceAsStream("/apex-bundle/live/repo.json.zst")!!.readAllBytes())
			val dir = root.resolve("repo")
			RepoBundle.unpackZst(zst, dir)
			loaded = NEURepository.of(dir).also { it.reload() }
		}

		@JvmStatic @AfterAll
		fun cleanUp() {
			loaded = null
			tmp?.toFile()?.deleteRecursively()
			tmp = null
		}
	}
	private fun item(id: String) = repo.items.items[id] ?: error("$id missing")
	private fun plain(s: String) = s.replace(Regex("§."), "")
	private fun lore(id: String) = item(id).lore.map(::plain)
	private fun wiki(id: String) = item(id).info.orEmpty().filter { it.startsWith("§8Wiki: ") }.map { it.removePrefix("§8Wiki: ") }

	@Test fun taterArmourHasTieredBonusAndBootsFarmersGrace() {
		for (piece in listOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
			val id = "MELON_$piece"
			val l = lore(id)
			assertTrue(plain(item(id).displayName).startsWith("Tater "), "$id name")
			assertTrue(l.any { it.startsWith("Tiered Bonus: Cropier Crops (2+ pieces)") }, "$id tiered bonus: $l")
			val text = l.joinToString(" ")
			assertTrue("0.00%➜0.03%➜0.04%➜0.05%" in text, "$id Cropie chances")
			assertTrue("2.0%➜12.0%➜16.0%➜20.0%" in text, "$id Greenhouse chances")
			assertTrue("+0➜10➜20➜30" in text, "$id farming fortune")
			assertEquals(piece == "BOOTS", l.contains("Ability: Farmer's Grace"), "$id Farmer's Grace only on the boots")
			assertTrue(l.contains("This item can be reforged!"), id)
			assertEquals("UNCOMMON $piece", l.last())
		}
	}

	@Test fun aoteAndHyperionAbilities() {
		val aote = lore("ASPECT_OF_THE_END")
		assertTrue(aote.any { it.startsWith("Ability: Instant Transmission") && "RIGHT CLICK" in it }, aote.toString())
		assertTrue(aote.any { it.startsWith("Mana Cost: ") }, aote.toString())

		assertTrue(item("ASPECT_OF_THE_END").lore.any { it.startsWith("§8Mana Cost: ") }, item("ASPECT_OF_THE_END").lore.toString())

		val hyp = lore("HYPERION")
		assertTrue(hyp.contains("Right-click to use your class ability!"), hyp.toString())
		assertTrue(hyp.any { it.startsWith("❣ Requires The Catacombs Floor VII Completion") }, hyp.toString())
		assertEquals("LEGENDARY SWORD", hyp.last())
	}

	@Test fun jerryRaritiesArePetsNotBooks() {
		val rar = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
		for (r in 0..5) {
			val it = item("JERRY;$r")
			assertTrue(plain(it.displayName).endsWith("Jerry") && "Lvl" in it.displayName, "JERRY;$r ${it.displayName}")
			assertFalse(it.lore.any { "Ultimate Jerry" in it }, "JERRY;$r is not the Ultimate Jerry book")
			assertEquals(rar[r], plain(it.lore.last()).trim(), "JERRY;$r rarity line")
			assertTrue("textures:" in it.nbttag || "Value:" in it.nbttag, "JERRY;$r has a skull texture")
		}
	}

	@Test fun namesFixed() {
		assertEquals("Birch Minion XII", plain(item("BIRCH_GENERATOR_12").displayName))
		assertEquals("Ivory Black Cat Skin", plain(item("PET_SKIN_BLACK_CAT_IVORY").displayName))
		val skins = repo.items.items.filterKeys { it.startsWith("PET_SKIN_") }
		assertTrue(skins.size > 200)

		val joined = Regex("[a-z](Black Cat|Cat|Dog|Wolf|Rabbit|Sheep|Elephant|Dragon|Monkey|Tiger|Lion|Jerry|Rock|Parrot|Blaze|Squid)\\b")
		skins.forEach { (id, it) -> assertFalse(joined.containsMatchIn(plain(it.displayName)), "$id '${it.displayName}' camel-joined") }
		skins.forEach { (id, it) -> assertTrue(plain(it.displayName).endsWith(" Skin") || plain(it.displayName).endsWith(" Skin)"), "$id '${it.displayName}'") }
		val minion = Regex("^.* Minion ([IVXLC]+)$")
		repo.items.items.values.map { plain(it.displayName) }.filter { " Minion " in it }.forEach {
			minion.matchEntire(it)?.let { m -> assertTrue(m.groupValues[1].all { c -> c in "IVXLC" }, it) }
			assertFalse(Regex(" [IVX]+[a-z]$").containsMatchIn(it), it)
		}
	}

	@Test fun trophyFishAreSpeciesPlusTier() {
		assertEquals("Blobfish BRONZE", plain(item("BLOBFISH_BRONZE").displayName))
		assertEquals("Golden Fish DIAMOND", plain(item("GOLDEN_FISH_DIAMOND").displayName))
		val tiers = listOf("BRONZE", "SILVER", "GOLD", "DIAMOND")
		val fish = repo.items.items.filter { (id, it) -> tiers.any { t -> id.endsWith("_$t") } && it.lore.any { l -> plain(l).endsWith(" TROPHY") } }
		assertEquals(18 * 4, fish.size, "18 species x 4 tiers")
		fish.forEach { (id, it) ->
			assertTrue(plain(it.displayName).endsWith(" " + id.substringAfterLast('_')), "$id '${it.displayName}'")
			assertFalse(it.minecraftItemId == "minecraft:barrier", "$id barrier")
			assertTrue(wiki(id).isNotEmpty(), "$id wiki link")
		}
	}

	@Test fun wikiLinksCorrectAndDecoded() {
		assertEquals(listOf("https://hypixelskyblock.minecraft.wiki/w/Enchanted_Shark_Fin"), wiki("ENCHANTED_SHARK_FIN"))
		val pct = Regex("%(?!25|3F|23|22|3C|3E)[0-9A-Fa-f]{2}")
		val bad = repo.items.items.flatMap { (id, _) -> wiki(id).filter { pct.containsMatchIn(it.substringAfter("/w/")) }.map { "$id $it" } }
		assertTrue(bad.isEmpty(), bad.take(10).toString())
		val linked = repo.items.items.keys.count { wiki(it).isNotEmpty() }
		assertTrue(linked >= 7500, "items with a wiki link: $linked")
	}

	@Test fun enchantedBookExtraAttributesMatchRepoId() {
		val nbt = item("SHARPNESS;5").nbttag
		assertTrue("id:\"ENCHANTED_BOOK\"" in nbt && "sharpness:5" in nbt, nbt)
		val shard = repo.items.items.entries.first { it.key.startsWith("ATTRIBUTE_SHARD_") && ';' in it.key }
		assertTrue("ATTRIBUTE_SHARD" in shard.value.nbttag && "attributes:" in shard.value.nbttag, "${shard.key}: ${shard.value.nbttag}")
	}

	@Test fun loreHasNoHtmlEntitiesOrStraySectionSigns() {
		val stray = Regex("§(?![0-9a-fk-or])|§$")
		val bad = repo.items.items.flatMap { (id, it) -> it.lore.filter { l -> stray.containsMatchIn(l) }.map { "$id: $it" } }
		assertTrue(bad.isEmpty(), bad.take(10).toString())
	}

	@Test fun starredItemsUseTheirOwnBaseStats() {
		assertTrue(item("STARRED_ADAPTIVE_BOOTS").lore.any { plain(it) == "Health: +100" }, item("STARRED_ADAPTIVE_BOOTS").lore.toString())
		assertTrue(item("STARRED_STONE_BLADE").lore.any { plain(it) == "Damage: +190" })
	}

	@Test fun dragonRangePetsResolveAtTheirLowestLevel() {

		val info = repo.constants.petNumbers["JADE_DRAGON"]!![io.github.moulberry.repo.data.Rarity.LEGENDARY]!!
		assertEquals(100, info.lowLevel)
		assertEquals(200, info.highLevel)
		val map = dev.ashtonite365.apex.repo.PetPlaceholders.stub(info)
		assertEquals("100", map["LVL"])
		val lore = item("JADE_DRAGON;4").lore.map { plain(dev.ashtonite365.apex.repo.PetPlaceholders.apply(it, map)) }
		assertTrue("Strength: +25" in lore, lore.toString())
		assertFalse(lore.any { '{' in it }, lore.toString())
	}

	@Test fun infoHasNoWikiFooterBoilerplate() {
		val bad = repo.items.items.filterValues { it.info.orEmpty().any { l -> plain(l).startsWith("Retrieved from \"") } }.keys
		assertTrue(bad.isEmpty(), bad.take(10).toString())
	}

	@Test fun petPerksKeepPlaceholders() {
		val pets = repo.items.items.filter { (id, it) -> Regex("^.+;\\d$").matches(id) && it.lore.any { l -> Regex("\\{(\\d|[A-Z_]+)\\}").containsMatchIn(l) } }
		assertTrue(pets.size >= 300, "pets with perk placeholders: ${pets.size}")
	}
}
