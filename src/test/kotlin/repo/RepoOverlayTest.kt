package dev.ashtonite365.apex.test.repo

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.repo.RepoOverlay
import dev.ashtonite365.apex.repo.RepoSource
import dev.ashtonite365.apex.repo.RepoSources

class RepoOverlayTest {

	@TempDir
	lateinit var tempDir: Path

	private fun write(root: Path, rel: String, text: String) {
		val p = root.resolve(rel); p.parent.createDirectories(); p.writeText(text)
	}

	@Test
	fun higherPriorityWinsAndPathsAreDeduped() {
		val tmp = Files.createTempDirectory(tempDir, "overlay")
		val apex = tmp.resolve("apex"); val core = tmp.resolve("core"); val out = tmp.resolve("merged")
		write(apex, "items/TOPAZ_GEM.json", "apex")
		write(apex, "mobs/scatha.json", "apex-mob")
		write(core, "items/TOPAZ_GEM.json", "core")
		write(core, "items/ONLY_CORE.json", "core-only")
		write(out, "items/STALE.json", "old merged file")
		val report = RepoOverlay.merge(listOf("apex" to apex, "core" to core, "missing" to tmp.resolve("nope")), out)
		assertEquals("apex", out.resolve("items/TOPAZ_GEM.json").readText())
		assertEquals("core-only", out.resolve("items/ONLY_CORE.json").readText())
		assertEquals("apex-mob", out.resolve("mobs/scatha.json").readText())
		assertFalse(out.resolve("items/STALE.json").exists())
		assertEquals(3, report.totalFiles)
		assertEquals(mapOf("apex" to 2, "core" to 1), report.filesWon)
		assertEquals("core", report.overridden["items/TOPAZ_GEM.json"])

		RepoOverlay.merge(listOf("core" to core, "apex" to apex), out)
		assertEquals("core", out.resolve("items/TOPAZ_GEM.json").readText())
	}

	@Test
	fun parsesPriorityList() {
		val l = RepoSources.parse("Ashtonite365/SkyBlockApex-REPO#master, !NotEnoughUpdates/NotEnoughUpdates-REPO\nbad entry, https://github.com/a/b.git#dev")
		assertEquals(listOf(
			RepoSource("Ashtonite365", "SkyBlockApex-REPO"),
			RepoSource("NotEnoughUpdates", "NotEnoughUpdates-REPO", enabled = false),
			RepoSource("a", "b", "dev"),
		), l)
		assertEquals(l, RepoSources.parse(RepoSources.format(l)))
	}

	@Test
	fun migratesOldDefaultButKeepsCustomRepo() {
		assertEquals(listOf(RepoSources.APEX), RepoSources.DEFAULT)
		assertFalse(RepoSources.DEFAULT_STRING.contains("NotEnoughUpdates"))
		fun repos(o: JsonObject) = RepoSources.parse((o["repos"] as JsonPrimitive).content)
		val old = JsonObject(mapOf("username" to JsonPrimitive("NotEnoughUpdates"), "reponame" to JsonPrimitive("NotEnoughUpdates-REPO"), "branch" to JsonPrimitive("prerelease")))
		val m = RepoSources.migrate(old)
		assertEquals(RepoSources.DEFAULT, repos(m))
		assertNull(m["username"])
		val custom = JsonObject(mapOf("username" to JsonPrimitive("me"), "reponame" to JsonPrimitive("r"), "branch" to JsonPrimitive("dev")))
		assertEquals(listOf(RepoSource("me", "r", "dev")), repos(RepoSources.migrate(custom)))

		val withCore = JsonObject(mapOf("repos" to JsonPrimitive("Ashtonite365/SkyBlockApex-REPO#master, me/extra#dev, NotEnoughUpdates/NotEnoughUpdates-REPO#master")))
		assertEquals(listOf(RepoSources.APEX, RepoSource("me", "extra", "dev")), repos(RepoSources.migrate(withCore)))
		val onlyCore = JsonObject(mapOf("repos" to JsonPrimitive("!NotEnoughUpdates/NotEnoughUpdates-REPO")))
		assertEquals(RepoSources.DEFAULT, repos(RepoSources.migrate(onlyCore)))
		val coreFork = JsonObject(mapOf("repos" to JsonPrimitive("x/y, NotEnoughUpdates/NotEnoughUpdates-REPO#dangerous")))
		assertTrue(RepoSources.migrate(coreFork) === coreFork)
		val already = JsonObject(mapOf("repos" to JsonPrimitive("x/y")))
		assertTrue(RepoSources.migrate(already) === already)
	}
}
