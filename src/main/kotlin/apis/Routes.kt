package dev.ashtonite365.apex.apis

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.MinecraftDispatcher
import dev.ashtonite365.apex.util.net.HttpUtil

object Routes {
	private const val MOWOJANG = "https://mowojang.matdoes.dev/"
	private val uuidByName = mutableMapOf<String, Deferred<UUID?>>()
	private val nameByUuid = mutableMapOf<UUID, Deferred<String?>>()

	suspend fun getPlayerNameForUUID(uuid: UUID): String? =
		memoised(nameByUuid, uuid, "could not get name for uuid $uuid") { found ->
			uuidByName[found.name] = CompletableDeferred(found.id)
			found.name
		}

	suspend fun getUUIDForPlayerName(name: String): UUID? =
		memoised(uuidByName, name, "could not get uuid for name $name") { found ->
			nameByUuid[found.id] = CompletableDeferred(found.name)
			found.id
		}

	private suspend fun <K : Any, V : Any> memoised(
		cache: MutableMap<K, Deferred<V?>>, key: K, failure: String, accept: (MowojangNameLookup) -> V,
	): V? {
		val pending = withContext(MinecraftDispatcher) {
			cache.getOrPut(key) {
				Apex.coroutineScope.async {
					val found = Faults.attempt(failure) { HttpUtil.request(MOWOJANG + key).forJson<MowojangNameLookup>().await() }.valueOrNull()
					found?.let { withContext(MinecraftDispatcher) { accept(it) } }
				}
			}
		}
		return pending.await()
	}
}
