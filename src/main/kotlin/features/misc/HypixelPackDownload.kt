/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: fetch, verify and cache Hypixel's official SkyBlock Resource Pack from Hypixel's public API
 * (all builds; no build bundles the pack). Pure logic (no Minecraft calls), unit tested; DownloadedHypixelPack is the game side.
 * The pack is (C) Hypixel Inc. under its own licence; Apex never ships or re-distributes it, the player's game downloads it.
 */
package dev.ashtonite365.apex.features.misc

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

object HypixelPackApi {

	const val PACKS_URL = "https://api.hypixel.net/v2/resources/packs"
	const val PACK_ID = "SkyBlock"

	const val PACK_FORMAT = 97

	val DOWNLOAD_HOSTS = setOf("resourcepacks.hypixel.net")

	const val HYPIXEL_URL = "https://hypixel.net/"

	data class PackVersion(val packFormat: Int, val sha1: String, val url: String, val deployId: String?)

	private val SHA1 = Regex("^[0-9a-fA-F]{40}$")

	fun allowedDownload(url: String): Boolean = runCatching {
		val u = URI(url.trim())
		u.scheme == "https" && u.host?.lowercase() in DOWNLOAD_HOSTS
	}.getOrDefault(false)

	fun parse(body: String, packId: String = PACK_ID, packFormat: Int = PACK_FORMAT): PackVersion? = runCatching {
		val root = JsonParser.parseString(body).asJsonObject
		if (root.get("success")?.asBoolean == false) return null
		val pack = root.getAsJsonArray("packs").map { it.asJsonObject }.firstOrNull { it.get("id")?.asString == packId } ?: return null
		val v = pack.getAsJsonArray("versions").map { it.asJsonObject }.firstOrNull { it.get("packFormat")?.asInt == packFormat } ?: return null
		val hash = v.get("hash").asString.trim()
		val url = v.get("url").asString.trim()
		if (!SHA1.matches(hash) || !allowedDownload(url)) return null
		PackVersion(packFormat, hash.lowercase(), url, (pack.get("deployId") as? com.google.gson.JsonPrimitive)?.asString)
	}.getOrNull()
}

object PackHash {
	fun sha1(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))
	fun sha1(file: Path): String {
		val md = MessageDigest.getInstance("SHA-1")
		Files.newInputStream(file).use { inp ->
			val buf = ByteArray(64 * 1024)
			while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) }
		}
		return hex(md.digest())
	}
	fun matches(actual: String, expected: String) = actual.equals(expected.trim(), ignoreCase = true)
	private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}

class PackHashMismatch(val expected: String, val actual: String) : IOException("SHA-1 mismatch: expected $expected, got $actual")

class HypixelPackCache(val dir: Path, val packFormat: Int = HypixelPackApi.PACK_FORMAT) {
	val zip: Path get() = dir.resolve("hypixel_skyblock-$packFormat.zip")
	val hashFile: Path get() = dir.resolve("hypixel_skyblock-$packFormat.zip.sha1")

	data class Cached(val zip: Path, val sha1: String)

	fun verified(): Cached? = runCatching {
		if (!Files.isRegularFile(zip) || !Files.isRegularFile(hashFile)) return null
		val expected = Files.readString(hashFile).trim()
		val actual = PackHash.sha1(zip)
		if (expected.isEmpty() || !PackHash.matches(actual, expected)) null else Cached(zip, actual)
	}.getOrNull()

	fun store(bytes: ByteArray, expectedSha1: String): Cached {
		val actual = PackHash.sha1(bytes)
		if (!PackHash.matches(actual, expectedSha1)) throw PackHashMismatch(expectedSha1, actual)
		Files.createDirectories(dir)
		atomicWrite(zip, bytes)
		atomicWrite(hashFile, (actual + "\n").toByteArray())
		return Cached(zip, actual)
	}

