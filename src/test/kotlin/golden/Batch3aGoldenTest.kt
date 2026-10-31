// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.chat.AnnouncementSwallower
import dev.ashtonite365.apex.features.chat.ArachneQuieter
import dev.ashtonite365.apex.features.dungeon.DungeonLootMarks
import dev.ashtonite365.apex.features.fishing.SeaCreatureBook
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.skyblock.DungeonSecretCounts
import dev.ashtonite365.apex.util.bareText
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch3aGoldenTest {
	private fun keys(name: String) = Golden.load(name).map { it.first }

	@Test
	fun watchdogHider() {
		Golden.verify("sh-watchdog-hider", keys("sh-watchdog-hider").map { k ->
			val s = AnnouncementSwallower.watchdog(); k to Batch3aCorpus.lines(k).joinToString("") { if (s.swallow(it)) "1" else "0" }
		}, minCases = 400)
	}

	@Test
	fun arachneHider() {
		Golden.verify("sh-arachne-hider", keys("sh-arachne-hider").map { k ->
			val q = ArachneQuieter(); k to Batch3aCorpus.lines(k).joinToString("") { if (q.quieten(it)) "1" else "0" }
		}, minCases = 400)
	}

	@Test
	fun seaCreatures() {
		val lotus = SeaCreatureBook.named("Lotus Guardian")!!.catchLine
		val loose = Golden.load("feesh-sea-creatures")
			.filter { (k, o) -> k.startsWith("msg|") && o.startsWith("Lotus Guardian|") && k.removePrefix("msg|") != lotus }.map { it.first }.toSet()
		assertEquals(1, loose.size, "expected exactly the one typo'd Lotus Guardian line: $loose")
		loose.forEach { assertEquals(null, SeaCreatureBook.caughtBy(it.removePrefix("msg|"))) }
		val out = keys("feesh-sea-creatures").filter { it !in loose }.map { k ->
			val (kind, arg) = k.substringBefore('|') to k.substringAfter('|')
			k to when (kind) {
				"msg" -> SeaCreatureBook.caughtBy(arg)?.let { "${it.name}|${it.colour}|${it.rare}|${it.doubleHookable}|${it.displayName}" } ?: "none"
				"name" -> SeaCreatureBook.named(arg)?.colour ?: "-"
				"in" -> SeaCreatureBook.mentionedIn(arg)?.name ?: "-"
				"order" -> SeaCreatureBook.kinds.joinToString(";") { it.name }
				"rare-order" -> SeaCreatureBook.rareKinds.joinToString(";") { it.name }
				else -> error(k)
			}
		}
		Golden.verify("feesh-sea-creatures", out, minCases = 1300) { it !in loose }
	}

	@Test
	fun dungeonSecrets() {
		val out = keys("dungeon-tab-secrets").map { k ->
			val arg = k.substringAfter('|')
			k to Golden.run {
				when {
					k.startsWith("ab|") -> DungeonSecretCounts.fromActionBar(arg)?.let { "${it.found}/${it.total}/${it.done}" } ?: "null"
					k.startsWith("tab") -> {
						val team = DungeonSecretCounts.fromTab(arg.split("⏎"))
						"${team.found},${team.share},${team.estimatedTotal}," +
							Batch3aCorpus.fractions.joinToString(";") { "${DungeonSecretCounts.stillNeeded(team, it)}" }
					}
					k.startsWith("colour|") -> arg.split('|').let { (f, t) -> DungeonSecretCounts.colourFor(f.toInt(), t.toInt()) }
					k.startsWith("clean|") -> DungeonSecretCounts.plain(arg)
					else -> error(k)
				}
			}
		}
		Golden.verify("dungeon-tab-secrets", out, minCases = 4700)
	}

	@Test
	fun lootMarks() {
		val out = keys("dungeon-loot-highlighter").map { k ->
			k to Golden.run {
				if (k.startsWith("lore|")) DungeonLootMarks.isGearRarityLine(Component.literal(k.removePrefix("lore|")).bareText).toString()
				else k.split('|').let { (_, id, name) ->
					val tag = CompoundTag(); if (id.isNotEmpty()) tag.putString("id", id)
					val st = ItemStack(Items.PAPER); if (!tag.isEmpty) st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
					st.set(DataComponents.CUSTOM_NAME, Component.literal(name))
					DungeonLootMarks.isJunkItem(st).toString()
				}
			}
		}

		val stackIndependent = { key: String -> !key.startsWith("junk|POTION") }
		Golden.verify("dungeon-loot-highlighter", out.filter { stackIndependent(it.first) }, minCases = 69, only = stackIndependent)

		for (name in Batch3aCorpus.hoverNames) {
			val plain = Component.literal(name).bareText
			assertEquals("Healing VIII Splash Potion" in plain, DungeonLootMarks.isJunk("POTION_HEALING", plain), name)
		}
		assertEquals(false, DungeonLootMarks.isJunk("POTIONX", "Healing VIII Splash Potion"))
		assertEquals(true, DungeonLootMarks.isJunk("REVIVE_STONE", ""))
	}
}
