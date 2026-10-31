// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.repo

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.EnchantedBookNames
import dev.ashtonite365.apex.repo.ExpLadders
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.RepoDownloadManager
import dev.ashtonite365.apex.repo.RepoModResourcePack
import dev.ashtonite365.apex.test.ours.OursGolden

class RepoExtrasEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun <T> attempt(block: () -> T): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName + ":" + e.message
	}

	@Test
	fun romanNumerals() {
		OursGolden.check("repo-roman", (-3..4100).map { "$it\t${EnchantedBookNames.roman(it)}" })
	}

	@Test
	fun ladderMaths() {
		val out = mutableListOf<String>()
		val ladders = listOf(emptyList(), listOf(100L), listOf(100L, 250L, 1000L), (1L..120L).map { it * it * 37 }, listOf(16_777_217L, 3L, 5L))
		for (ladder in ladders) {
			val l = ExpLadders.ExpLadder(ladder)
			out += "ladder\t$ladder\t${l.cumulativeLevelCost}"
			for (exp in listOf(-5.0, 0.0, 0.5, 99.9, 100.0, 349.0, 350.0, 1350.0, 1e6, 1.7e7, 2e9))
				out += "level\t$exp\t${attempt { l.getPetLevel(exp).let { "$it|${it.levelText}|${it.percentageToNextLevel}|${it.percentageToMaxLevel}" } }}"
			for (lv in -1..125) out += "exp\t$lv\t${l.getPetExpForLevel(lv)}"
		}
		OursGolden.check("repo-ladder-maths", out)
	}

	@Test
	fun apiPayloads() {
		val bazaar = """{"product_id":"ENCHANTED_DIAMOND","quick_status":{"sellPrice":1.5,"sellVolume":10,"sellMovingWeek":20,
			"sellOrders":3,"buyPrice":2.5,"buyVolume":11,"buyMovingWeek":21,"buyOrders":4},
			"sell_summary":[{"amount":5,"pricePerUnit":1.4,"orders":1}],"buy_summary":[{"amount":6,"pricePerUnit":2.6}]}"""
		val items = """{"success":true,"items":[{"id":"A","npc_sell_price":3.0},{"id":"B","skin":{"value":"v"}}]}"""
		val out = listOf(
			attempt { Apex.json.decodeFromString(HypixelStaticData.BazaarData.serializer(), bazaar) },
			attempt { Apex.json.decodeFromString(HypixelStaticData.ItemsResponse.serializer(), items) },
			attempt { Apex.json.decodeFromString(HypixelStaticData.BazaarStatus.serializer(), "{}") },
			attempt { Apex.json.encodeToString(HypixelStaticData.BazaarData.serializer(), Apex.json.decodeFromString(HypixelStaticData.BazaarData.serializer(), bazaar)) },
		)
		OursGolden.check("repo-api-payloads", out)
	}

	private fun tree(): Path {
		val root = Files.createTempDirectory("apex-ours-pack")
		for (p in listOf("a.txt", "dir/b.png", "dir/b.png.mcmeta", "dir/sub/c.json", "Bad Name.txt")) {
			val f = root.resolve(p)
			Files.createDirectories(f.parent)
			f.writeText("content of $p")
		}
		return root
	}

	@Test
	fun resourcePackView() {
		val root = tree()
		try {
			val pack = RepoModResourcePack(root)
			val out = mutableListOf<String>()
			for (t in PackType.entries) out += "ns\t$t\t${pack.getNamespaces(t)}"
			for (segs in listOf(arrayOf("a.txt"), arrayOf("dir", "b.png"), arrayOf("dir"), arrayOf("missing"), arrayOf("..", "x")))
				out += "file\t${segs.toList()}\t${attempt { pack.getFile(segs)?.let { root.relativize(it) } }}"
			out += "root\t${attempt { pack.getRootResource("a.txt")?.get()?.readAllBytes()?.decodeToString() }}"
			for (t in PackType.entries) for (id in listOf("neurepo:a.txt", "neurepo:dir/b.png", "minecraft:a.txt", "neurepo:nope"))
				out += "res\t$t\t$id\t${attempt { pack.getResource(t, Identifier.parse(id))?.get()?.readAllBytes()?.decodeToString() }}"
			for (t in PackType.entries) for (ns in listOf("neurepo", "minecraft")) for (prefix in listOf("", "dir", "dir/sub", "none")) {
				val seen = mutableListOf<String>()
				attempt { pack.listResources(t, ns, prefix) { id, _ -> seen += id.toString() } }
				out += "list\t$t\t$ns\t$prefix\t${seen.sorted()}"
			}
			out += "loc\t${pack.location().id()}\t${pack.location().title().string}\t${pack.location().source()}"
			out += "overlay\t${root.relativize((pack.createOverlay("dir") as RepoModResourcePack).basePath)}"
			for (type in listOf(net.minecraft.server.packs.metadata.pack.PackMetadataSection.CLIENT_TYPE, net.minecraft.server.packs.metadata.pack.PackMetadataSection.FALLBACK_TYPE)) out += "meta\t${attempt { pack.getMetadataSection(type) }}"
			OursGolden.check("repo-pack-view", out)
		} finally {
			root.toFile().deleteRecursively()
		}
	}

	private fun zip(at: Path, entries: List<Pair<String, String?>>) {
		ZipOutputStream(Files.newOutputStream(at)).use { z ->
			for ((name, body) in entries) {
				z.putNextEntry(ZipEntry(name))
				if (body != null) z.write(body.toByteArray())
				z.closeEntry()
			}
		}
	}

	@Test
	fun archiveExtraction() {
		val work = Files.createTempDirectory("apex-ours-zip")
		try {
			val extract = RepoDownloadManager::class.java.getDeclaredMethod("extractZip", Path::class.java, Path::class.java)
			extract.isAccessible = true
			val out = mutableListOf<String>()
			val cases = mapOf(
				"plain" to listOf("repo-abc/" to null, "repo-abc/items/A.json" to "a", "repo-abc/x/y/z.txt" to "z", "toplevel.txt" to "t"),
				"slip" to listOf("repo-abc/ok.txt" to "ok", "repo-abc/../../evil.txt" to "evil"),
			)
			for ((name, entries) in cases) {
				val zipFile = work.resolve("$name.zip")
				zip(zipFile, entries)
				val target = work.resolve("out-$name")
				val result = try {
					extract.invoke(RepoDownloadManager, zipFile, target); "ok"
				} catch (e: java.lang.reflect.InvocationTargetException) {
					"ERR:" + e.targetException.javaClass.simpleName + ":" + e.targetException.message
				}
				val files = if (Files.exists(target)) Files.walk(target).use { s ->
					s.filter { Files.isRegularFile(it) }.map { target.relativize(it).toString() + "=" + it.readText() }.sorted().toList()
				} else emptyList()
				out += "$name\t$result\t$files"
			}
			OursGolden.check("repo-zip", out)
		} finally {
			work.toFile().deleteRecursively()
		}
	}
}
