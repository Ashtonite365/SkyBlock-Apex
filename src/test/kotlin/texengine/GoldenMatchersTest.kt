// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import io.github.moulberry.repo.data.Rarity
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class GoldenMatchersTest {
	private val engine: GoldenEngine = ApexGolden

	@Test
	fun `G-STR-1 bare string is equals with colour stripping`() {
		val m = engine.stringMatcher("\"Hyperion\"")
		assertTrue(m.matches("§6Hyperion"))
		assertFalse(m.matches("Hyperion "))
	}

	@Test
	fun `G-STR-2 preserve keeps colour codes`() {
		val m = engine.stringMatcher("""{"equals":"§6Hyperion","color":"preserve"}""")
		assertTrue(m.matches("§6Hyperion"))
		assertFalse(m.matches("Hyperion"))
	}

	@Test
	fun `G-STR-3 regex is a full match`() {
		assertTrue(engine.stringMatcher("""{"regex":"Hyper.*"}""").matches("§cHyperion"))
		assertFalse(engine.stringMatcher("""{"regex":"yper"}""").matches("Hyperion"))
	}

	@Test
	fun `G-STR-4 malformed matchers are errors`() {
		assertThrows<Exception> { engine.stringMatcher("""{"equals":"a","regex":"a"}""") }
		assertThrows<Exception> { engine.stringMatcher("{}") }
		assertThrows<Exception> { engine.stringMatcher("""{"equals":"a","color":"blue"}""") }
	}

	@Test
	fun `G-STR-5 components are flattened`() {
		val text = Component.literal("Hyp").append(Component.literal("erion").withStyle(ChatFormatting.GOLD))
		assertTrue(engine.stringMatcher("\"Hyperion\"").matches(text))
	}

	private fun num(json: String): (Number) -> Boolean = engine.numberMatcher(json) ?: error("rejected: $json")

	@Test
	fun `G-NUM-1 ranges`() {
		val halfOpen = num("\"[1.5,3)\"")
		assertTrue(halfOpen(1.5)); assertFalse(halfOpen(3)); assertTrue(halfOpen(2.9)); assertFalse(halfOpen(1.4))
		val other = num("\"(1.5,3]\"")
		assertFalse(other(1.5)); assertTrue(other(3))
	}

	@Test
	fun `G-NUM-2 operators`() {
		val ge = num("\">=10\"")
		assertTrue(ge(10)); assertFalse(ge(9.99))
		assertFalse(num("\"<5\"")(5))
	}

	@Test
	fun `G-NUM-3 exact integer`() {
		val m = num("5")
		assertTrue(m(5.0)); assertFalse(m(5.5))
	}

	@Test
	fun `G-NUM-4 QUIRK decimal exact compares as whole number`() {
		val m = num("1.5")
		assertTrue(m(1.0)); assertTrue(m(1.9)); assertFalse(m(2.0)); assertTrue(m(1.5))
	}

	@Test
	fun `G-NUM-5 non-number json is rejected`() {
		assertNull(engine.numberMatcher("true"))
		assertNull(engine.numberMatcher("[1]"))
	}

	@Test
	fun `G-RAR-1 rarity exact and ranges`() {
		val exact = engine.rarityMatcher("\"EPIC\"")!!
		assertTrue(exact(Rarity.EPIC)); assertFalse(exact(Rarity.LEGENDARY))
		val from = engine.rarityMatcher("\"[EPIC,)\"")!!
		assertTrue(from(Rarity.LEGENDARY)); assertFalse(from(Rarity.RARE))
		val open = engine.rarityMatcher("\"(RARE,LEGENDARY)\"")!!
		assertTrue(open(Rarity.EPIC)); assertFalse(open(Rarity.LEGENDARY))
	}
}
