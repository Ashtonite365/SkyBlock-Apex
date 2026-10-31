// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.entity

import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.gui.entity.ModifyEquipment
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.gui.entity.MobRenderFailures
import dev.ashtonite365.apex.gui.entity.PreviewDraw
import dev.ashtonite365.apex.gui.entity.PreviewEntityIds
import dev.ashtonite365.apex.gui.entity.PreviewOutcome
import dev.ashtonite365.apex.gui.entity.PreviewScreenBox
import dev.ashtonite365.apex.gui.entity.MobRenderLog
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.joml.Matrix3x2f
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.mobs.MobRenderPlanner
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class MobPreviewRenderTest {
	@AfterEach fun reset() { MobRenderFailures.resetForTests(); MobRenderLog.resetForTests() }

	private fun bareEntity(): ArmorStand {
		val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
		return (f.get(null) as sun.misc.Unsafe).allocateInstance(ArmorStand::class.java) as ArmorStand
	}

	@Test
	fun idAssignedBeforeDraw() {
		val a = bareEntity(); val b = bareEntity()
		assertThrows(IllegalStateException::class.java) { a.id }
		assertFalse(PreviewEntityIds.hasId(a))
		var seen = 0
		val out = PreviewDraw.drawWithFallback(PreviewEntityIds.ensure(a), false, { null }, { seen = it.id }) { throw it }
		assertEquals(PreviewOutcome.PRIMARY, out)
		assertTrue(seen < 0, "local negative id never collides with real (positive) entity ids")
		val first = a.id
		PreviewEntityIds.ensure(a)
		assertEquals(first, a.id, "idempotent")
		assertNotEquals(a.id, PreviewEntityIds.ensure(b).id, "unique")
	}

	@Test
	fun fallbackUsedOnException() {
		val drawn = mutableListOf<String>(); val fails = mutableListOf<Throwable>()
		val out = PreviewDraw.drawWithFallback("mob", false, { "unknown?" }, {
			if (it == "mob") throw IllegalStateException("Tried to access entity ID before ID assignment"); drawn += it
		}) { fails += it }
		assertEquals(PreviewOutcome.FALLBACK, out)
		assertEquals(listOf("unknown?"), drawn)
		assertEquals(1, fails.size)

		assertEquals(PreviewOutcome.FALLBACK, PreviewDraw.drawWithFallback("mob", true, { "unknown?" }, { drawn += it }) { fails += it })
		assertEquals(1, fails.size)
		assertEquals(PreviewOutcome.FAILED, PreviewDraw.drawWithFallback("mob", true, { null }, { drawn += it }) {})
	}

	@Test
	fun logOncePerMob() {
		val lines = mutableListOf<String>()
		MobRenderFailures.log = { lines += it }
		val ex = IllegalStateException("boom")
		assertTrue(MobRenderFailures.report("ant", ex))
		assertFalse(MobRenderFailures.report("ant", ex))
		assertTrue(MobRenderFailures.report("angry_archeologist", ex))
		assertEquals(listOf(
			"[ItemList] mob render failed ant: IllegalStateException: boom",
			"[ItemList] mob render failed angry_archeologist: IllegalStateException: boom",
		), lines)
	}

	private val known = setOf("Player", "ArmorStand", "Zombie")

	@Test
	fun angryArchaeologistIsSkinnedArmouredPlayer() {
		val spec = JsonParser.parseString("""{"entity":"Player","modifiers":[{"type":"playerdata","skin":"neurepo:mobs/angry_archeologist.png"},
			{"type":"equipment","hand":"DIAMOND_SWORD","helmet":"PERFECT_CHESTPLATE_10","chestplate":"PERFECT_CHESTPLATE_10"}]}""").asJsonObject
		val p = MobRenderPlanner.plan(MobEntry("angry_archeologist", "Angry Archaeologist", renderSpec = spec), known::contains) { null }
		assertEquals("Player", p.entity); assertFalse(p.unknown)
		assertEquals(listOf("playerdata", "equipment"), p.modifiers.map { it["type"].asString })
		assertEquals("neurepo:mobs/angry_archeologist.png", p.modifiers[0]["skin"].asString)
	}

	@Test
	fun antIsItsRepoEntity() {
		val spec = JsonParser.parseString("""{"entity":"ArmorStand","modifiers":[{"type":"invisible"},{"type":"equipment","helmet":"ANT_MONSTER"}]}""").asJsonObject
		val p = MobRenderPlanner.plan(MobEntry("ant", "§cAnt", render = "@neurepo:mobs/ant.json"), known::contains) { spec }
		assertEquals("ArmorStand", p.entity); assertFalse(p.unknown)
		assertEquals("ANT_MONSTER", p.modifiers[1]["helmet"].asString)
	}

	@Test
	fun eachSlotGetsItsOwnItem() {
		val info = JsonParser.parseString("""{"type":"equipment","hand":"DIAMOND_SWORD","offhand":"SHIELD","helmet":"DIAMOND_HELMET",
			"chestplate":"DIAMOND_CHESTPLATE","leggings":"DIAMOND_LEGGINGS","feet":"DIAMOND_BOOTS"}""").asJsonObject
		assertEquals(mapOf(
			EquipmentSlot.MAINHAND to "DIAMOND_SWORD", EquipmentSlot.OFFHAND to "SHIELD", EquipmentSlot.HEAD to "DIAMOND_HELMET",
			EquipmentSlot.CHEST to "DIAMOND_CHESTPLATE", EquipmentSlot.LEGS to "DIAMOND_LEGGINGS", EquipmentSlot.FEET to "DIAMOND_BOOTS",
		), ModifyEquipment.slotItems(info))
		val partial = JsonParser.parseString("""{"type":"equipment","chestplate":"X","boots":"Y"}""").asJsonObject
		assertEquals(mapOf(EquipmentSlot.CHEST to "X", EquipmentSlot.FEET to "Y"), ModifyEquipment.slotItems(partial))
	}

	@Test
	fun suffixedIdLookup() {
		val mobs = listOf(
			MobEntry("ashfang_follower_monster", "§cAshfang Follower"),
			MobEntry("bat_private_island_monster", "§cBat"),
			MobEntry("angry_archeologist_miniboss", "§cAngry Archaeologist"),
			MobEntry("ant", "§cAnt"),
			MobEntry("rabbit", "§cRabbit"),
			MobEntry("rabbit_monster_x", "§cRabbit", render = "Rabbit"),
		).associateBy { it.id }
		assertEquals("ashfang_follower_monster", MobIndex.lookup("ASHFANG_FOLLOWER_MONSTER", mobs)?.id)
		assertEquals("bat_private_island_monster", MobIndex.lookup("BAT_PRIVATE_ISLAND_MONSTER", mobs)?.id)
		assertEquals("bat_private_island_monster", MobIndex.lookup("Bat (Private Island)", mobs)?.id)
		assertEquals("ashfang_follower_monster", MobIndex.lookup("Ashfang Follower", mobs)?.id)
		assertEquals("angry_archeologist_miniboss", MobIndex.lookup("Angry Archaeologist", mobs)?.id)
		assertEquals("ant", MobIndex.lookup("ANT_MONSTER", mobs)?.id)
		assertEquals(null, MobIndex.lookup("Nope", mobs))
		assertEquals("rabbit", MobIndex.lookup("RABBIT", mobs)?.id)
		assertEquals("rabbit_monster_x", MobIndex.lookup("§cRabbit ", mobs.filterKeys { it != "rabbit" } + mapOf("x" to MobEntry("x", "§cRabbit"))) ?.id)
	}

	@Test
	fun failureLogCarriesMobName() {
		val lines = mutableListOf<String>(); MobRenderFailures.log = { lines += it }
		MobRenderFailures.report("ashfang_follower_monster", IllegalStateException("x"), "§cAshfang Follower")
		assertEquals(listOf("[ItemList] mob render failed ashfang_follower_monster (Ashfang Follower): IllegalStateException: x"), lines)
	}

	@Test
	fun fallbackWhenPrimaryDrawsNothing() {
		val drawn = mutableListOf<String>(); val fails = mutableListOf<Throwable>()
		val out = PreviewDraw.drawWithFallback("invisible", false, { "unknown?" }, { drawn += it }, { "invisible" }) { fails += it }
		assertEquals(PreviewOutcome.FALLBACK, out)
		assertEquals(listOf("unknown?"), drawn)
		assertTrue(fails.single().message!!.contains("draws nothing"))
		fun shape(t: String = "minecraft:wither_skeleton", inv: Boolean = false, w: Float = 0.7f, h: Float = 2.4f, sc: Float = 1f, gear: Boolean = false) =
			dev.ashtonite365.apex.gui.entity.PreviewShape(t, inv, w, h, sc, gear)
		val v = dev.ashtonite365.apex.gui.entity.PreviewVisibility
		assertEquals(null, v.reason(shape()))
		assertTrue(v.reason(shape(inv = true))!!.contains("invisible"))
		assertEquals(null, v.reason(shape(t = "minecraft:armor_stand", inv = true, gear = true)), "head-only stand draws its helmet")
		assertTrue(v.reason(shape(t = "minecraft:block_display")) != null)
		assertTrue(v.reason(shape(h = 0f)) != null)
		assertTrue(v.reason(shape(sc = 0f)) != null)
		assertEquals(null, v.reasonForChain(listOf(shape(inv = true), shape())), "a visible rider still draws")
	}

	@Test
	fun translatedPoseEntityBoxIsCulledUnlessMapped() {
		val pose = Matrix3x2f().translation(300f, 120f)
		val scissor = ScreenRectangle(304, 124, 50, 80)

		assertEquals(null, PictureInPictureRenderState.getBounds(4, 4, 54, 84, PictureInPictureRenderState.IDENTITY_POSE, scissor))
		val s = PreviewScreenBox.toScreen(pose, 4, 4, 54, 84, 35.0, 10.0, 20.0)
		assertEquals(PreviewScreenBox.Box(304, 124, 354, 204, 35.0, 310.0, 140.0), s)
		assertNotEquals(null, PictureInPictureRenderState.getBounds(s.x0, s.y0, s.x1, s.y1, PictureInPictureRenderState.IDENTITY_POSE, scissor))
	}

	@Test
	fun screenBoxIdentityAndScale() {
		assertEquals(PreviewScreenBox.Box(4, 4, 54, 84, 35.0, 1.0, 2.0), PreviewScreenBox.toScreen(Matrix3x2f(), 4, 4, 54, 84, 35.0, 1.0, 2.0))
		val scaled = PreviewScreenBox.toScreen(Matrix3x2f().translation(10f, 10f).scale(2f), 0, 0, 50, 80, 35.0, 0.0, 0.0)
		assertEquals(PreviewScreenBox.Box(10, 10, 110, 170, 70.0, 10.0, 10.0), scaled)
	}

	@Test
	fun everyMobLogsWhatItDrewOnce() {
		val lines = mutableListOf<String>(); MobRenderLog.log = { lines += it }
		assertTrue(MobRenderLog.report("belle_monster", PreviewOutcome.PRIMARY, "minecraft:wolf", "§cBelle"))
		assertFalse(MobRenderLog.report("belle_monster", PreviewOutcome.PRIMARY, "minecraft:wolf", "§cBelle"))
		MobRenderLog.report("x_monster", PreviewOutcome.FALLBACK, null)
		assertEquals(listOf("[ItemList] mob render belle_monster (Belle): drew minecraft:wolf", "[ItemList] mob render x_monster: drew the ? fallback"), lines)
	}
}
