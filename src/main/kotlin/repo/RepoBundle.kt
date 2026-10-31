package dev.ashtonite365.apex.repo

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import dev.ashtonite365.apex.compress.ApexPack
import dev.ashtonite365.apex.compress.Decompress

data class BundleLayout(
	val manifestPath: String = "manifest.json",
	val bundlePath: String = "repo.json.zst",
)

data class BundleAlternate(val bundle: String, val format: String, val formatVersion: Int?, val bytes: Long?, val sha256: String?)

data class BundleManifest(val version: String?, val sha256: String?, val itemCount: Int?, val bundlePath: String?,
                          val fileCount: Int? = null, val recipeTotal: Int? = null, val recipeCounts: Map<String, Int> = mapOf(),
                          val alternates: List<BundleAlternate> = listOf()) {
	companion object {
		const val APEX_PACK_FORMAT = "apex-pack"

		private fun alternates(o: JsonObject): List<BundleAlternate> =
			(o.get("alternates") as? com.google.gson.JsonArray)?.mapNotNull { el ->
				val a = el as? JsonObject ?: return@mapNotNull null
				val bundle = a.firstString("bundle", "file", "path") ?: return@mapNotNull null
				val format = a.firstString("format") ?: return@mapNotNull null
				BundleAlternate(bundle, format, a.firstString("format_version")?.toDoubleOrNull()?.toInt(),
					a.firstString("bytes")?.toDoubleOrNull()?.toLong(), a.firstString("sha256", "hash")?.lowercase())
			} ?: listOf()

		private fun JsonObject.firstString(vararg keys: String) =
			keys.firstNotNullOfOrNull { k -> get(k)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }

		fun parse(text: String): BundleManifest {
			val o = JsonParser.parseString(text).asJsonObject
			return BundleManifest(
				o.firstString("version", "sha", "commit"),
				o.firstString("sha256", "hash", "bundle_sha256")?.lowercase(),
				o.firstString("item_count", "items", "itemCount")?.toDoubleOrNull()?.toInt(),
				o.firstString("bundle", "file", "path"),
				o.firstString("file_count", "files")?.toDoubleOrNull()?.toInt(),
				o.firstString("recipe_total")?.toDoubleOrNull()?.toInt(),
				(o.get("recipe_counts") as? JsonObject)?.entrySet()
					?.mapNotNull { (k, v) -> v.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.let { k to it.toInt() } }
					?.toMap() ?: mapOf(),
				alternates(o),
			)
		}
	}

	val cacheKey: String get() = listOfNotNull(version, sha256).joinToString("@").ifEmpty { "unknown" }

	val apexPackAlternate: BundleAlternate?
		get() = alternates.firstOrNull {
			it.format == APEX_PACK_FORMAT && (it.formatVersion ?: 1) == 1 && it.sha256 != null && RepoBundle.safeRelative(it.bundle) != null
		}
}

class BundleException(message: String) : Exception(message)

object RepoBundle {
	val META_KEYS = setOf("format", "format_version", "version", "meta", "manifest", "build_date")

	const val FORMAT_TAG = "apex-repo-bundle"

	fun sha256(file: Path): String {
		val md = MessageDigest.getInstance("SHA-256")
		file.inputStream().use { input ->
			val buf = ByteArray(1 shl 16)
			while (true) {
				val n = input.read(buf); if (n < 0) break
				md.update(buf, 0, n)
			}
		}
		return md.digest().joinToString("") { "%02x".format(it) }
	}

	fun verify(file: Path, manifest: BundleManifest) =
		verifySha(file, manifest.sha256 ?: throw BundleException("manifest has no sha256"))

	fun verifySha(file: Path, expected: String) {
		val actual = sha256(file)
		if (!actual.equals(expected, ignoreCase = true))
			throw BundleException("bundle sha256 mismatch: expected $expected, got $actual")
	}

	data class Installed(val files: Int, val format: String, val fallbackReason: String?)

