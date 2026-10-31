package dev.ashtonite365.apex.test.features.items

import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUMobDropRecipe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.recipes.HowToGet
import dev.ashtonite365.apex.features.items.recipes.PriceSnapshot
import dev.ashtonite365.apex.features.items.recipes.SourceKind
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.RepoBundle
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.util.ItemId

class HowToGetTest {
	companion object {

		private var tmpRoot: Path? = null
		private var loaded: Pair<NEURepository, Path>? = null

		@JvmStatic @AfterAll
		fun cleanUp() {
			loaded = null
			tmpRoot?.toFile()?.deleteRecursively()
			tmpRoot = null
		}
	}

	private val repo: Pair<NEURepository, Path>
		get() = loaded ?: run {
			val root = Files.createTempDirectory("howto").also { tmpRoot = it }
			val zst = root.resolve("repo.json.zst")
			zst.writeBytes(javaClass.getResourceAsStream("/apex-bundle/live/repo.json.zst")!!.readAllBytes())
			val dir = root.resolve("repo")
			RepoBundle.unpackZst(zst, dir)
			(NEURepository.of(dir).also { it.reload() } to dir).also { loaded = it }
		}

	@Test
	fun groupsEveryProducingRecipeAndReferencesMobsOnly() {
		val (core, dir) = repo
		val items = core.items.itemsSafe.values
		val all = items.flatMap { it.recipes }
		val mobs = MobIndex.build(items.flatMap { i -> i.recipes.filterIsInstance<NEUMobDropRecipe>().map { MobIndex.fromRecipe(i.skyblockItemId, it) } },
		                          MobIndex.readMobFiles(dir))
		val dropping = MobIndex.indexDrops(mobs)
		val model = HowToGet.build("ENCHANTED_WHEAT", all, dropping["ENCHANTED_WHEAT"].orEmpty())
		val kinds = model.recipeGroups.map { it.kind }
		assertTrue(SourceKind.CRAFTING in kinds && SourceKind.TRADE in kinds, "$kinds")
		assertEquals(kinds.sorted(), kinds)
		assertTrue(model.mobIds.isNotEmpty())
		assertTrue(model.mobIds.all { it in mobs }, "mob drops are ids of Mobs-tab entries")

		val gem = HowToGet.build("FINE_AMBER_GEM", all, listOf())
		assertTrue(gem.recipeGroups.map { it.kind }.containsAll(listOf(SourceKind.CRAFTING, SourceKind.FORGE)))
		assertTrue(all.filterIsInstance<NEUMobDropRecipe>().none { SourceKind.of(it) != null })
		assertTrue(HowToGet.build("NOT_AN_ITEM", all, listOf()).isEmpty)
	}

	@Test
	fun priceComesOnlyFromLiveDataAndSaysSoWhenMissing() {
		assertEquals(listOf("No live price"), PriceSnapshot().lines())
		val stock: (ItemId) -> ItemId.BazaarProduct = { ItemId.BazaarProduct(it.skyblockId) }
		assertEquals(listOf("No live price"), HowToGet.livePrice("ENCHANTED_WHEAT", stock, mapOf(), mapOf(), mapOf()).lines())
		val p = HowToGet.livePrice("HYPERION", stock, mapOf(), mapOf(ItemId("HYPERION") to 750_000_000.0), mapOf())
		assertEquals(750_000_000.0, p.lowestBin)
		assertTrue(p.lines().single().startsWith("AH lowest BIN: 750,000,000"))
		val bz = mapOf(ItemId.BazaarProduct("ENCHANTED_WHEAT") to HypixelStaticData.BazaarData(
			ItemId.BazaarProduct("ENCHANTED_WHEAT"), HypixelStaticData.BazaarStatus(150.5, 1, 1, 1, 160.0, 1, 1, 1)))
		val w = HowToGet.livePrice("ENCHANTED_WHEAT", stock, bz, mapOf(), mapOf())
		assertEquals(160.0, w.bazaarBuy); assertEquals(150.5, w.bazaarSell)
		assertEquals(2, w.lines().size)
	}
}
