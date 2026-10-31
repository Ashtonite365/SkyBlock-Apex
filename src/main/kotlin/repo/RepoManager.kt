// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.NEURepositoryException
import io.github.moulberry.repo.data.NEUItem
import io.github.moulberry.repo.data.NEURecipe
import io.github.moulberry.repo.data.Rarity
import java.nio.file.Path
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket
import net.minecraft.world.item.crafting.SelectableRecipe
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.Apex.logger
import dev.ashtonite365.apex.events.ReloadRegistrationEvent
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.MinecraftDispatcher
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.TestUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

object RepoManager  {
	@Config
	object TConfig : ManagedConfig("repo", Category.GENERAL) {

		var repos by string("repos") { RepoSources.DEFAULT_STRING }
		val autoUpdate: Boolean by toggle("autoUpdate") { true }

		var bundleManifest by string("bundle-manifest") { "manifest.json" }
		var bundleFile by string("bundle-file") { "repo.json.zst" }
		var bundleUrlTemplate by string("bundle-url") { "https://raw.githubusercontent.com/{user}/{repo}/{branch}/{path}" }
		val reset: Unit by button("reset") {
			repos = RepoSources.DEFAULT_STRING
			bundleManifest = "manifest.json"
			bundleFile = "repo.json.zst"
			bundleUrlTemplate = "https://raw.githubusercontent.com/{user}/{repo}/{branch}/{path}"
			this.markDirty()
		}
		override fun migrate(saved: kotlinx.serialization.json.JsonObject) = RepoSources.migrate(saved)
		val enableREI: Boolean by toggle("enable-rei") { true }
		val disableItemGroups: Boolean by toggle("disable-item-groups") { true }
		val reload: Unit by button("reload") {
			this.markDirty()
			RepoManager.let { repo -> Apex.coroutineScope.launch { repo.reload() } }
		}
		val redownload: Unit by button("redownload") {
			this.markDirty()
			launchAsyncUpdate(force = true)
		}
		val alwaysSuperCraft: Boolean by toggle("enable-super-craft") { true }
		var warnForMissingItemListMod: Boolean by toggle("warn-for-missing-item-list-mod") { true }
		val perfectRenders: PerfectRender by choice("perfect-renders") { PerfectRender.RENDER }
	}

	enum class PerfectRender(val label: String) : net.minecraft.util.StringRepresentable {
		NOTHING("nothing"), RENDER("render"), RENDER_AND_TEXT("text");

		fun rendersPerfectText(): Boolean = this === RENDER_AND_TEXT
		fun rendersPerfectVisuals(): Boolean = this !== NOTHING

		override fun getSerializedName() = label
	}

	val currentDownloadedSha: String? get() = RepoDownloadManager.latestSavedVersionHash

	var recentlyFailedToUpdateItemList: Boolean = false

	val essenceRecipeProvider: EssenceRecipeProvider = EssenceRecipeProvider()
	val recipeCache: BetterRepoRecipeCache = BetterRepoRecipeCache(essenceRecipeProvider, ReforgeStore)
	val miningData: MiningRepoData = MiningRepoData()
	val overlayData: ModernOverlaysData = ModernOverlaysData()
	val enchantedBookCache: EnchantedBookCache = EnchantedBookCache()
	val enchantData: EnchantData = EnchantData()

	private fun derivedCaches(): List<io.github.moulberry.repo.IReloadable> = listOf(
		overlayData, ItemCache, BoxContents, RepoItemTypeCache, ExpLadders, ItemNameLookup, ReforgeStore,
		essenceRecipeProvider, recipeCache, miningData, enchantedBookCache, enchantData,
		dev.ashtonite365.apex.repo.mobs.MobIndex,
	)

	fun makeCoreRepository(path: Path): NEURepository = NEURepository.of(path).also { repo ->
		derivedCaches().forEach(repo::registerReloadListener)
		ReloadRegistrationEvent.publish(ReloadRegistrationEvent(repo))
		repo.registerReloadListener { _ -> if (!TestUtil.isInTest) requestRecipeResyncLater() }
	}

	private fun requestRecipeResyncLater() {
		Apex.coroutineScope.launch(context = MinecraftDispatcher) {
			val sent = resyncRecipes()
			if (sent) return@launch
			logger.warn("Could not send the client an empty ClientboundUpdateRecipesPacket, so item list viewers may show stale items until the next level tick.")
			recentlyFailedToUpdateItemList = !sent
		}
	}

	private fun resyncRecipes(): Boolean {
		val mc = Minecraft.getInstance()
		if (mc.level == null) return false
		val handler = mc.connection ?: return false
		handler.handleUpdateRecipes(ClientboundUpdateRecipesPacket(mutableMapOf(), SelectableRecipe.SingleInputSet.empty()))
		return true
	}

