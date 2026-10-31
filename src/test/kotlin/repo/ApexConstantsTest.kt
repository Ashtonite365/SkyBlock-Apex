package dev.ashtonite365.apex.test.repo

import io.github.moulberry.repo.NEURepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.RepoBundle
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.repo.SafeRepoConstants

class ApexConstantsTest {
	companion object {

		private var tmpRoot: Path? = null

		@JvmStatic @AfterAll
		fun cleanUp() {
			tmpRoot?.toFile()?.deleteRecursively()
			tmpRoot = null
		}
	}

	@Test
	fun missingConstantsDegradeToNoData() {
		val root = Files.createTempDirectory("consts").also { tmpRoot = it }
		val zst = root.resolve("repo.json.zst")
		zst.writeBytes(javaClass.getResourceAsStream("/apex-bundle/live/repo.json.zst")!!.readAllBytes())
		val dir = root.resolve("repo")
		RepoBundle.unpackZst(zst, dir)
		val repo = NEURepository.of(dir).also { it.reload() }

		val warps = SafeRepoConstants.warps(repo)
		val areas = SafeRepoConstants.areaNames(repo)
		val tps = SafeRepoConstants.teleporters(repo)
		val ranks = SafeRepoConstants.ranks(repo)
		val souls = SafeRepoConstants.fairySoulLocations(repo)
		val pets = SafeRepoConstants.petNumbers(repo)
		val essence = SafeRepoConstants.essenceCosts(repo)
		val blocks = SafeRepoConstants.tree(repo, "mining/blocks", "custom mining block hardness")
		val areasFile = SafeRepoConstants.file(repo, "mining/custom_mining_areas.json", "custom mining areas")
		val reforges = SafeRepoConstants.file(repo, "constants/reforges.json", "reforges")

		assertTrue(repo.constants.leveling != null)
		assertTrue(repo.constants.enchants != null)
		assertTrue(repo.constants.bonuses != null)

		val pld = repo.constants.petLevelingData
		val empty = dev.ashtonite365.apex.repo.ExpLadders.ExpLadder(listOf())
		assertTrue(empty.getPetLevel(12345.0).currentLevel == 1 && empty.getPetExpForLevel(50) == 0L)
		println("APEX-PETS levels=${pld.petExpCostForLevel?.size} offsets=${pld.petLevelStartOffset?.size}")

		val raw = listOf("misc", "garden", "skymart", "hotmlayout", "glacite_tunnel_waypoints", "museum", "trophyfish",
		                 "abiphone", "reforgestones", "islands", "bazaarstocks", "leveling", "pets", "enchants")
			.associateWith { RepoConstants.read(repo, it) }
		assertTrue(raw["leveling"] != null)
		assertTrue(raw["garden"] != null)
		assertNull(RepoConstants.read(repo, "no_such_constant_file"))
		println("APEX-CONSTANTS warps=${warps.size} areas=${areas.size} teleporters=${tps.size} ranks=${ranks.size} " +
			        "soulIslands=${souls.size} petnums=${pets.size} essence=${essence.size} miningBlocks=${blocks.size} " +
			        "miningAreas=${areasFile != null} reforges=${reforges != null} " +
			        "raw=${raw.mapValues { it.value != null }} missing=${SafeRepoConstants.missing.sorted()}")
	}
}
