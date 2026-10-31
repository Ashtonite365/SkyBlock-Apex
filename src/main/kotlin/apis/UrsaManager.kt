package dev.ashtonite365.apex.apis

import java.net.URI
import java.net.http.HttpHeaders
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.jvm.optionals.getOrNull
import net.minecraft.client.Minecraft
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.net.HttpUtil

object UrsaManager {
	private const val HOST = "ursa.notenoughupdates.org"

	private class Session(val token: String, val expires: Instant, val host: String) {
		fun usableFor(target: String) = target == host && Instant.now().plusSeconds(60) < expires
	}

	private var session: Session? = null
	private val handshake = Mutex()

	private fun reusableSession(host: String) = session?.takeIf { it.usableFor(host) }

	internal fun urlFor(host: String, path: List<String>): URI =
		URI.create(path.joinToString("/", prefix = "https://$host/") { segment -> segment.trim('/') })

	suspend fun <T> request(path: List<String>, bodyHandler: HttpResponse.BodyHandler<T>): T {
		reusableSession(HOST)?.let { return send(path, bodyHandler, it) }

		return handshake.withLock { send(path, bodyHandler, reusableSession(HOST)) }
	}

	private suspend fun <T> send(path: List<String>, bodyHandler: HttpResponse.BodyHandler<T>, known: Session?): T {
		val request = HttpUtil.request(urlFor(HOST, path))
		if (known != null) request.header("x-ursa-token", known.token) else proveAccount(request)
		val response = request.execute(bodyHandler).await()
		adoptToken(response.headers())
		if (response.statusCode() != 200) Apex.logger.error("Failed to contact ursa minor: ${response.statusCode()}")
		return response.body()
	}

	private suspend fun proveAccount(request: HttpUtil.Request) = withContext(Dispatchers.IO) {
		val user = Minecraft.getInstance().user
		val joinId = UUID.randomUUID().toString()
		Minecraft.getInstance().services().sessionService.joinServer(user.profileId, user.accessToken, joinId)
		request.header("x-ursa-username", user.name)
		request.header("x-ursa-serverid", joinId)
	}

	private fun adoptToken(headers: HttpHeaders) {
		val token = headers.firstValue("x-ursa-token").getOrNull() ?: return
		val expiry = headers.firstValueAsLong("x-ursa-expires").takeIf { it.isPresent }?.let { Instant.ofEpochMilli(it.asLong) }
		session = Session(token, expiry ?: Instant.now().plus(Duration.ofMinutes(55)), HOST)
	}
}
