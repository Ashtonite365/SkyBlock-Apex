package dev.ashtonite365.apex.repo

import java.io.IOException
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.Apex.logger
import dev.ashtonite365.apex.util.iterate
import dev.ashtonite365.apex.util.net.HttpUtil

object RepoDownloadManager  {

	val repoSavedLocation: Path = Apex.DATA_DIR.resolve("repo-extracted")
	val reposRoot: Path = Apex.DATA_DIR.resolve("repos")

	val repoMetadataLocation: Path = Apex.DATA_DIR.resolve("loaded-repo-sha.txt")

	fun folderFor(source: RepoSource): Path = reposRoot.resolve(source.folderKey)
	private fun versionFileFor(source: RepoSource): Path = reposRoot.resolve(source.folderKey + ".version")

	fun savedVersionFor(source: RepoSource): String? =
		if (folderFor(source).isDirectory()) runCatching { versionFileFor(source).readText().trim() }.getOrNull()?.ifEmpty { null }
		else null

	private fun saveVersion(source: RepoSource, version: String) {
		reposRoot.createDirectories()
		versionFileFor(source).writeText(version)
	}

	val latestSavedVersionHash: String?
		get() = RepoManager.enabledSources().firstOrNull()?.let(::savedVersionFor)
			?: runCatching { repoMetadataLocation.readText().trim() }.getOrNull()

	val lastResult = mutableMapOf<String, String>()

	@Serializable
	private class GithubCommitsResponse(@Suppress("unused") val sha: String)

	private suspend fun requestLatestGithubSha(source: RepoSource, branch: String): String {
		val commitUrl = "https://api.github.com/repos/" + source.user + "/" + source.name + "/commits/" + branch
		val head: GithubCommitsResponse = HttpUtil.request(commitUrl).forJson<GithubCommitsResponse>().await()
		return head.sha
	}

	private suspend fun fetchText(url: String): String? {
		val res = HttpUtil.request(url).execute(HttpResponse.BodyHandlers.ofString()).await()
		return if (res.statusCode() == 200) res.body() else null
	}

	fun bundleUrl(source: RepoSource, branch: String, path: String): String =
		RepoManager.TConfig.bundleUrlTemplate
			.replace("{user}", source.user).replace("{repo}", source.name)
			.replace("{branch}", branch).replace("{path}", path)

	suspend fun <T> withTempDownload(prefix: String, suffix: String, fill: suspend (Path) -> Unit, use: suspend (Path) -> T): T {
		val tmp = withContext(IO) { Files.createTempFile(prefix, suffix) }
		try {
			fill(tmp)
			return use(tmp)
		} finally {
			withContext(IO) { runCatching { tmp.deleteIfExists() } }
		}
	}

	private suspend fun downloadTo(url: String, target: Path) = withContext(IO) {
		val res = HttpUtil.request(url).execute(HttpResponse.BodyHandlers.ofInputStream()).await()
		res.body().use { input ->
			if (res.statusCode() != 200) throw IOException("HTTP ${res.statusCode()} for $url")
			Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
				.use { input.copyTo(it) }
		}
	}

	private fun downloadBlocking(url: String, target: Path) {
		val res = HttpUtil.request(url).execute(HttpResponse.BodyHandlers.ofInputStream()).join()
		res.body().use { input ->
			if (res.statusCode() != 200) throw IOException("HTTP ${res.statusCode()} for $url")
			Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
				.use { input.copyTo(it) }
		}
	}

