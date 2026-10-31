// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.plus

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipInputStream
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientStartedEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.Client
import net.minecraft.network.chat.Component

object PlusRepoSource {
	@Config
	object TConfig : ManagedConfig("plus-data", Category.MISC) {
		val download by toggle("download") { true }
		val showSource by button("show-source") {
			Client.showChat(Component.literal("§ePlus data source: §f${describe()}"))
		}
	}

	enum class Source(val label: String) { DOWNLOADED("downloaded from GitHub"), CACHE("cached download"), BUNDLED("bundled snapshot") }

	const val ZIP_URL = "https://github.com/hannibal002/SkyHanni-REPO/archive/refs/heads/main.zip"
	val ALLOWED_HOSTS = setOf("github.com", "codeload.github.com", "objects.githubusercontent.com")
	const val MAX_ZIP_BYTES = 40L * 1024 * 1024
	const val MAX_EXTRACTED_BYTES = 80L * 1024 * 1024
	const val MAX_REDIRECTS = 5
	const val BUNDLED_BASE = "/assets/apex/plus-repo/"

	@Volatile var source: Source = Source.BUNDLED; private set
	@Volatile private var active: Map<String, ByteArray>? = null
	@Volatile var lastError: String? = null; private set

	@Volatile var generation = 0; private set

	val cacheFile: Path get() = Apex.DATA_DIR.resolve("plus-repo").resolve("community-repo-main.zip")

	fun describe(): String {
		val err = lastError ?: return source.label
		return "${source.label} (last download failed: $err)"
	}

	fun readText(path: String): String? {
		val key = safeEntryPath(path) ?: return null
		val loaded = active?.get(key)
		if (loaded != null) return loaded.decodeToString()
		val res = PlusRepoSource::class.java.getResourceAsStream(BUNDLED_BASE + key) ?: return null
		return res.use { it.readBytes().decodeToString() }
	}

	fun isAllowedUrl(uri: URI): Boolean {
		if (!"https".equals(uri.scheme, ignoreCase = true)) return false
		val host = uri.host ?: return false
		if (host.lowercase() !in ALLOWED_HOSTS) return false
		if (uri.userInfo != null) return false
		return uri.port == -1 || uri.port == 443
	}

	private val FORBIDDEN_CHARS = charArrayOf('\\', ':', '\u0000')

	fun safeEntryPath(name: String): String? {
		if (name.isEmpty() || name[0] == '/' || name.indexOfAny(FORBIDDEN_CHARS) >= 0) return null
		val kept = ArrayList<String>()
		for (seg in name.split('/')) {
			if (seg.isEmpty() || seg == ".") continue
			if (seg == "..") return null
			kept += seg
		}
		return if (kept.isEmpty()) null else kept.joinToString("/")
	}

	private class Budget(val cap: Long, val tooBig: () -> String) {
		var used = 0L

		fun drain(input: InputStream, bufferSize: Int): ByteArray {
			val sink = ByteArrayOutputStream()
			val buf = ByteArray(bufferSize)
			var n = input.read(buf)
			while (n >= 0) {
				used += n
				check(used <= cap, tooBig)
				sink.write(buf, 0, n)
				n = input.read(buf)
			}
			return sink.toByteArray()
		}
	}

	fun readZip(input: InputStream, maxTotal: Long = MAX_EXTRACTED_BYTES): Map<String, ByteArray> {
		val found = HashMap<String, ByteArray>()
		val budget = Budget(maxTotal) { "zip too large when extracted" }
		ZipInputStream(input).use { zip ->
			var entry = zip.nextEntry
			while (entry != null) {
				if (!entry.isDirectory) {
					val name = entry.name
					val clean = safeEntryPath(name) ?: error("unsafe zip entry: $name")
					val inRepo = clean.substringAfter('/', "")
					if (inRepo.startsWith("constants/") && inRepo.endsWith(".json")) found[inRepo] = budget.drain(zip, 8192)
				}
				entry = zip.nextEntry
			}
		}
		check(found.isNotEmpty()) { "no constants/ files in zip" }
		return found
	}

	fun readCapped(input: InputStream, cap: Long): ByteArray =
		Budget(cap) { "download larger than ${cap / 1024 / 1024} MB" }.drain(input, 16384)

	private val http: HttpClient by lazy {
		HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build()
	}

	private fun adopt(map: Map<String, ByteArray>, from: Source) {
		active = map
		source = from
		generation++
		Apex.logger.info("Community data: using ${from.label} (${map.size} files)")
	}

	@Subscribe
	fun onStarted(event: ClientStartedEvent) {
		CompletableFuture.runAsync {
			try {
				if (Files.isRegularFile(cacheFile)) adopt(Files.newInputStream(cacheFile).use { readZip(it) }, Source.CACHE)
			} catch (t: Throwable) {
				Apex.logger.warn("Community data: cache unusable", t)
			}
			if (TConfig.download) {
				try {
					download()
				} catch (t: Throwable) {
					lastError = t.message ?: t.javaClass.simpleName
					Apex.logger.warn("Community data: download failed, using ${source.label}", t)
				}
			}
		}
	}

	private fun store(bytes: ByteArray) {
		val dir = cacheFile.parent
		Files.createDirectories(dir)
		val part = Files.createTempFile(dir, "repo", ".zip.part")
		Files.write(part, bytes)
		Files.move(part, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}

	fun download() {
		var target = URI(ZIP_URL)
		for (hop in 0..MAX_REDIRECTS) {
			require(isAllowedUrl(target)) { "host not allowed: ${target.host}" }
			val request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(60)).header("User-Agent", "SkyBlock-Apex").GET().build()
			val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
			val status = response.statusCode()
			if (status == 200) {
				val bytes = response.body().use { readCapped(it, MAX_ZIP_BYTES) }
				val map = readZip(ByteArrayInputStream(bytes))
				store(bytes)
				lastError = null
				adopt(map, Source.DOWNLOADED)
				return
			}
			response.body().close()
			if (status !in 300..399) throw IllegalStateException("HTTP $status")
			val next = response.headers().firstValue("location").orElseThrow { IllegalStateException("redirect without location") }
			target = target.resolve(next)
		}
		throw IllegalStateException("too many redirects")
	}
}
