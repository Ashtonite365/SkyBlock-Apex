// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.featureevents

import io.github.notenoughupdates.moulconfig.observer.ObservableList
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.events.anniversity.AnniversaryFeatures
import dev.ashtonite365.apex.features.events.anniversity.CenturyRaffleFeatures
import dev.ashtonite365.apex.features.events.carnival.CarnivalFeatures
import dev.ashtonite365.apex.features.events.carnival.MinesweeperHelper
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.collections.batchUpdate

class FeatureEventsEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun groups(p: java.util.regex.Pattern, s: String, vararg names: String): String {
		val m = p.matcher(s)
		if (!m.matches()) return "nomatch"
		return names.joinToString(",") { "$it=" + m.group(it) }
	}

	@Test
	fun anniversaryRewardsBehaveTheSame() {
		val out = mutableListOf<String>()
		val a = AnniversaryFeatures
		out += "id ${a.identifier} dur=${a.pigTimeLimit} pattern=${a.orbLootLine.pattern()}"
		out += "exp=${a.xpPart.pattern()} coin=${a.coinPart.pattern()} item=${a.itemPart.pattern()}"
		for (s in listOf("SHINY! You extracted Shiny Shard and +500 Farming XP from the piglet's orb!",
			"SHINY! You extracted Shiny Shard and 3x Enchanted Pork from the piglet's orb!", "SHINY! nope"))
			out += "pig $s -> ${groups(a.orbLootLine, s, "reward")}"
		for (s in listOf("+500 Farming XP", "+1.5k Combat XP", "+2M Coins", "+300 coins", "3x Enchanted Pork", "Enchanted Pork", "+ Coins"))
			out += "r $s -> exp[${groups(a.xpPart, s, "exp", "kind")}] coin[${groups(a.coinPart, s, "amount")}] item[${groups(a.itemPart, s, "amount", "name")}]"
		val rewards = listOf(
			AnniversaryFeatures.Reward.EXP(10.0, "Farming"), AnniversaryFeatures.Reward.EXP(5.0, "Combat"),
			AnniversaryFeatures.Reward.EXP(2.5, "Farming"), AnniversaryFeatures.Reward.Coins(100.0),
			AnniversaryFeatures.Reward.Coins(50.0), AnniversaryFeatures.Reward.Items(2, ItemId("PORK")),
			AnniversaryFeatures.Reward.Items(3, ItemId("PORK")), AnniversaryFeatures.Reward.Items(1, ItemId("BEEF")),
			AnniversaryFeatures.Reward.Unknown("x"), AnniversaryFeatures.Reward.Unknown("x"),
		)
		for (x in rewards) for (y in rewards) out += "merge $x + $y = ${x.combine(y)}"
		val saved = a.rewards.toList()
		try {
			a.rewards.clear()
			for (x in rewards) {
				a.addReward(x)
				out += "add $x -> ${a.rewards}"
			}
		} finally {
			a.rewards.clear(); a.rewards.addAll(saved)
		}
		val list = ObservableList<Int>(mutableListOf(1, 2))
		var updates = 0
		list.setObserver { updates++ }
		list.batchUpdate {
			list.add(3); list.add(4); list.removeAt(0)
			out += "inside observer=${list.observer == null}"
		}
		out += "atOnce list=$list updates=$updates observerBack=${list.observer != null}"
		out += "pigs empty=${a.ledPigs.isEmpty()} last=${a.lastTouchedPig}"
		OursGolden.check("featev-anniversary", out)
	}

	@Test
	fun raffleAndCarnivalTablesAreTheSame() {
		val out = mutableListOf<String>()
		val c = CenturyRaffleFeatures
		out += "icon ${c.cakeIcon}"
		for (t in c.cakeColors) out += "team ${t.id} ${t.formatting} rgb=${Integer.toHexString(t.searchedTextRgb)} bright=${Integer.toHexString(t.brightenedRgb)} lookup=${c.sliceToColor[t.id] == t}"
		out += "map ${c.sliceToColor.keys}"
		out += "carnival ${CarnivalFeatures.identifier}"
		OursGolden.check("featev-tables", out)
	}

	@Test
	fun minesweeperBehavesTheSame() {
		val out = mutableListOf<String>()
		val m = MinesweeperHelper
		out += "box ${m.sandBoxLow} ${m.sandBoxHigh} size=${m.boardSize}"
		out += "msgs ${m.gameStartMessage}|${m.gameEndMessage}|${m.startGameQuestion}|${m.bombPattern.pattern()}"
		for (s in listOf("MINES! There are 3 bombs hidden nearby.", "MINES! There is 1 bomb hidden nearby.", "MINES! There are 9 bombs hidden nearby.", "MINES! There is 0 bombs hidden nearby."))
			out += "bomb $s -> ${groups(m.bombPattern, s, "bombCount")}"

		for (d in MinesweeperHelper.DowsingMode.entries) out += "mode ${d.name} ${d.itemType} ${d.feature} ${d.description}"
		out += "shovel ${MinesweeperHelper.DowsingMode.id}"
		for (x in -114..-104) for (y in 71..73) for (z in -13..-3) {
			val pos = BlockPos(x, y, z)
			val bp = MinesweeperHelper.BoardPosition.fromBlockPos(pos) ?: continue
			out += "pos $pos -> $bp back=${bp.toBlockPos()}"
		}
		out += "outside ${MinesweeperHelper.BoardPosition.fromBlockPos(BlockPos(-106, 72, -8))} ${MinesweeperHelper.BoardPosition.fromBlockPos(BlockPos(-112, 73, -8))}"
		out += "state ${MinesweeperHelper.GameState()} current=${m.gameState}"
		OursGolden.check("featev-minesweeper", out)
	}
}
