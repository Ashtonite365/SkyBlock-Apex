package dev.ashtonite365.apex.test.repo

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.ReforgeLoreInsert
import dev.ashtonite365.apex.test.ApexTestBootstrap
import dev.ashtonite365.apex.util.bareText

class ReforgeLoreInsertTest {
	init { ApexTestBootstrap }
	private fun lit(vararg s: String) = s.map { Component.literal(it) }
	private fun text(l: List<Component>) = l.map { it.bareText }

	@Test
	fun `Fabled Bonus appears exactly once when the ability text already has the header`() {
		val lore = lit("§7Damage: §c+100", "", "§6§lLEGENDARY SWORD")
		val ability = lit("Fabled Bonus", "§7Critical hits have a chance to deal up to §a20% §7extra damage.")
		val out = text(ReforgeLoreInsert.insert(lore, Component.literal("Fabled Bonus"), ability))
		assertEquals(1, out.count { it == "Fabled Bonus" }, out.toString())
		assertEquals("LEGENDARY SWORD", out.last())
	}

	@Test
	fun `header is added once and not again when already in the lore`() {
		val lore = lit("§7Damage: §c+100", "", "§6§lLEGENDARY SWORD")
		val once = ReforgeLoreInsert.insert(lore, Component.literal("Fabled Bonus"), lit("§7text"))
		assertEquals(1, text(once).count { it == "Fabled Bonus" })
		val twice = ReforgeLoreInsert.insert(once, Component.literal("Fabled Bonus"), lit("§7text"))
		assertEquals(text(once), text(twice))
	}

	@Test
	fun `lore without a blank line does not crash and goes before the rarity line`() {
		val lore = lit("§7Damage: §c+100", "§6§lLEGENDARY SWORD")
		val out = text(ReforgeLoreInsert.insert(lore, Component.literal("Fabled Bonus"), lit("§7text")))
		assertEquals(listOf("Damage: +100", "", "Fabled Bonus", "text", "", "LEGENDARY SWORD"), out)
	}

	@Test
	fun `lore without blank or rarity line appends at the end`() {
		val out = text(ReforgeLoreInsert.insert(lit("§7a", "§7b"), Component.literal("Spiritual Bonus"), lit("§7c")))
		assertEquals(listOf("a", "b", "", "Spiritual Bonus", "c"), out)
		val empty = text(ReforgeLoreInsert.insert(listOf(), Component.literal("Spiritual Bonus"), lit("§7c")))
		assertEquals(listOf("Spiritual Bonus", "c"), empty)
	}

	@Test
	fun `with a blank line the layout matches the previous behaviour`() {
		val out = text(ReforgeLoreInsert.insert(lit("§7a", "", "§6§lLEGENDARY"), Component.literal("X Bonus"), lit("§7c")))
		assertEquals(listOf("a", "", "X Bonus", "c", "", "LEGENDARY"), out)
		assertTrue(ReforgeLoreInsert.isBonusHeader("Fabled Bonus"))
	}
}
