// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.auction.PriceHistory
import dev.ashtonite365.apex.features.inventory.EnchantColours
import dev.ashtonite365.apex.features.inventory.storageoverlay.StoragePaneCtm
import dev.ashtonite365.apex.features.mining.CrystalHollowsChestHighlighter.Cell
import dev.ashtonite365.apex.features.mining.CrystalHollowsChestHighlighter.SpawnWatch
import dev.ashtonite365.apex.features.mining.CrystalHollowsOverlay
import dev.ashtonite365.apex.features.misc.CoreMiscTweaks
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import java.nio.file.Files
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch6GoldenTest {
	private val fixture = "batch6-repo-misc"
	private val ruleSets = Batch6Corpus.ruleSets.map { set -> set.mapNotNull { EnchantColours.parseRule(it) } }

	private val tileOfMask: Map<Int, String> by lazy {
		Golden.load(fixture).filter { it.first.startsWith("ctm|") }.associate { (k, v) -> k.removePrefix("ctm|").toInt() to v }
	}

	private fun tiles(grid: List<Int?>): String =
		grid.indices.joinToString(",") { i -> if (grid[i] == null) "null" else tileOfMask.getValue(StoragePaneCtm.neighbours(grid, i)) }

	@Test
	fun recorded() {
		val out = Golden.load(fixture).map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"rule" -> EnchantColours.parseRule(k.substringAfter('|'))
						?.let { "${it.compareWith} ${it.formatting} ${it.comparator} ${it.relativeToMax}" } ?: "null"
					"ench" -> EnchantColours.replaceLine(k.split('|', limit = 3)[2], ruleSets[p[1].toInt()]) { Batch6Corpus.maxLevels[it] }
					"enchnomax" -> EnchantColours.replaceLine(k.split('|', limit = 3)[2], ruleSets[p[1].toInt()])
					"lvl" -> ruleSets[p[1].toInt()][p[2].toInt()].levelMatches(p[3].toInt(), p[4].toInt().takeIf { it >= 0 }).toString()
					"sign" -> CoreMiscTweaks.calculate(k.substringAfter('|')) ?: "null"
					"ch" -> {
						val data = CrystalHollowsOverlay.Data()
						val changed = k.substringAfter('|').split('¦').map { CrystalHollowsOverlay.applyChat(data, it) }
						"$changed ${data.crystals.toSortedMap()} ${data.divanParts.toSortedMap()} ${data.automatonParts.toSortedMap()}"
					}

					"ctm" -> tileOfMask.getValue(p[1].toInt())
					"tiles" -> tiles(k.substringAfter('|').split(',').map { it.toInt().takeIf { c -> c >= 0 } })
					"phformat" -> PriceHistory.encode(PriceHistory.decode(Golden.load(fixture).single { it.first == "phformat" }.second))
					else -> error(k)
				}
			}
		}
		Golden.verify(fixture, out, minCases = 3000)
	}

	@Test
	fun priceHistoryFiles() {
		val folder = Files.createTempDirectory("junit-ph")
		val now = Instant.parse("2026-10-28T01:00:00Z")
		PriceHistory.append(folder, mapOf("A" to PriceHistory.Point(5, 1.0), "BZ:B" to PriceHistory.Point(5, 2.0, 1.5)), now, 7)
		PriceHistory.append(folder, mapOf("A" to PriceHistory.Point(3, 0.5)), now, 7)

		assertEquals(listOf("prices_2026-10-28.json.apack"), Files.list(folder).use { s -> s.map { it.fileName.toString() }.toList() })
		assertEquals(listOf(3L, 5L), PriceHistory.readAll(folder, "A").map { it.t })
		assertEquals(listOf(PriceHistory.Point(5, 2.0, 1.5)), PriceHistory.readAll(folder, "BZ:B"))

		val recorded = PriceHistory.decode(Golden.load(fixture).single { it.first == "phformat" }.second)
		assertEquals(listOf(160.5, 170.0), recorded.items.getValue("ENCHANTED_DIAMOND").map { it.buy })
		assertEquals(498.0, recorded.items.getValue("BZ:ENCHANTED_IRON").single().sell)
		folder.toFile().deleteRecursively()
	}

	@Test
	fun chestWatch() {
		val world = HashMap<BlockPos, Cell>()
		val watch = SpawnWatch(radius = 2)
		val origin = BlockPos(0, 64, 0)
		val chestSpot = BlockPos(1, 64, 1)
		world[chestSpot] = Cell.CHEST
		assertTrue(watch.scan(origin) { world[it] ?: Cell.OTHER }.isEmpty(), "a chest already there is not new")
		world[chestSpot] = Cell.OPEN
		assertTrue(watch.scan(origin) { world[it] ?: Cell.OTHER }.isEmpty())
		world[chestSpot] = Cell.CHEST
		assertEquals(listOf(chestSpot), watch.scan(origin) { world[it] ?: Cell.OTHER }, "open spot became a chest")
		assertTrue(watch.scan(origin) { world[it] ?: Cell.OTHER }.isEmpty(), "reported once")

		world[chestSpot] = Cell.OPEN
		watch.scan(origin) { world[it] ?: Cell.OTHER }
		watch.forget()
		world[chestSpot] = Cell.CHEST
		assertTrue(watch.scan(origin) { world[it] ?: Cell.OTHER }.isEmpty())

		val far = BlockPos(3, 64, 0)
		world[far] = Cell.OPEN
		watch.scan(origin) { world[it] ?: Cell.OTHER }
		world[far] = Cell.CHEST
		assertTrue(watch.scan(origin) { world[it] ?: Cell.OTHER }.isEmpty())
	}

	@Test
	fun paneMasks() {
		val grid = listOf<Int?>(1, 1, null, null, null, null, null, null, 2, 1, 1)
		assertEquals(StoragePaneCtm.RIGHT or StoragePaneCtm.DOWN or StoragePaneCtm.DOWN_RIGHT, StoragePaneCtm.neighbours(grid, 0))
		assertEquals(0, StoragePaneCtm.neighbours(grid, 8), "no wrap from the right edge to the next row")
		assertEquals(0, StoragePaneCtm.neighbours(grid, 2))
	}
}