	private lateinit var repository: NEURepository

	val coreRepo: NEURepository get() = repository

	fun getAllRecipes() = coreRepo.items.itemsSafe.values.asSequence().flatMap { it.recipes }
	fun allItemIds(): List<String> = coreRepo.items.itemsSafe.values.map { it.skyblockItemId }

	fun getRecipesFor(catalogItemId: ItemId): Set<NEURecipe> = recipeCache.recipes[catalogItemId].orEmpty()
	fun getUsagesFor(catalogItemId: ItemId): Set<NEURecipe> = recipeCache.usages[catalogItemId].orEmpty()

	fun getCoreItem(catalogItemId: ItemId): NEUItem? = repository.items.getItemBySkyblockId(catalogItemId.skyblockId)

	fun downloadOverridenBranch(branch: String): Unit = run {
		Apex.coroutineScope.launch { updateThenReload { RepoDownloadManager.downloadUpdate(true, branch) } }
	}

	fun launchAsyncUpdate(force: Boolean = false): Unit = run {
		Apex.coroutineScope.launch { updateThenReload { RepoDownloadManager.downloadUpdate(force) } }
	}

	suspend fun updateThenReload(update: suspend () -> Boolean): Boolean {
		val hasLocal = hasLocalCopy()
		if (hasLocal) reload()
		val updated = try {
			update()
		} catch (e: kotlinx.coroutines.CancellationException) {
			throw e
		} catch (e: Throwable) {
			logger.warn("Repo update failed (${e.javaClass.simpleName}: ${e.message}); ${if (hasLocal) "keeping the local copy" else "no local repo copy available"}")
			null
		}
		if (updated == true || !hasLocal) reload()
		return updated != null
	}

	fun hasLocalCopy(): Boolean = runCatching {
		java.nio.file.Files.isDirectory(RepoDownloadManager.repoSavedLocation.resolve("items")) ||
			RepoDownloadManager.mergeLayers()
	}.getOrDefault(false)

	fun reloadForTest(from: Path) = makeCoreRepository(from).let { repo ->
		repository = repo
		this.reloadSync()
	}

	suspend fun reload() = withContext(Dispatchers.IO) { reloadSync() }

	fun reloadSync(): Unit = try {
		logger.info("Repo reload: starting")
		repository.reload()
		logger.info("Repo reload: done")
	} catch (exc: Exception) {
		Faults.reportFault("Repository reload threw; repo-backed features may be missing", exc)
		Client.showChat(tr("apex.repo.reloadfail", "Failed to reload repository. This will result in some mod features not working."))
	}

	private var started = false

	fun initialize(): Unit {
		if (started) return
		started = true
		val testRepo = System.getProperty("apex.testrepo")
		if (testRepo != null) return reloadForTest(Path.of(testRepo))
		repository = makeCoreRepository(RepoDownloadManager.repoSavedLocation)
		if (TConfig.autoUpdate) launchAsyncUpdate()
		else Apex.coroutineScope.launch { reload() }
	}

	fun getPotentialStubPetData(catalogItemId: ItemId): PetData? = stubPetOf(catalogItemId)

	private fun stubPetOf(catalogItemId: ItemId): PetData? {
		val id = catalogItemId.skyblockId
		val split = id.indexOf(';')
		if (split < 0 || id.indexOf(';', split + 1) >= 0) return null
		val petId = id.substring(0, split)
		val tier = id.substring(split + 1)
		if (tier.any { !it.isDigit() }) return null
		val rarity = Rarity.entries.getOrNull(tier.toInt()) ?: return null

		val repoName = runCatching { coreRepo.items.getItemBySkyblockId(catalogItemId.skyblockId)?.displayName }.getOrNull()
		if (!PetPlaceholders.isStubPet(petId, SafeRepoConstants.petNumbers(coreRepo).keys, repoName)) return null
		return PetData(rarity, petId, 0.0, true)
	}

	fun configuredSources(): List<RepoSource> = RepoSources.parse(TConfig.repos).ifEmpty { RepoSources.DEFAULT }
	fun enabledSources(): List<RepoSource> = configuredSources().filter { it.enabled }

	fun getRepoRef(): String = enabledSources().joinToString(" > ") { it.ref }

	fun shouldLoadREI() = TConfig.enableREI

	init {
		ClientTickEvents.START_LEVEL_TICK.register { _ ->
			if (recentlyFailedToUpdateItemList && resyncRecipes()) recentlyFailedToUpdateItemList = false
		}
		if (TestUtil.isInTest) initialize()
	}
}