	fun install(manifest: BundleManifest, defaultBundlePath: String, target: Path, tempDir: Path? = null,
	            download: (relativePath: String, into: Path) -> Unit): Installed {
		fun temp(suffix: String) = if (tempDir != null) Files.createTempFile(tempDir, "apex-repo", suffix) else Files.createTempFile("apex-repo", suffix)
		var reason: String? = null
		val alt = manifest.apexPackAlternate
		if (alt != null) {
			val tmp = temp(".json.apack")
			try {
				download(alt.bundle, tmp)
				if (alt.bytes != null && Files.size(tmp) != alt.bytes) throw BundleException("apex-pack size ${Files.size(tmp)} != ${alt.bytes}")
				verifySha(tmp, alt.sha256!!)
				val head = tmp.inputStream().use { it.readNBytes(4) }
				if (!ApexPack.isApexPack(head, 0, head.size)) throw BundleException("alternate is not an apex pack")
				return Installed(unpackCompressed(tmp, target), BundleManifest.APEX_PACK_FORMAT, null)
			} catch (e: Exception) {
				reason = "${e.javaClass.simpleName}: ${e.message}"
			} finally {
				runCatching { tmp.deleteIfExists() }
			}
		}
		val tmp = temp(".json.zst")
		try {
			download(manifest.bundlePath ?: defaultBundlePath, tmp)
			verify(tmp, manifest)
			return Installed(unpackCompressed(tmp, target), "zst", reason)
		} finally {
			runCatching { tmp.deleteIfExists() }
		}
	}

	fun safeRelative(path: String): String? {
		val p = path.replace('\\', '/').trimStart('/')
		if (p.isEmpty() || p.split('/').any { it == ".." || it.isEmpty() } || p.contains(':')) return null
		return p
	}

	fun unpackCompressed(bundle: Path, target: Path): Int = bundle.inputStream().use { unpack(Decompress.open(it.buffered()), target) }

	fun unpackZst(bundle: Path, target: Path): Int = unpackCompressed(bundle, target)

	fun unpack(json: InputStream, target: Path): Int {
		val staging = target.resolveSibling(target.fileName.toString() + ".unpacking")
		staging.toFile().deleteRecursively()
		Files.createDirectories(staging)
		try {
			var count = 0
			JsonReader(InputStreamReader(json, Charsets.UTF_8)).use { r ->
				r.beginObject()
				while (r.hasNext()) {
					val key = r.nextName()
					if (key == "files" && r.peek() == JsonToken.BEGIN_OBJECT) {
						r.beginObject()
						while (r.hasNext()) { writeEntry(staging, r.nextName(), r); count++ }
						r.endObject()
					} else if (key == "format" && r.peek() == JsonToken.STRING) {
						val tag = r.nextString()
						if (tag != FORMAT_TAG) throw BundleException("unknown bundle format '$tag' (expected $FORMAT_TAG)")
					} else if (key in META_KEYS) {
						r.skipValue()
					} else {
						writeEntry(staging, key, r); count++
					}
				}
				r.endObject()
			}
			if (count == 0) throw BundleException("bundle contains no files")
			target.toFile().deleteRecursively()
			Files.move(staging, target)
			return count
		} catch (e: Throwable) {
			staging.toFile().deleteRecursively()
			throw e
		}
	}

	private fun writeEntry(root: Path, rawPath: String, r: JsonReader) {
		val rel = safeRelative(rawPath) ?: throw BundleException("unsafe path in bundle: $rawPath")
		val dest = root.resolve(rel).normalize()
		if (!dest.startsWith(root.normalize())) throw BundleException("unsafe path in bundle: $rawPath")
		Files.createDirectories(dest.parent)
		when (r.peek()) {
			JsonToken.STRING -> Files.writeString(dest, r.nextString())
			else -> {
				val el: JsonElement = JsonParser.parseReader(r)
				val obj = el as? JsonObject
				val b64 = (obj?.get("base64") ?: obj?.get("b64"))?.takeIf { it.isJsonPrimitive }?.asString
				if (b64 != null) Files.write(dest, Base64.getDecoder().decode(b64))
				else Files.writeString(dest, el.toString())
			}
		}
	}
}