	suspend fun updateSource(source: RepoSource, force: Boolean, branchOverride: String? = null): Boolean {
		val branch = branchOverride ?: source.branch
		val layout = BundleLayout(RepoManager.TConfig.bundleManifest, RepoManager.TConfig.bundleFile)
		val manifestText = runCatching { fetchText(bundleUrl(source, branch, layout.manifestPath)) }.getOrNull()
		val dir = folderFor(source)
		if (manifestText != null) {
			val manifest = BundleManifest.parse(manifestText)
			val version = "bundle:" + manifest.cacheKey
			if (!force && version == savedVersionFor(source)) {
				lastResult[source.ref] = "bundle up to date (${manifest.version})"; return false
			}
			logger.info("Downloading repo bundle ${source.ref} ${manifest.version}" +
				(manifest.apexPackAlternate?.let { " (apex-pack ${it.bundle})" } ?: ""))
			return withContext(IO) {
				val t0 = System.nanoTime()
				val installed = try {
					RepoBundle.install(manifest, layout.bundlePath, dir) { path, into -> downloadBlocking(bundleUrl(source, branch, path), into) }
				} catch (e: BundleException) {
					logger.error("Repo ${source.ref}: ${e.message}; keeping the previous cache")
					lastResult[source.ref] = "sha256 mismatch, kept previous cache"
					return@withContext false
				}
				installed.fallbackReason?.let { logger.warn("Repo ${source.ref}: apex-pack alternate not used ($it); used the zstd bundle") }
				saveVersion(source, version)
				lastResult[source.ref] = "bundle ${manifest.version} (${installed.format}): ${installed.files} files in ${(System.nanoTime() - t0) / 1_000_000} ms" +
					(manifest.itemCount?.let { ", manifest items $it" } ?: "")
				true
			}
		}
		val sha = requestLatestGithubSha(source, branch)
		val version = "git:$sha"
		if (!force && version == savedVersionFor(source)) {
			lastResult[source.ref] = "archive up to date (${sha.take(8)})"; return false
		}

		val url = "https://github.com/${source.user}/${source.name}/archive/$sha.zip"
		logger.info("Downloading repo archive ${source.ref} at $sha from $url")
		return withTempDownload("apex-repo", ".zip", { downloadTo(url, it) }) { zip ->
			withContext(IO) {
				val staging = dir.resolveSibling(dir.fileName.toString() + ".extracting")
				staging.toFile().deleteRecursively()
				try {
					extractZip(zip, staging)
					dir.toFile().deleteRecursively()
					Files.move(staging, dir)
				} finally {
					staging.toFile().deleteRecursively()
				}
				saveVersion(source, version)
				lastResult[source.ref] = "archive ${sha.take(8)}"
				true
			}
		}
	}

	suspend fun downloadUpdate(force: Boolean, branch: String? = null): Boolean = withContext(CoroutineName("Repo Update Check")) {
			val sources: List<RepoSource> = RepoManager.enabledSources()
			var changed = false
			var anyOk = false
			sources.forEachIndexed { idx, source ->
				try {

					if (updateSource(source, force, if (idx == 0) branch else null)) changed = true
					anyOk = true
				} catch (e: kotlinx.coroutines.CancellationException) {
					throw e
				} catch (e: Throwable) {
					logger.warn("Repo ${source.ref} update failed (${e.javaClass.simpleName}: ${e.message}); keeping its cache")
					lastResult[source.ref] = "update failed: ${e.javaClass.simpleName}"
				}
			}
			if (!anyOk && sources.isNotEmpty()) throw IOException("no repo could be updated")
			if (changed || !repoSavedLocation.resolve("items").isDirectory()) mergeLayers(sources) else false
		}

	fun mergeLayers(sources: List<RepoSource> = RepoManager.enabledSources()): Boolean {
		val layers = sources.map { it.ref to folderFor(it) }.filter { it.second.isDirectory() }
		if (layers.isEmpty()) return false
		val report = RepoOverlay.merge(layers, repoSavedLocation)
		logger.info("Merged ${report.totalFiles} repo files: ${report.filesWon}; ${report.overridden.size} paths overridden by a higher priority")
		sources.firstOrNull()?.let(::savedVersionFor)?.let { runCatching { repoMetadataLocation.writeText(it) } }
		return true
	}

	private fun extractZip(zipFile: Path, target: Path) {
		target.createDirectories()

		val zipSlip = "SkyBlock Apex detected an invalid zip file. This is a potential security risk, please report this to the SkyBlock Apex maintainer."
		ZipInputStream(zipFile.inputStream()).use { zip ->
			var next = zip.nextEntry
			while (next != null) {
				if (!next.isDirectory) {
					val out = target.resolve(next.name.substringAfter('/', ""))
					if (out.iterate { p -> p.parent }.none { it == target }) {
						logger.error(zipSlip)
						throw RuntimeException(zipSlip)
					}
					out.parent.createDirectories()
					out.outputStream().use { sink -> zip.copyTo(sink) }
				}
				next = zip.nextEntry
			}
		}
	}
}
