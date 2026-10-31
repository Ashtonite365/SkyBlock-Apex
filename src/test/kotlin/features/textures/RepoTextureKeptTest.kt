// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.textures

import io.github.moulberry.repo.NEURepository
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.ItemCache
import dev.ashtonite365.apex.repo.LearnedSkullTextures
import dev.ashtonite365.apex.repo.OfficialItemSkins
import dev.ashtonite365.apex.repo.RepoBundle

class RepoTextureKeptTest {
	companion object {

		private var tmpRoot: Path? = null
		private var loaded: NEURepository? = null

		@JvmStatic @AfterAll
		fun cleanUp() {
			loaded = null
			tmpRoot?.toFile()?.deleteRecursively()
			tmpRoot = null
		}
	}

	private val repo: NEURepository
		get() = loaded ?: run {
			val root = Files.createTempDirectory("apex-tex").also { tmpRoot = it }
			val zst = root.resolve("repo.json.zst")
			Files.write(zst, javaClass.getResourceAsStream("/apex-bundle/live/repo.json.zst")!!.readAllBytes())
			val dir = root.resolve("repo")
			RepoBundle.unpackZst(zst, dir)
			NEURepository.of(dir).also { it.reload(); loaded = it }
		}

	@Test fun texturedRepoHeadsAreNeverOverridden() {
		var textured = 0
		for ((id, item) in repo.items.items) {
			val nbt = item.nbttag ?: ""
			val head = item.minecraftItemId == "minecraft:skull" || item.minecraftItemId == "minecraft:player_head"
			if (!head) { assertFalse(ItemCache.legacyHeadWithoutTexture(item.minecraftItemId, nbt), id); continue }
			if (!nbt.contains("textures:")) continue
			textured++
			assertFalse(ItemCache.legacyHeadWithoutTexture(item.minecraftItemId, nbt), "$id has a repo texture; no fallback may replace it")
		}
		assertTrue(textured > 2000, "textured heads checked: $textured")
	}

	@Test fun repoTextureWinsOverOfficialAndLearned() {
		val learned = LearnedSkullTextures.Learned("00000000-0000-0000-0000-000000000001", "learned")
		assertEquals("repo", OfficialItemSkins.headTexture("RAT;4", "repo", "official", learned)!!.value)
		assertEquals("official", OfficialItemSkins.headTexture("ESSENCE_GOLD", null, "official", learned)!!.value)
		assertEquals("learned", OfficialItemSkins.headTexture("ESSENCE_GOLD", null, null, learned)!!.value)
	}

	@Test fun shardsAndHelixLogAreNotHeads() {
		for (id in listOf("ENCHANTED_HELIX_LOG", "ATTRIBUTE_SHARD_ALMIGHTY;1")) {
			val item = repo.items.items[id] ?: continue
			assertFalse(ItemCache.legacyHeadWithoutTexture(item.minecraftItemId, item.nbttag), id)
		}
	}
}
