// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.mobs.MobDrop
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.repo.mobs.MobText
import dev.ashtonite365.apex.util.mc.DisplayStackCount

class MobPageTest {
	private fun r(host: String, name: String, render: String?, drops: List<MobDrop>, level: Int = 0, lore: List<String> = listOf()) =
		MobIndex.DropRecipeInput(host, name, level, 0, mapOf(), null, listOf(), render, drops, lore)

	@Test
	fun renderlessRecipesMergeIntoTheNamedMob() {
		val built = MobIndex.build(listOf(
			r("BOTROOT", "§aBeetle", null, listOf(MobDrop("BOTROOT", 1, "5%"))),
			r("BEETLE_MONSTER", "§cBeetle", "@neurepo:mobs/beetle_monster.json", listOf(MobDrop("CHITIN", 1, "100%")), 10),
			r("FIELD_MOUSE_A", "Field Mouse", null, listOf(MobDrop("CHEESE", 1, "50%"))),
			r("FIELD_MOUSE_B", "§eField Mouse", null, listOf(MobDrop("CHEESE", 1, "50%"), MobDrop("TAIL", 1, "1%"))),
		), mapOf())
		assertEquals(setOf("beetle_monster", "field_mouse_a"), built.keys, "no duplicate ids per plain name")
		val beetle = built.getValue("beetle_monster")
		assertEquals(listOf("CHITIN", "BOTROOT"), beetle.drops.map { it.itemId })
		assertEquals(listOf("BEETLE_MONSTER", "BOTROOT"), beetle.hostItems)
		assertEquals(listOf("CHEESE", "TAIL"), built.getValue("field_mouse_a").drops.map { it.itemId }, "identical drops deduped")
		val droppedBy = MobIndex.indexDrops(built)
		assertEquals(listOf("beetle_monster"), droppedBy["BOTROOT"], "dropped-by kept, pointing at the merged mob")
		assertEquals(beetle, MobIndex.forHostItem("botroot", built), "side-list entry of a merged host opens the mob page")
		assertEquals(beetle, MobIndex.lookup("BOTROOT", built))
	}

	@Test
	fun witherGourdKeepsItsRender() {
		val built = MobIndex.build(listOf(
			r("WITHER_GOURD_MONSTER", "§cWither Gourd", "WitherSkeleton", listOf(MobDrop("PUMPKIN_BOMB", 2, "100%")), 94),
			r("WITHER_GOURD_STRANDED_MONSTER", "§cWither Gourd (Stranded)", "WitherSkeleton", listOf(), 94),
		), mapOf())
		assertEquals("WitherSkeleton", built.getValue("wither_gourd_monster").render)
		assertEquals(2, built.size)
	}

	@Test
	fun tooltipIsNameAndLevelOnlyAndCardHasTheRest() {
		val mob = MobIndex.build(listOf(MobIndex.DropRecipeInput("WITHER_GOURD_MONSTER", "Wither Gourd", 94, 31,
			mapOf("combat" to 141, "exp" to 103), null, listOf(), "WitherSkeleton", listOf(),
			listOf("§7Location: §aunknown", "§7Level: §a94", "§7Health: §a5,000"))), mapOf()).values.single()
		assertEquals(listOf("[Lv94] Wither Gourd"), MobText.hover(mob))
		val card = MobText.cardLines(mob)
		assertTrue("§631 coins" in card); assertTrue("§3+141 Combat XP" in card); assertTrue("§3+103 Exp XP" in card)
		assertTrue("§7Monster · Lv94" in card); assertTrue("§7Health: §f5,000" in card)
		assertFalse(card.any { it.contains("unknown") }, "unknown location is not shown")
	}

	@Test
	fun displayStacksAboveMaxUseCountOneAndText() {
		assertEquals(1, DisplayStackCount.clamp(400, 64)); assertEquals("400", DisplayStackCount.label(400, 64))
		assertEquals(1, DisplayStackCount.clamp(7000, 64)); assertEquals("7,000", DisplayStackCount.label(7000, 64))
		assertEquals(32, DisplayStackCount.clamp(32, 64)); assertNull(DisplayStackCount.label(32, 64))
		assertEquals(1, DisplayStackCount.clamp(2, 1)); assertEquals("2", DisplayStackCount.label(2, 1))
		assertEquals(0, DisplayStackCount.clamp(0, 64))
	}
}
