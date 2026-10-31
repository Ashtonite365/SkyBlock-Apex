package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.LividLogic
import dev.ashtonite365.apex.features.dungeon.LividLogic.Body
import dev.ashtonite365.apex.features.dungeon.LividLogic.Livid
import dev.ashtonite365.apex.features.dungeon.LividLogic.Tag
import dev.ashtonite365.apex.features.dungeon.LividLogic.Via

class LividLogicTest {
	@Test fun woolColourMapsToEveryLivid() {
		val expected = mapOf(
			"white" to Livid.VENDETTA, "magenta" to Livid.CROSSED, "yellow" to Livid.ARCADE, "lime" to Livid.SMILE,
			"gray" to Livid.DOCTOR, "purple" to Livid.PURPLE, "blue" to Livid.SCREAM, "green" to Livid.FROG, "red" to Livid.HOCKEY,
		)
		for ((dye, livid) in expected) {
			assertEquals(livid, LividLogic.fromBlockId("minecraft:${dye}_wool"), dye)
			assertEquals(livid, LividLogic.fromBlockId("${dye}_wool"), dye)
			assertEquals(livid, LividLogic.fromBlockId("minecraft:${dye}_stained_glass"), dye)
		}
		assertEquals(9, Livid.entries.map { LividLogic.fromBlockId("${it.dye}_wool") }.toSet().size)
	}

	@Test fun aliasesAndNonIndicatorBlocks() {
		assertEquals(Livid.CROSSED, LividLogic.fromBlockId("pink_wool"))
		assertEquals(Livid.DOCTOR, LividLogic.fromBlockId("light_gray_stained_glass"))
		assertNull(LividLogic.fromBlockId("minecraft:air"))
		assertNull(LividLogic.fromBlockId("minecraft:stone_bricks"))
		assertNull(LividLogic.fromBlockId("red_wool_slab"))
		assertNull(LividLogic.fromBlockId("red_wool_stairs"))
		assertNull(LividLogic.fromBlockId("red_carpet"))
		assertNull(LividLogic.fromBlockId("red_stained_glass_pane"))
		assertNull(LividLogic.fromBlockId("orange_wool"))
		assertNull(LividLogic.fromBlockId(null))
	}

	@Test fun chatColoursMatchNameColours() {
		assertEquals('f', Livid.VENDETTA.chatCode); assertEquals('d', Livid.CROSSED.chatCode)
		assertEquals('e', Livid.ARCADE.chatCode); assertEquals('a', Livid.SMILE.chatCode)
		assertEquals('7', Livid.DOCTOR.chatCode); assertEquals('5', Livid.PURPLE.chatCode)
		assertEquals('9', Livid.SCREAM.chatCode); assertEquals('2', Livid.FROG.chatCode)
		assertEquals('c', Livid.HOCKEY.chatCode)
	}

	@Test fun indicatorPositionsVerifiedAgainstReferences() {
		assertEquals(LividLogic.Pos(5, 108, 43), LividLogic.KIT_POS)
		assertEquals(LividLogic.Pos(6, 109, 43), LividLogic.PLUS_POS)
		assertEquals(LividLogic.Pos(13, 107, 25), LividLogic.SBTOOLS_LEGACY_POS)
		assertFalse(LividLogic.OLD_WRONG_POS in LividLogic.indicatorPositions(true))
		assertEquals(listOf(LividLogic.KIT_POS, LividLogic.PLUS_POS), LividLogic.indicatorPositions(false))
		assertEquals(LividLogic.SBTOOLS_LEGACY_POS, LividLogic.indicatorPositions(true).last())
	}

	@Test fun pickIndicatorTakesFirstColouredBlock() {
		val o = LividLogic.KIT_POS; val s = LividLogic.PLUS_POS
		assertEquals(o to Livid.SMILE, LividLogic.pickIndicator(listOf(o to "minecraft:lime_wool", s to "minecraft:red_wool")))
		assertEquals(s to Livid.FROG, LividLogic.pickIndicator(listOf(o to "minecraft:air", s to "minecraft:green_wool")))
		assertNull(LividLogic.pickIndicator(listOf(o to "minecraft:air", s to "minecraft:stone")))
	}

