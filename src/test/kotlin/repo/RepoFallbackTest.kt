package dev.ashtonite365.apex.test.repo

import java.nio.file.Path
import io.github.moulberry.repo.NEURepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.coroutines.runBlocking
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.itemsSafe

class RepoFallbackTest {

	@TempDir
	lateinit var tempDir: Path

	@Test
	fun unloadedRepoReadsAsEmptyNotNull() {
		val dir = java.nio.file.Files.createTempDirectory(tempDir, "empty-repo")
		val repo = NEURepository.of(dir)
		Assertions.assertNull(repo.items.items)
		Assertions.assertEquals(0, repo.items.itemsSafe.size)
	}

	@Test
	fun failedUpdateNeverSkipsTheReload() = runBlocking {
		RepoManager.initialize()
		val before = RepoManager.coreRepo.items.itemsSafe.size
		Assertions.assertTrue(before > 0)

		val ok = RepoManager.updateThenReload { throw java.net.ConnectException("github unreachable") }
		Assertions.assertFalse(ok)
		Assertions.assertEquals(before, RepoManager.coreRepo.items.itemsSafe.size)
		Assertions.assertTrue(RepoManager.updateThenReload { false })
		Assertions.assertTrue(RepoManager.getAllRecipes().any())
	}
}
