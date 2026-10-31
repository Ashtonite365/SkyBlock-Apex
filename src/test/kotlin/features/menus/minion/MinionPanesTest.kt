// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.minion

import java.io.File
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.menus.minion.MinionPanes
import dev.ashtonite365.apex.features.menus.minion.MinionParser
import dev.ashtonite365.apex.features.menus.minion.MinionSlotMap
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class MinionPanesTest {
	data class Cell(val itemId: String, val sbId: String?, val name: String, val lore: List<String>)
	private val fillerIds = listOf("minecraft:gray_stained_glass_pane", "minecraft:black_stained_glass_pane", "minecraft:glass_pane",
		"minecraft:lime_stained_glass_pane", "minecraft:white_stained_glass_pane", "minecraft:red_stained_glass_pane")

	private fun cellsOf(slots: List<SlotText>): MutableMap<Int, Cell> {
		val m = mutableMapOf<Int, Cell>()
		slots.forEachIndexed { i, s ->
			m[s.slot] = when {
				s.cleanName.isEmpty() -> Cell(fillerIds[i % fillerIds.size], null, s.name, s.lore)
				s.cleanName.startsWith("Storage unlocked at tier") -> Cell("minecraft:white_stained_glass_pane", null, s.name, s.lore)
				else -> Cell("minecraft:paper", "SOME_ITEM", s.name, s.lore)
			}
		}
		return m
	}
	private fun hidden(cells: Map<Int, Cell>): (Int) -> Boolean = { slot -> cells[slot]?.let { MinionPanes.hide(it.itemId, it.sbId, it.name, it.lore) } ?: false }

	@Test fun paneIds() {
		val colours = listOf("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")
		for (c in colours) assertTrue(MinionPanes.isPane("minecraft:${c}_stained_glass_pane"), c)
		assertTrue(MinionPanes.isPane("minecraft:glass_pane"))
		for (id in listOf("minecraft:glass", "minecraft:gray_stained_glass", "minecraft:paper", "minecraft:air")) assertFalse(MinionPanes.isPane(id), id)
	}

	@Test fun colouredNamedAndBlankPanesHidden() {
		for (c in listOf("gray", "black", "white", "lime", "red", "light_blue")) {
			assertTrue(MinionPanes.hide("minecraft:${c}_stained_glass_pane", null, " ", emptyList()), "blank $c")
			assertTrue(MinionPanes.hide("minecraft:${c}_stained_glass_pane", null, "", emptyList()), "empty $c")
			assertTrue(MinionPanes.hide("minecraft:${c}_stained_glass_pane", null, "§cStorage unlocked at tier X", emptyList()), "named $c")
			assertTrue(MinionPanes.hide("minecraft:${c}_stained_glass_pane", null, "§aClick here", listOf("Some hint")), "named+lore $c")
		}
		assertTrue(MinionPanes.hide("minecraft:glass_pane", null, " ", emptyList()))
		assertTrue(MinionPanes.hide("minecraft:glass_pane", null, "Glass Pane", emptyList()))
		assertFalse(MinionPanes.hide("minecraft:paper", null, " ", emptyList()))
	}

	@Test fun woolWeaverPanesKept() {
		val d = RealDumpFixtures.load("shop-wool-weaver-cool")
		val panes = d.slots.filter { it.cleanName.endsWith("Stained Glass Pane") }
		assertEquals(8, panes.size)
		for (p in panes) {
			val id = "minecraft:" + p.cleanName.removeSuffix(" Stained Glass Pane").lowercase().replace(' ', '_') + "_stained_glass_pane"
			assertTrue(MinionPanes.isPane(id), id)

			assertFalse(MinionPanes.hide(id, "STAINED_GLASS_PANE", "§f" + p.cleanName, listOf("§f§lCOMMON")), p.cleanName)

			assertFalse(MinionPanes.hide(id, null, p.cleanName, p.cleanLore), p.cleanName)
		}

		assertTrue(d.slots.any { it.cleanName.isEmpty() && MinionPanes.hide("minecraft:black_stained_glass_pane", null, it.name, it.lore) })
	}

	@Test fun minionChestRealDumpShowsOnlyTheStorage() {
		val d = RealDumpFixtures.load("minion-chest-items")
		val cells = cellsOf(d.slots)
		val layout = MinionPanes.layout((0 until 27).toList(), hidden(cells))
		assertEquals(listOf(3, 4, 5, 12, 13, 14, 21, 22, 23), layout.map { it.first })
		assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2), layout.map { it.second to it.third })
		assertEquals(3, MinionPanes.rows(layout))
		assertEquals("Heroic Rogue Sword", d.slots.first { it.slot == 3 }.cleanName)

		val d2 = RealDumpFixtures.load("minion-chest-items-2")
		assertEquals(layout.map { it.first }, MinionPanes.layout((0 until 27).toList(), hidden(cellsOf(d2.slots))).map { it.first })

		cells[4] = Cell("minecraft:cyan_stained_glass_pane", "STAINED_GLASS_PANE", "§fCyan Stained Glass Pane", listOf("§f§lCOMMON"))
		assertTrue(4 in MinionPanes.layout((0 until 27).toList(), hidden(cells)).map { it.first })
	}

	@Test fun minionStorageLockPanesHidden() {
		val d = RealDumpFixtures.load("minion-main-tier9")
		val m = MinionParser.parse(d.title, d.slots)!!
		assertEquals(9, m.tier)
		val cells = cellsOf(d.slots)
		val layout = MinionPanes.layout(MinionSlotMap.STORAGE, hidden(cells))
		val shown = layout.map { it.first }
		for (s in listOf(41, 42, 43)) assertFalse(s in shown, "lock pane $s")
		assertEquals(MinionSlotMap.STORAGE - setOf(41, 42, 43), shown)
		assertTrue(21 in shown && 22 in shown)
		assertEquals(5, layout.maxOf { it.second } + 1)
		assertEquals(3, MinionPanes.rows(layout))
	}

	@Test fun paneSlotTurningIntoAnItemAppears() {
		val d = RealDumpFixtures.load("minion-chest")
		val cells = cellsOf(d.slots)
		val slots = (0 until 27).toList()
		assertFalse(0 in MinionPanes.layout(slots, hidden(cells)).map { it.first })
		cells[0] = Cell("minecraft:quartz", "QUARTZ", "Nether Quartz", listOf("COMMON"))
		val after = MinionPanes.layout(slots, hidden(cells)).map { it.first }
		assertTrue(0 in after); assertEquals(10, after.size)
		cells[0] = Cell("minecraft:gray_stained_glass_pane", null, " ", emptyList())
		assertFalse(0 in MinionPanes.layout(slots, hidden(cells)).map { it.first })
	}

	private fun stack(item: net.minecraft.world.level.ItemLike, name: String? = null, lore: List<String> = emptyList(), sbId: String? = null) =
		ItemStack(item).apply {
			name?.let { set(DataComponents.CUSTOM_NAME, Component.literal(it)) }
			if (lore.isNotEmpty()) set(DataComponents.LORE, ItemLore(lore.map { Component.literal(it) }))
			sbId?.let { set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { putString("id", it) })) }
		}

	@Test fun liveStacks() {
		assertTrue(MinionPanes.hide(stack(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ")))
		assertTrue(MinionPanes.hide(stack(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK), " ")))
		assertTrue(MinionPanes.hide(stack(Items.GLASS_PANE, " ")))
		assertTrue(MinionPanes.hide(stack(Items.STAINED_GLASS_PANE.pick(DyeColor.WHITE), "Storage unlocked at tier X")))
		assertTrue(MinionPanes.hide(stack(Items.STAINED_GLASS_PANE.pick(DyeColor.LIME))))
		assertFalse(MinionPanes.hide(stack(Items.STAINED_GLASS_PANE.pick(DyeColor.PURPLE), "Purple Stained Glass Pane", listOf("COMMON"), "STAINED_GLASS_PANE")))
		assertFalse(MinionPanes.hide(stack(Items.STAINED_GLASS_PANE.pick(DyeColor.CYAN), "Cyan Stained Glass Pane", listOf("COMMON"))))
		assertFalse(MinionPanes.hide(stack(Items.IRON_SWORD, "Heroic Rogue Sword", listOf("LEGENDARY SWORD"), "ROGUE_SWORD")))
		assertFalse(MinionPanes.hide(ItemStack.EMPTY))
	}

	@Test fun hiddenCellsAreNotRegistered() {
		val src = listOf("src/main/kotlin", "../src/main/kotlin", "../../src/main/kotlin").map { File(it).absoluteFile.normalize() }
			.first { File(it, "features/menus/minion/MinionChestMenu.kt").isFile }
		val chest = File(src, "features/menus/minion/MinionChestMenu.kt").readText()
		assertTrue(chest.contains("MinionPanes.layout((0 until m.chestSlots).toList()) { MinionPanes.hide(live(it)) }"))
		assertTrue(chest.contains("for ((slot, col, row) in cells) realTile("))
		assertFalse(chest.contains("for (i in 0 until m.chestSlots) realTile("))
		val minion = File(src, "features/menus/minion/MinionMenu.kt").readText()
		assertTrue(minion.contains("MinionPanes.layout(MinionSlotMap.STORAGE) { MinionPanes.hide(live(it)) }"))
		assertTrue(minion.contains("for ((s, col, row) in cells) realTile("))
		assertFalse(minion.contains("STORAGE.forEachIndexed"))
		assertTrue(minion.contains("if (MinionPanes.hide(live(o.slot))) continue"))

		val cells = MinionPanes.layout((0 until 27).toList()) { it % 2 == 0 }
		assertTrue(cells.none { it.first % 2 == 0 })
	}
}
