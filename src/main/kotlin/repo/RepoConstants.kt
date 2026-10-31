
package dev.ashtonite365.apex.repo

import com.google.gson.JsonObject
import io.github.moulberry.repo.NEURepository

object RepoConstants {
	private var repoRef: NEURepository? = null
	private val cache = mutableMapOf<String, JsonObject?>()

	fun get(name: String): JsonObject? {
		val repo = runCatching { RepoManager.coreRepo }.getOrNull() ?: return null
		if (repo !== repoRef) {
			repoRef = repo; cache.clear()
		}
		return cache.getOrPut(name) { read(repo, name) }
	}

	fun read(repo: NEURepository, name: String): JsonObject? {
		val o = runCatching { repo.file("constants/$name.json")?.json(JsonObject::class.java) }.getOrNull()
		if (o == null || o.size() == 0) {
			SafeRepoConstants.reportMissing("constants/$name.json", FEATURES[name] ?: "features using it")
			return null
		}
		return o
	}

	val FEATURES = mapOf(
		"misc" to "accessory bag upgrades/ignored talismans, minion helper, rank prefixes",
		"garden" to "garden milestones, crop ranking, visitor/plot progress",
		"skymart" to "garden tool copper costs",
		"hotmlayout" to "HOTM tree costs and profile viewer HOTM",
		"glacite_tunnel_waypoints" to "glacite tunnel gemstone waypoints",
		"museum" to "museum helper",
		"trophyfish" to "trophy fish fillet values",
	)

	fun price(id: dev.ashtonite365.apex.util.ItemId): Double? =
		HypixelStaticData.bazaarData[dev.ashtonite365.apex.util.bazaarStockOf(id)]?.quickStatus?.sellPrice
			?: HypixelStaticData.lowestBin[id]
}
