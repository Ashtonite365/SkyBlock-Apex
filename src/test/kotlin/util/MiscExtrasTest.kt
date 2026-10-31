package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.MiscExtras

class MiscExtrasTest {
	@Test
	fun rules() {
		val r = MiscExtras.parseRules("explosion, garden:minecraft:block.anvil.land; minecraft:entity.bat.*")
		assertEquals(3, r.size)
		assertTrue(MiscExtras.matches(r, "minecraft:explosion", "hub"))
		assertTrue(MiscExtras.matches(r, "minecraft:block.anvil.land", "garden"))
		assertFalse(MiscExtras.matches(r, "minecraft:block.anvil.land", "hub"))
		assertTrue(MiscExtras.matches(r, "minecraft:entity.bat.ambient", null))
		assertTrue(MiscExtras.wikiUrl("Hyperion sword").endsWith("search=Hyperion+sword"))
	}

	@Test
	fun damage() {
		val R = dev.ashtonite365.apex.features.misc.MiscRender
		assertTrue(R.isDamageSplash("✧12,345✧"))
		assertTrue(R.isDamageSplash("1.2M❤"))
		assertFalse(R.isDamageSplash("Zealot 13k"))
		assertTrue(R.nameMatches("[Lv55] Zealot 13k", "Zealot|Goblin"))
	}

	@Test
	fun volumeBoost() {
		val X = dev.ashtonite365.apex.features.misc.MiscExtras
		assertEquals(2f, X.boostFactor(200, 100))
		assertEquals(3f, X.boostFactor(300, 300))
		assertEquals(0f, X.safeGain(Float.NaN))
		assertEquals(3f, X.safeGain(99f))
		assertEquals(0f, X.safeGain(-1f))
	}

	@Test
	fun gapParsers() {
		assertEquals("Superior", dev.ashtonite365.apex.features.misc.DragonTracker.dragonType("☬ The Superior Dragon has spawned!"))
		assertEquals(123456.0, dev.ashtonite365.apex.features.misc.DragonTracker.damage("           Your Damage: 123,456 (Position #2)"))
		assertEquals("LEGENDARY" to "Desert Settlement", dev.ashtonite365.apex.features.misc.TrevorHelper.task("[NPC] Trevor: You can find your LEGENDARY animal near the Desert Settlement."))
		assertTrue(dev.ashtonite365.apex.features.misc.GapMisc.isInquisitor("Uh oh! You dug out a Minos Inquisitor!"))
		assertEquals(50, dev.ashtonite365.apex.features.misc.GapMisc.quiverLeft("You only have 50 arrows left in your Quiver!"))
		assertEquals(2, dev.ashtonite365.apex.features.misc.GapMisc.customLines("&aHi|there").size)
	}

	@Test
	fun warpsAndDamage() {
		val W = dev.ashtonite365.apex.features.misc.WarpMenu
		val list = listOf(dev.ashtonite365.apex.features.misc.WarpMenu.Entry("mines", "mining_3", listOf("dmines"), net.minecraft.core.BlockPos.ZERO), dev.ashtonite365.apex.features.misc.WarpMenu.Entry("hub", "hub", emptyList(), net.minecraft.core.BlockPos.ZERO))
		val names = mapOf("mining_3" to "Dwarven Mines", "hub" to "Hub")
		assertEquals("mines", W.find("DMINES", list) { names[it]!! }?.warp)
		assertEquals("mines", W.find("dwarven", list) { names[it]!! }?.warp)
		assertEquals("hub", W.find("hub", list) { names[it]!! }?.warp)
		assertEquals("§712,345,678§r", dev.ashtonite365.apex.features.misc.DamageCommas.rewrite("§712,345,678", dev.ashtonite365.apex.features.misc.DamageCommas.Style.COMMAS))
	}

	@Test
	fun gapParsers2() {
		val G = dev.ashtonite365.apex.features.misc.GapFeatures2
		assertEquals(3, G.callingProgress("☄ Steve placed an Arachne's Calling! Something is awakening! (3/4)"))
		assertEquals(12345L, G.storedAmount(listOf("x", "Stored: 12,345/80k")))
		assertEquals(setOf("a"), G.duplicates(listOf("a", "b", "a", null, null)))
		assertEquals(25000.0 to 50000.0, G.thunderCharge(listOf("Charge: 25,000/50k ⸎")))
	}

	@Test
	fun chatExtras() {
		val C = dev.ashtonite365.apex.features.chat.ChatExtras
		assertEquals("CRAZY RARE" to "Judgement Core", C.rareDrop("CRAZY RARE DROP!  Judgement Core (+300% ✯ Magic Find)"))
		assertEquals(90L, C.parseDurationMinutes("1h30m"))
		assertEquals(45L, C.parseDurationMinutes("45"))
		assertEquals(null, C.parseDurationMinutes("soon"))
		assertTrue(C.shouldAlert(dev.ashtonite365.apex.features.chat.ChatExtras.PriceAlert("X", false, 10.0), 9.0))
	}
}