	fun extract(cached: Cached, description: String): Path {
		val out = dir.resolve("hypixel_skyblock-$packFormat-${cached.sha1}")
		val done = out.resolve(".apex-complete")
		if (Files.isRegularFile(done)) return out
		var kept = 0
		ZipFile(cached.zip.toFile()).use { z ->
			for (e in z.entries()) {
				if (e.isDirectory) continue
				val name = e.name
				if (!safeEntry(name) || !BuiltinHypixelPack.serves(name, true)) continue
				val target = out.resolve(name).normalize()
				if (!target.startsWith(out)) continue
				Files.createDirectories(target.parent)
				val bytes = z.getInputStream(e).use { it.readAllBytes() }
				atomicWrite(target, if (name == "pack.mcmeta") withDescription(bytes, description) else bytes)
				kept++
			}
		}
		if (!Files.isRegularFile(out.resolve("pack.mcmeta"))) throw IOException("downloaded pack has no pack.mcmeta")
		if (kept < 2) throw IOException("downloaded pack has nothing on the keep-list")
		atomicWrite(done, "$kept\n".toByteArray())
		return out
	}

	companion object {

		fun safeEntry(name: String): Boolean =
			name.isNotEmpty() && !name.startsWith("/") && '\\' !in name && ':' !in name &&
				name.split('/').none { it == ".." || it == "." || it.isEmpty() }

		fun withDescription(mcmeta: ByteArray, description: String): ByteArray = runCatching {
			val root = JsonParser.parseString(mcmeta.decodeToString()).asJsonObject
			(root.getAsJsonObject("pack") ?: JsonObject().also { root.add("pack", it) }).addProperty("description", description)
			root.toString().toByteArray()
		}.getOrDefault(mcmeta)

		fun atomicWrite(target: Path, bytes: ByteArray) {
			Files.createDirectories(target.parent)
			val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
			Files.write(tmp, bytes)
			runCatching { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
				.getOrElse { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING) }
		}
	}
}

class HypixelPackFetcher(
	private val cache: HypixelPackCache,
	private val http: Http,
	private val notifier: Notifier,
	private val description: String = NOTICE,
) {

	fun interface Http { fun get(url: String, maxBytes: Long): ByteArray }

	interface Notifier {

		fun ready(root: Path, downloaded: Boolean) {}

		fun unavailable(reason: String)
	}

	sealed interface Result {
		data class Fresh(val root: Path, val downloaded: Boolean) : Result
		data class FromCache(val root: Path, val reason: String) : Result
		data class Unavailable(val reason: String) : Result
	}

	fun run(): Result {
		val failure: String = try {
			val meta = HypixelPackApi.parse(http.get(HypixelPackApi.PACKS_URL, MAX_API_BYTES).decodeToString())
				?: throw IOException("API has no ${HypixelPackApi.PACK_ID} pack for format ${cache.packFormat}")
			val current = cache.verified()
			val (cached, downloaded) = if (current != null && PackHash.matches(current.sha1, meta.sha1)) current to false
			else cache.store(http.get(meta.url, MAX_PACK_BYTES), meta.sha1) to true
			val root = cache.extract(cached, description)
			notifier.ready(root, downloaded)
			return Result.Fresh(root, downloaded)
		} catch (e: Exception) {
			"${e.javaClass.simpleName}: ${e.message}"
		}
		val cached = cache.verified()
		if (cached != null) {
			val root = runCatching { cache.extract(cached, description) }.getOrNull()
			if (root != null) { notifier.ready(root, false); return Result.FromCache(root, failure) }
		}
		notifier.unavailable(failure)
		return Result.Unavailable(failure)
	}

	companion object {
		const val MAX_API_BYTES = 1L shl 20
		const val MAX_PACK_BYTES = 256L shl 20
		const val NOTICE = "Hypixel SkyBlock Resource Pack, (C) Hypixel Inc., under its own licence (see LICENSE). Downloaded from Hypixel, not distributed by SkyBlock Apex."
	}
}

class RetryGate(private val minIntervalMs: Long = 30_000, private val clock: () -> Long = System::currentTimeMillis) {
	private var last = Long.MIN_VALUE
	@Synchronized fun tryAcquire(): Boolean {
		val now = clock()
		if (last != Long.MIN_VALUE && now - last < minIntervalMs) return false
		last = now; return true
	}
}

class PackNoticePolicy {
	enum class Show { NONE, NOTICE, UNAVAILABLE }

	private var unavailableShown = false

	@Synchronized fun onResult(result: HypixelPackFetcher.Result): Show = when (result) {
		is HypixelPackFetcher.Result.Fresh -> if (result.downloaded) Show.NOTICE else Show.NONE
		is HypixelPackFetcher.Result.FromCache -> Show.NONE
		is HypixelPackFetcher.Result.Unavailable -> if (unavailableShown) Show.NONE else { unavailableShown = true; Show.UNAVAILABLE }
	}
}