	@Test fun entityNames() {
		assertEquals(Livid.HOCKEY, LividLogic.fromEntityName("Hockey Livid"))
		assertEquals(Livid.DOCTOR, LividLogic.fromEntityName("§7Doctor Livid "))
		assertNull(LividLogic.fromEntityName("Livid"))
		assertNull(LividLogic.fromEntityName("Hockey Livid Jr"))
	}

	@Test fun nametagColour() {
		assertEquals('2', LividLogic.nametagColour("§2﴾ §2§lLivid§r§r §a7M§c❤ §2﴿"))
		assertEquals('5', LividLogic.nametagColour("§5﴾ §e\uE07B§5\uE073 §5§lLivid§r§r §a7M§c❤ §5﴿"))
		assertEquals(Livid.HOCKEY, LividLogic.fromNametag("§r§c﴾§r §c§lLivid§r §a6.9M§c❤ §c﴿§r"))
		assertEquals(Livid.VENDETTA, LividLogic.fromNametag("§f﴾ Livid §e6.9M§c❤ §f﴿"))
		assertNull(LividLogic.nametagColour("§c[Lv10] Zombie 100❤"))
		assertNull(LividLogic.fromNametag("Hockey Livid"))
	}

	private val fakes = listOf(
		Body(1, "Hockey Livid", 0.0, 108.0, 0.0), Body(2, "Smile Livid", 10.0, 108.0, 0.0),
		Body(3, "Frog Livid", 20.0, 108.0, 0.0), Body(4, "Arcade Livid", 30.0, 108.0, 0.0),
	)

	@Test fun matchByPlayerName() {
		val m = LividLogic.match(Livid.SMILE, fakes, emptyList())!!
		assertEquals(2, m.bodyId); assertEquals(Via.PLAYER_NAME, m.via); assertEquals(Livid.SMILE, m.livid)
		assertNull(LividLogic.match(Livid.SCREAM, fakes, emptyList()))
	}

	@Test fun matchByNametagWhenNameMissing() {

		val bodies = listOf(Body(10, "Livid", 0.0, 108.0, 0.0), Body(11, "Frog Livid", 20.2, 108.0, 0.3))
		val tags = listOf(
			Tag(100, "§c﴾ §c§lLivid§r §a7M§c❤ §c﴿", 0.0, 110.2, 0.0),
			Tag(101, "§9﴾ §9§lLivid§r §a7M§c❤ §9﴿", 20.0, 110.2, 0.0),
		)
		val m = LividLogic.match(Livid.SCREAM, bodies, tags)!!
		assertEquals(Via.NAMETAG, m.via); assertEquals(101, m.tagId); assertEquals(11, m.bodyId)

		val alone = LividLogic.match(Livid.HOCKEY, listOf(Body(12, "Smile Livid", 40.0, 108.0, 0.0)), tags)!!
		assertEquals(100, alone.tagId); assertNull(alone.bodyId)
		assertNull(LividLogic.match(Livid.ARCADE, bodies, tags))
	}

	@Test fun colourChangeIsRematchedEveryCheck() {

		val first = LividLogic.pickIndicator(listOf(LividLogic.KIT_POS to "red_wool"))!!.second
		assertEquals(1, LividLogic.match(first, fakes, emptyList())!!.bodyId)
		val later = LividLogic.pickIndicator(listOf(LividLogic.KIT_POS to "lime_wool"))!!.second
		assertEquals(2, LividLogic.match(later, fakes, emptyList())!!.bodyId)
	}

	@Test fun blindnessGate() {
		assertTrue(LividLogic.shouldDraw(blind = false, showWhileBlind = false))
		assertFalse(LividLogic.shouldDraw(blind = true, showWhileBlind = false))
		assertTrue(LividLogic.shouldDraw(blind = true, showWhileBlind = true))
	}

	@Test fun isUnderGeometry() {
		val t = Tag(1, "", 0.0, 110.0, 0.0)
		assertTrue(LividLogic.isUnder(t, Body(2, "x", 0.5, 108.0, -0.5)))
		assertFalse(LividLogic.isUnder(t, Body(2, "x", 1.5, 108.0, 0.0)))
		assertFalse(LividLogic.isUnder(t, Body(2, "x", 0.0, 105.0, 0.0)))
	}
}
