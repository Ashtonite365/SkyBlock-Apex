package dev.ashtonite365.apex.repo

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

data class RepoSource(val user: String, val name: String, val branch: String = "master", val enabled: Boolean = true) {
	val ref: String get() = "$user/$name#$branch"

	val folderKey: String get() = listOf(user, name, branch).joinToString("__") { it.replace(Regex("[^A-Za-z0-9._-]"), "_") }

	override fun toString(): String = (if (enabled) "" else "!") + ref
}

object RepoSources {
	const val APEX_USER = "Ashtonite365"
	const val APEX_REPO = "SkyBlockApex-REPO"
	const val CORE_USER = "NotEnoughUpdates"
	const val CORE_REPO = "NotEnoughUpdates-REPO"

	val APEX = RepoSource(APEX_USER, APEX_REPO, "master")

	private val OLD_CORE_DEFAULT = RepoSource(CORE_USER, CORE_REPO, "master")

	val DEFAULT: List<RepoSource> = listOf(APEX)
	val DEFAULT_STRING: String get() = format(DEFAULT)

	fun parse(text: String): List<RepoSource> =
		text.split(',', '\n', ';').mapNotNull { parseOne(it) }.distinctBy { it.ref.lowercase() }

	fun parseOne(raw: String): RepoSource? {
		var s = raw.trim()
		if (s.isEmpty()) return null
		val enabled = !s.startsWith("!")
		s = s.removePrefix("!").trim()
		s = s.removePrefix("https://github.com/")
		val branch = s.substringAfter('#', "").trim().ifEmpty { "master" }
		val path = s.substringBefore('#').trim().removeSuffix("/").removeSuffix(".git")
		val parts = path.split('/').filter { it.isNotBlank() }
		if (parts.size != 2) return null
		val ok = Regex("[A-Za-z0-9._-]+")
		if (!parts.all { ok.matches(it) } || !Regex("[A-Za-z0-9._/-]+").matches(branch)) return null
		return RepoSource(parts[0], parts[1], branch, enabled)
	}

	fun format(list: List<RepoSource>): String = list.joinToString(", ")

	fun isOldCoreDefault(src: RepoSource) =
		src.user.equals(CORE_USER, true) && src.name.equals(CORE_REPO, true) && src.branch in setOf("master", "prerelease")

	fun migrate(saved: JsonObject): JsonObject {
		val existing = (saved["repos"] as? JsonPrimitive)?.takeIf { it.isString }?.content
		if (existing != null) {
			val list = parse(existing)
			if (list.none(::isOldCoreDefault)) return saved
			val kept = list.filterNot(::isOldCoreDefault).ifEmpty { DEFAULT }
			return JsonObject(saved.toMutableMap().also { it["repos"] = JsonPrimitive(format(kept)) })
		}
		fun str(k: String) = (saved[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
		val user = str("username")
		val name = str("reponame")
		val branch = str("branch")
		if (user == null && name == null && branch == null) return saved
		val legacy = RepoSource(user ?: CORE_USER, name ?: CORE_REPO, branch ?: "master")
		val list = if (isOldCoreDefault(legacy)) DEFAULT
		else listOf(legacy.copy(branch = if (legacy.branch == "prerelease") "master" else legacy.branch))
		val out = saved.toMutableMap()
		out["repos"] = JsonPrimitive(format(list))
		out.remove("username"); out.remove("reponame"); out.remove("branch")
		return JsonObject(out)
	}
}

data class OverlayReport(val filesWon: Map<String, Int>, val overridden: Map<String, String>) {
	val totalFiles: Int get() = filesWon.values.sum()
}

object RepoOverlay {

	fun merge(layers: List<Pair<String, Path>>, target: Path): OverlayReport {
		val present = layers.filter { it.second.isDirectory() }
		val winner = LinkedHashMap<String, Pair<String, Path>>()
		val overridden = LinkedHashMap<String, String>()
		for ((label, root) in present) {
			Files.walk(root).use { stream ->
				stream.filter { it.isRegularFile() }.sorted().forEach { file ->
					val rel = root.relativize(file).toString().replace('\\', '/')
					if (rel.startsWith(".git/")) return@forEach
					val prev = winner[rel]
					if (prev == null) winner[rel] = label to file
					else overridden.putIfAbsent(rel, label)
				}
			}
		}
		val staging = target.resolveSibling(target.fileName.toString() + ".merging")
		staging.toFile().deleteRecursively()
		Files.createDirectories(staging)
		for ((rel, src) in winner) {
			val dest = staging.resolve(rel)
			if (!dest.normalize().startsWith(staging.normalize())) continue
			Files.createDirectories(dest.parent)
			Files.copy(src.second, dest, StandardCopyOption.REPLACE_EXISTING)
		}
		target.toFile().deleteRecursively()
		Files.move(staging, target)
		val won = present.associate { (label, _) -> label to winner.values.count { it.first == label } }
		return OverlayReport(won, overridden)
	}
}
