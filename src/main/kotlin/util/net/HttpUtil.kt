// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.net

import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import dev.ashtonite365.apex.Apex

object HttpUtil {

	val httpClient: HttpClient = with(HttpClient.newBuilder()) {
		followRedirects(HttpClient.Redirect.NORMAL)
		build()
	}

	data class Request(val request: HttpRequest.Builder) {

		fun header(name: String, value: String) { request.header(name, value) }

		fun <T> execute(handler: HttpResponse.BodyHandler<T>): CompletableFuture<HttpResponse<T>> =
			httpClient.sendAsync(request.build(), handler)

		fun <T> forBody(handler: HttpResponse.BodyHandler<T>): CompletableFuture<T> =
			execute(handler).thenApply { response -> response.body() }

		fun forInputStream(): CompletableFuture<InputStream> = forBody(HttpResponse.BodyHandlers.ofInputStream())

		fun <T> forJson(strategy: DeserializationStrategy<T>): CompletableFuture<T> = forBody(jsonBodyHandler(strategy))

		inline fun <reified T> forJson(): CompletableFuture<T> = forJson(serializer<T>())
	}

	private val userAgent get() = "Apex/${Apex.version.friendlyString}"

	fun request(uri: URI): Request {
		val builder = HttpRequest.newBuilder(uri)
		builder.header("user-agent", userAgent)
		return Request(builder)
	}

	fun request(url: URL): Request = url.toURI().let(::request)

	fun request(url: String): Request = URI.create(url).let(::request)

	fun <T> jsonBodyHandler(strategy: DeserializationStrategy<T>) =
		HttpResponse.BodyHandler<T> { _ ->
			HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofInputStream()) { stream ->
				Apex.json.decodeFromStream(strategy, stream)
			}
		}
}
