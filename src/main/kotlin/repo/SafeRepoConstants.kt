package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.NEURepoFile
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.constants.Enchants
import io.github.moulberry.repo.constants.Islands
import io.github.moulberry.repo.constants.Leveling
import io.github.moulberry.repo.constants.RankData
import java.util.concurrent.ConcurrentHashMap
import dev.ashtonite365.apex.Apex

object SafeRepoConstants {
	private val reported = ConcurrentHashMap.newKeySet<String>()

	val missing: Set<String> get() = reported

	fun reportMissing(name: String, features: String) {
		if (reported.add(name))
			runCatching { Apex.logger.warn("Repo data '$name' is missing or empty; $features will show no data.") }
	}

	private fun <T> safe(name: String, features: String, f: () -> T?): T? {
		val v = try { f() } catch (e: Throwable) { null }
		if (v == null || (v is Collection<*> && v.isEmpty()) || (v is Map<*, *> && v.isEmpty())) reportMissing(name, features)
		return v
	}

	private fun current(): NEURepository? = runCatching { RepoManager.coreRepo }.getOrNull()

	fun islands(repo: NEURepository? = current()): Islands? =
		safe("constants/islands.json", "warps, /warp completion, island names, navigation") { repo?.constants?.islands }

	fun warps(repo: NEURepository? = current()): List<Islands.Warp> =
		safe("constants/islands.json#warps", "warp list and /warp completion") { islands(repo)?.warps } ?: listOf()

	fun areaNames(repo: NEURepository? = current()): Map<String, String> =
		safe("constants/islands.json#area_names", "island display names") { islands(repo)?.areaNames } ?: mapOf()

	fun teleporters(repo: NEURepository? = current()): List<Islands.Teleporter> =
		safe("constants/islands.json#teleporters", "cross-island navigation") { islands(repo)?.teleporters } ?: listOf()

	fun ranks(repo: NEURepository? = current()): Map<String, RankData> =
		safe("constants/misc.json#ranks", "player rank prefixes (profile viewer)") { repo?.constants?.misc?.ranks } ?: mapOf()

	fun fairySoulLocations(repo: NEURepository? = current()) =
		safe("constants/fairy_souls.json#locations", "fairy soul waypoints") { repo?.constants?.fairySouls?.soulLocations } ?: mapOf()

	fun petNumbers(repo: NEURepository? = current()) =
		safe("constants/petnums.json", "pet stat tooltips") { repo?.constants?.petNumbers } ?: mapOf()

	fun leveling(repo: NEURepository? = current()): Leveling? =
		safe("constants/leveling.json", "skill, slayer, HOTM and catacombs levels") { repo?.constants?.leveling }

	fun enchants(repo: NEURepository? = current()): Enchants? =
		safe("constants/enchants.json", "enchant data") { repo?.constants?.enchants }

	fun essenceCosts(repo: NEURepository? = current()) =
		safe("constants/essencecosts.json", "essence upgrade recipes") { repo?.constants?.essenceCost?.costs } ?: mapOf()

	fun tree(repo: NEURepository?, path: String, features: String): List<NEURepoFile> =
		safe(path, features) { repo?.tree(path)?.use { it.toList() } } ?: listOf()

	fun file(repo: NEURepository?, path: String, features: String): NEURepoFile? =
		safe(path, features) { repo?.file(path) }
}
