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
import dev.ashtonite365.apex.repo.PetPlaceholders
import dev.ashtonite365.apex.repo.RepoBundle
import io.github.moulberry.repo.data.Rarity

class PetPlaceholdersTest {
	companion object {

		private var tmp: Path? = null
		private var loaded: NEURepository? = null
		private val repo: NEURepository get() = loaded ?: error("repo not unpacked")

		@JvmStatic @BeforeAll
		fun unpack() {
			val root = Files.createTempDirectory("pets")
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
	private val petId = Regex("^(.+);(\\d)$")

	@Test fun noPetHasALeftoverPlaceholder() {
		val nums = runCatching { repo.constants.petNumbers }.getOrNull().orEmpty()
		var checked = 0
		for ((id, item) in repo.items.items) {
			val m = petId.matchEntire(id) ?: continue
			val texts = listOf(item.displayName) + item.lore
			if (texts.none { "{" in it }) continue
			val pet = m.groupValues[1]
			val rarity = Rarity.entries.getOrNull(m.groupValues[2].toInt()) ?: continue
			assertTrue(PetPlaceholders.isStubPet(pet, nums.keys, item.displayName), "$id gets stub pet data")
			val map = PetPlaceholders.stub(nums[pet]?.get(rarity))
			texts.forEach { t -> assertFalse("{" in PetPlaceholders.apply(t, map), "$id: '${PetPlaceholders.apply(t, map)}'") }
			checked++
		}
		assertTrue(checked > 300, "checked $checked pet entries")
	}

	@Test fun ratLegendaryName() {
		val rat = repo.items.items["RAT;4"]!!

		assertEquals("§6[Lvl 1] Rat", PetPlaceholders.apply(rat.displayName, PetPlaceholders.stub(null)))
		assertEquals(1, PetPlaceholders.defaultLevel(null))
		assertFalse(PetPlaceholders.isStubPet("DIAMOND", emptySet(), "Diamond"))
	}
}
