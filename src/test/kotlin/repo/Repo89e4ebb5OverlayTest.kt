// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import io.github.moulberry.repo.NEURepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import net.minecraft.core.component.DataComponents
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemCache
import dev.ashtonite365.apex.repo.ModernOverlaysData
import dev.ashtonite365.apex.repo.RepoBundle
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.mc.loadItemFromNbt

@ExtendWith(AutoBootstrapExtension::class)
class Repo89e4ebb5OverlayTest {
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
			val root = Files.createTempDirectory("r89").also { tmpRoot = it }
			val zst = root.resolve("repo.json.zst")
			zst.writeBytes(javaClass.getResourceAsStream("/apex-bundle/r89e4ebb5/repo.json.zst")!!.readAllBytes())
			val dir = root.resolve("repo").also { RepoBundle.unpackZst(zst, it) }
			NEURepository.of(dir).also { it.reload(); loaded = it }
		}

	@Test fun overlayFolderIsSelectedOnThisGameVersion() {
		val o = ModernOverlaysData().also { it.reload(repo) }
		assertEquals(67, o.overlays.size, "overlay items")
		val dv = ItemCache.currentSaveVersion
		println("[r89] game data version $dv; overlay folders ${o.overlays.values.flatten().map { it.version }.toSet()}")
		for (id in o.overlays.keys) assertEquals(4671, o.getMostModernReadableOverlay(id, dv)?.version, "$id selected")
	}

	@OptIn(ExpensiveItemCacheApi::class, dev.ashtonite365.apex.util.mc.NeedsComponents::class)
	@Test fun overlayItemsLoadWithTheirItemModel() {
		val saved = RepoManager.overlayData.overlays
		try {
			RepoManager.overlayData.reload(repo)
			var withModel = 0
			for (id in RepoManager.overlayData.overlays.keys) {
				val tag = ItemCache.tryFindFromModernFormat(id)
				assertNotNull(tag, "$id overlay decodes")
				val stack = loadItemFromNbt(tag!!)!!.rebuildLive()
				if (stack.get(DataComponents.ITEM_MODEL) != null) withModel++
			}
			println("[r89] overlay stacks with item_model: $withModel / ${RepoManager.overlayData.overlays.size}")
			val helix = loadItemFromNbt(ItemCache.tryFindFromModernFormat(ItemId("ENCHANTED_HELIX_LOG"))!!)!!.rebuildLive()
			assertEquals("minecraft:stripped_mangrove_log", helix.get(DataComponents.ITEM_MODEL).toString())
		} finally { RepoManager.overlayData.overlays = saved }
	}

	@Test fun petnumsLoadsForEveryPetBase() {
		val nums = repo.constants.petNumbers
		val bases = repo.items.items.keys.filter { Regex("^[A-Z0-9_]+;\\d$").matches(it) && repo.items.items[it]!!.displayName.contains("{LVL}") }
			.map { it.substringBefore(';') }.toSet()
		println("[r89] petnums ${nums.size} bases; pet bases ${bases.size}; covered ${bases.count { it in nums }}")
		assertTrue(bases.all { it in nums })
		assertEquals(1, dev.ashtonite365.apex.repo.PetPlaceholders.defaultLevel(nums["RAT"]!![io.github.moulberry.repo.data.Rarity.LEGENDARY]))
	}
}
