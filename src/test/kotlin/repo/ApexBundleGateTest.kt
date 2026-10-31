package dev.ashtonite365.apex.test.repo

import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUCraftingRecipe
import io.github.moulberry.repo.data.NEUForgeRecipe
import io.github.moulberry.repo.data.NEUMobDropRecipe
import io.github.moulberry.repo.data.NEUNpcShopRecipe
import io.github.moulberry.repo.data.NEUTradeRecipe
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.BundleManifest
import dev.ashtonite365.apex.repo.RepoBundle
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.repo.mobs.KnownEntityNames
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.repo.mobs.MobRenderPlanner
import dev.ashtonite365.apex.repo.recipes.SBForgeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBKatUpgradeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBTradeRecipeRenderer

class ApexBundleGateTest {
	companion object {

		private var tmpRoot: Path? = null
		private fun root(): Path = tmpRoot ?: Files.createTempDirectory("gate").also { tmpRoot = it }

		@JvmStatic @AfterAll
		fun cleanUp() {
			tmpRoot?.toFile()?.deleteRecursively()
			tmpRoot = null
		}
	}

	private fun fixture(name: String): Path {
		val p = Files.createTempFile(root(), "gate", name)
		p.writeBytes(javaClass.getResourceAsStream("/apex-bundle/live/$name")!!.readAllBytes())
		return p
	}

	@Test
	fun liveBundleLoadsWithZeroErrors() {
		val errors = mutableListOf<String>()
		fun <T> stage(name: String, f: () -> T): T? = try { f() } catch (e: Throwable) { errors.add("$name: $e"); null }

		val manifest = BundleManifest.parse(fixture("manifest.json").readText())
		val zst = fixture("repo.json.zst")
		stage("sha256") { RepoBundle.verify(zst, manifest) }
		val dir = root().resolve("repo-extracted")
		val files = stage("unpack") { RepoBundle.unpackZst(zst, dir) } ?: 0
		assertEquals(manifest.fileCount, files)

		val repo = NEURepository.of(dir)
		stage("NEURepository.reload") { repo.reload() }
		val items = repo.items.itemsSafe.values
		val recipes = items.flatMap { it.recipes }
		val byType = recipes.groupingBy { it.javaClass.simpleName }.eachCount()
		val trades = stage("trade category") { SBTradeRecipeRenderer.findAllRecipes(repo).count() } ?: -1
		val forge = stage("forge category") { SBForgeRecipeRenderer.findAllRecipes(repo).count() } ?: -1
		val kat = stage("katgrade category") { SBKatUpgradeRecipeRenderer.findAllRecipes(repo).count() } ?: -1
		val shop = recipes.count { it is NEUNpcShopRecipe }
		val crafting = recipes.count { it is NEUCraftingRecipe }
		val mobs = stage("mob index") {
			val drops = items.flatMap { item -> item.recipes.filterIsInstance<NEUMobDropRecipe>().map { MobIndex.fromRecipe(item.skyblockItemId, it) } }
			MobIndex.build(drops, MobIndex.readMobFiles(dir))
		} ?: mapOf()
		var unknown = 0
		stage("mob renders") {
			for (mob in mobs.values) {
				val plan = MobRenderPlanner.plan(mob, KnownEntityNames.names::contains) { ref ->
					runCatching { JsonParser.parseString(dir.resolve(ref.substringAfter(':')).readText()).asJsonObject }.getOrNull()
				}
				check(plan.entity in KnownEntityNames.names) { "${mob.id} -> ${plan.entity}" }
				if (plan.unknown) unknown++
			}
		}
		println("APEX-GATE files=$files items=${items.size} recipes=${recipes.size} byType=$byType crafting=$crafting forge=$forge " +
			        "shop=$shop trades=$trades kat=$kat mobs=${mobs.size} mobsWithDrops=${mobs.values.count { it.drops.isNotEmpty() }} " +
			        "unknownModel=$unknown incomplete=${repo.isIncomplete} errors=$errors")
		assertTrue(errors.isEmpty(), errors.joinToString("\n"))
		assertTrue(items.size >= 7944, "items ${items.size}")
		assertTrue(recipes.size >= 3822, "recipes ${recipes.size}")

		assertTrue(recipes.count { it is NEUMobDropRecipe } >= 749, "mob drops ${recipes.count { it is NEUMobDropRecipe }}")
		assertTrue(trades >= 13, "trades $trades")
		assertTrue(recipes.count { it is NEUForgeRecipe } >= 116)
	}
}
