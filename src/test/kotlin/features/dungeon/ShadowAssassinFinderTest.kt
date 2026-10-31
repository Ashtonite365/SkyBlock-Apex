// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.ShadowAssassinFinder
import dev.ashtonite365.apex.features.dungeon.ShadowAssassinFinder.Kind
import dev.ashtonite365.apex.gui.config.RiskLevel

class ShadowAssassinFinderTest {
	private fun classify(
		enabled: Boolean = true, inDungeon: Boolean = true, includeInvisible: Boolean = true,
		name: String? = null, invisible: Boolean = false, armorStand: Boolean = false,
		self: Boolean = false, realPlayer: Boolean = false,
	) = ShadowAssassinFinder.classify(enabled, inDungeon, includeInvisible, name, invisible, armorStand, self, realPlayer)

	@Test
	fun nameMatching() {
		assertTrue(ShadowAssassinFinder.isShadowAssassinName("Shadow Assassin"))
		assertTrue(ShadowAssassinFinder.isShadowAssassinName("✯ Shadow Assassin 1.2M❤"))
		assertTrue(ShadowAssassinFinder.isShadowAssassinName("[Lv100] Shadow Assassin 300k❤"))
		assertTrue(ShadowAssassinFinder.isShadowAssassinName("shadow assassin"))
		assertFalse(ShadowAssassinFinder.isShadowAssassinName("Shadow Fury"))
		assertFalse(ShadowAssassinFinder.isShadowAssassinName("Assassin"))
		assertFalse(ShadowAssassinFinder.isShadowAssassinName("Lurker 50k❤"))
		assertFalse(ShadowAssassinFinder.isShadowAssassinName(null))
		assertEquals(Kind.SHADOW_ASSASSIN, classify(name = "Shadow Assassin"))
		assertEquals(Kind.SHADOW_ASSASSIN, classify(name = "Shadow Assassin", invisible = true))
		assertNull(classify(name = "Shadow Assassin", realPlayer = true))
		assertNull(classify(name = "Zombie"))
	}

	@Test
	fun invisibleFilter() {
		assertTrue(ShadowAssassinFinder.isInvisibleMob(invisible = true, armorStand = false, self = false, realPlayer = false))
		assertFalse(ShadowAssassinFinder.isInvisibleMob(invisible = false, armorStand = false, self = false, realPlayer = false))
		assertFalse(ShadowAssassinFinder.isInvisibleMob(invisible = true, armorStand = true, self = false, realPlayer = false))
		assertFalse(ShadowAssassinFinder.isInvisibleMob(invisible = true, armorStand = false, self = true, realPlayer = false))
		assertFalse(ShadowAssassinFinder.isInvisibleMob(invisible = true, armorStand = false, self = false, realPlayer = true))
		assertEquals(Kind.INVISIBLE, classify(invisible = true))
		assertNull(classify(invisible = true, includeInvisible = false))
		assertEquals(Kind.SHADOW_ASSASSIN, classify(name = "Shadow Assassin", invisible = true, includeInvisible = false))
		assertNull(classify(invisible = true, armorStand = true))
		assertNull(classify(invisible = true, realPlayer = true))
		assertNull(classify(invisible = true, self = true))
	}

	private fun invisibleOfType(typeId: String?, name: String? = null) = ShadowAssassinFinder.classify(true, true, true, name,
		invisible = true, armorStand = false, self = false, realPlayer = false, typeId = typeId)

	@Test
	fun invisibleSceneryIsSkipped() {

		assertNull(invisibleOfType("minecraft:wither"))
		assertNull(invisibleOfType("minecraft:ender_dragon"))
		assertNull(invisibleOfType("minecraft:giant"))
		assertTrue(ShadowAssassinFinder.isInvisibleScenery("minecraft:wither"))
		assertTrue(ShadowAssassinFinder.isInvisibleScenery("minecraft:ender_dragon"))
		assertTrue(ShadowAssassinFinder.isInvisibleScenery("minecraft:giant"))

		assertNull(ShadowAssassinFinder.classify(true, true, false, null, invisible = true, armorStand = false, self = false,
			realPlayer = false, typeId = "minecraft:enderman"))

		assertEquals(Kind.SHADOW_ASSASSIN, invisibleOfType("minecraft:wither", name = "Shadow Assassin"))
	}

	@Test
	fun invisibleEndermanIsBoxedAsBefore() {

		assertEquals(Kind.INVISIBLE, invisibleOfType("minecraft:enderman"))
		assertEquals(Kind.INVISIBLE, invisibleOfType("minecraft:enderman", name = "Fels"))
		assertFalse(ShadowAssassinFinder.isInvisibleScenery("minecraft:enderman"))

		assertNull(ShadowAssassinFinder.classify(true, true, true, "Fels", invisible = false, armorStand = false, self = false,
			realPlayer = false, typeId = "minecraft:enderman"))
	}

	@Test
	fun typesDroppedByHeightCutoffAreRestored() {

		for (id in listOf("minecraft:iron_golem", "minecraft:warden", "minecraft:creaking", "minecraft:ghast",
			"minecraft:happy_ghast", "minecraft:slime", "minecraft:magma_cube", "minecraft:phantom")) {
			assertEquals(Kind.INVISIBLE, invisibleOfType(id), id)
			assertFalse(ShadowAssassinFinder.isInvisibleScenery(id), id)
		}

		for (id in listOf("minecraft:player", "minecraft:zombie", "minecraft:skeleton", "minecraft:wither_skeleton",
			"minecraft:ravager", null)) {
			assertEquals(Kind.INVISIBLE, invisibleOfType(id), id.toString())
		}
		assertFalse(ShadowAssassinFinder.isInvisibleScenery(null))
	}

	@Test
	fun dungeonOnlyGate() {
		assertTrue(ShadowAssassinFinder.isActive(enabled = true, inDungeon = true))
		assertFalse(ShadowAssassinFinder.isActive(enabled = true, inDungeon = false))
		assertFalse(ShadowAssassinFinder.isActive(enabled = false, inDungeon = true))
		assertNull(classify(inDungeon = false, name = "Shadow Assassin"))
		assertNull(classify(inDungeon = false, invisible = true))
		assertNull(classify(enabled = false, name = "Shadow Assassin"))
	}

	@Test
	fun offByDefaultAndRisky() {
		val c = ShadowAssassinFinder.TConfig
		c.explicitDefaultLoad()
		val opt = c.allOptions.getValue("enabled")
		assertEquals(RiskLevel.RISKY, opt.riskLevel)
		assertEquals(false, opt.default())
		assertFalse(c.enabled)
		assertEquals("shadow-assassin-finder", c.name)

		assertTrue(c.sortedOptions.none { it.handler is dev.ashtonite365.apex.gui.config.KeyBindingHandler })
	}

	private fun pt(x: Double, y: Double = 70.0, z: Double = 0.0) = ShadowAssassinFinder.Point(x, y, z)

	@Test
	fun teleportIsAJumpWalkingIsNot() {
		val t = ShadowAssassinFinder.JumpTracker()
		assertTrue(t.update(mapOf(1 to pt(0.0)), 0).isEmpty())
		assertTrue(t.update(mapOf(1 to pt(0.4)), 50).isEmpty())
		assertTrue(t.update(mapOf(1 to pt(2.5)), 100).isEmpty())
		val j = t.update(mapOf(1 to pt(12.0, 71.0, 4.0)), 150)
		assertEquals(1, j.size)
		assertEquals(pt(2.5), j[0].from)
		assertEquals(pt(12.0, 71.0, 4.0), j[0].to)
		assertEquals(150L, j[0].atMs)

		assertTrue(t.update(mapOf(1 to pt(200.0)), 200).isEmpty())
	}

	@Test
	fun vanishAndReappearAsANewEntity() {
		val t = ShadowAssassinFinder.JumpTracker()
		t.update(mapOf(7 to pt(0.0)), 0)
		assertTrue(t.update(emptyMap(), 50).isEmpty())
		val j = t.update(mapOf(9 to pt(8.0)), 300)
		assertEquals(listOf(pt(0.0) to pt(8.0)), j.map { it.from to it.to })

		val t2 = ShadowAssassinFinder.JumpTracker()
		t2.update(mapOf(7 to pt(0.0)), 0)
		t2.update(emptyMap(), 50)
		assertTrue(t2.update(mapOf(9 to pt(8.0)), 50 + ShadowAssassinFinder.JumpTracker.VANISH_MS + 1).isEmpty())

		val t3 = ShadowAssassinFinder.JumpTracker()
		t3.update(mapOf(7 to pt(0.0)), 0)
		t3.update(emptyMap(), 50)
		assertTrue(t3.update(mapOf(9 to pt(1.0)), 100).isEmpty())
	}

	@Test
	fun effectLastsItsDurationThenExpires() {
		val d = ShadowAssassinFinder.JumpTracker.DEFAULT_DURATION_MS
		val t = ShadowAssassinFinder.JumpTracker()
		t.update(mapOf(1 to pt(0.0)), 0)
		t.update(mapOf(1 to pt(10.0)), 1000)
		assertEquals(1, t.active(1000).size)
		assertEquals(1, t.active(1000 + d).size)
		assertTrue(t.active(1001 + d).isEmpty())
		t.update(mapOf(1 to pt(0.0)), 5000)
		t.reset()
		assertTrue(t.active(5000).isEmpty())
	}

	@Test
	fun expiryFollowsTheConfiguredDuration() {
		val t = ShadowAssassinFinder.JumpTracker()
		t.update(mapOf(1 to pt(0.0)), 0)
		t.update(mapOf(1 to pt(10.0)), 100)
		assertEquals(1, t.active(100 + 5000, durationMs = 5000).size)
		assertTrue(t.active(100 + 5001, durationMs = 5000).isEmpty())
		val t2 = ShadowAssassinFinder.JumpTracker()
		t2.update(mapOf(1 to pt(0.0)), 0)
		t2.update(mapOf(1 to pt(10.0)), 100)
		assertTrue(t2.active(100 + 801, durationMs = 800).isEmpty())

		assertTrue(ShadowAssassinFinder.JumpTracker.DEFAULT_DURATION_MS > ShadowAssassinFinder.JumpTracker.WAVE_MS)
	}

	@Test
	fun pathStartIsTheLastPositionBeforeTheJump() {
		val t = ShadowAssassinFinder.JumpTracker()
		t.update(mapOf(3 to pt(0.0)), 0)
		t.update(mapOf(3 to pt(0.3)), 50)
		t.update(mapOf(3 to pt(0.6)), 100)
		assertEquals(pt(0.6), t.lastPosition(3))
		val j = t.update(mapOf(3 to pt(-6.0, 72.0, 5.0)), 150).single()
		assertEquals(pt(0.6), j.from)
		assertEquals(pt(-6.0, 72.0, 5.0), j.to)
		assertEquals(3, j.id)
		assertEquals(listOf(j), t.active(150))

		assertTrue(t.update(mapOf(3 to pt(-5.7, 72.0, 5.0)), 200).isEmpty())
		assertEquals(pt(-6.0, 72.0, 5.0), t.active(200).single().to)
	}

	@Test
	fun smoothedTeleportRecordsTheWholePath() {

		val t = ShadowAssassinFinder.JumpTracker()
		t.update(mapOf(1 to pt(0.0)), 0)
		t.update(mapOf(1 to pt(0.2)), 50)
		assertTrue(t.update(mapOf(1 to pt(2.6)), 100).isEmpty())
		val j = t.update(mapOf(1 to pt(5.0)), 150).single()
		assertEquals(pt(0.2), j.from)
		assertEquals(pt(5.0), j.to)
		assertTrue(t.update(mapOf(1 to pt(7.4)), 200).isEmpty())
		assertTrue(t.update(mapOf(1 to pt(7.5)), 250).isEmpty())
		val active = t.active(250).single()
		assertEquals(pt(0.2), active.from)
		assertEquals(pt(7.4), active.to)
		assertEquals(150L, active.atMs)
	}

	@Test
	fun knockbackAndSteadyMovementAreNotJumps() {
		val t = ShadowAssassinFinder.JumpTracker()
		t.update(mapOf(1 to pt(0.0)), 0)

		assertTrue(t.update(mapOf(1 to pt(1.2)), 50).isEmpty())
		assertTrue(t.update(mapOf(1 to pt(1.9)), 100).isEmpty())
		assertTrue(t.update(mapOf(1 to pt(2.3)), 150).isEmpty())
		assertTrue(t.active(150).isEmpty())
	}

	@Test
	fun invisibleAssassinThatDespawnsAndReappears() {

		val t = ShadowAssassinFinder.JumpTracker()
		t.update(mapOf(4 to pt(1.0)), 0)
		t.update(mapOf(4 to pt(1.5)), 50)
		for (ms in 100L..2000L step 50) assertTrue(t.update(emptyMap(), ms).isEmpty())
		val j = t.update(mapOf(11 to pt(9.0, 70.0, 6.0)), 2050).single()
		assertEquals(pt(1.5), j.from)
		assertEquals(pt(9.0, 70.0, 6.0), j.to)
		assertEquals(11, j.id)
	}

	@Test
	fun animationProgressOverTime() {
		val a = ShadowAssassinFinder.PathAnim
		val j = ShadowAssassinFinder.Jump(1, pt(0.0), pt(10.0), 1000)
		assertNull(a.age(1000, 999, 3000))
		assertEquals(0.0, a.age(1000, 1000, 3000)!!, 1e-9)
		assertEquals(0.5, a.age(1000, 2500, 3000)!!, 1e-9)
		assertNull(a.age(1000, 4001, 3000))

		assertEquals(0.0, a.pulse(j, 0.0).x, 1e-9)
		assertEquals(70.0 + a.BODY_Y, a.pulse(j, 0.0).y, 1e-9)
		var prev = -1.0
		for (i in 0..20) {
			val x = a.pulse(j, a.TRAVEL * i / 20).x
			assertTrue(x >= prev, "pulse moves towards the end")
			prev = x
		}
		assertEquals(10.0, a.pulse(j, a.TRAVEL).x, 1e-9)
		assertEquals(10.0, a.pulse(j, 0.9).x, 1e-9)
		assertTrue(a.pulse(j, a.TRAVEL / 2).x in 3.0..7.0)

		val (s0, s1) = a.streak(j, a.TRAVEL / 2)!!
		assertTrue(s0.x < s1.x)
		assertEquals(a.pulse(j, a.TRAVEL / 2), s1)
		assertNull(a.streak(j, a.TRAVEL))

		assertEquals(0.0, a.departAge(0.0), 1e-9)
		assertTrue(a.landAge(a.TRAVEL / 2) < 0)
		assertEquals(0.0, a.landAge(a.TRAVEL), 1e-9)
		assertTrue(a.landAge(1.0) >= 1.0)
	}

	@Test
	fun fadesOutInsteadOfVanishing() {
		val a = ShadowAssassinFinder.PathAnim
		assertEquals(1.0, a.fade(0.0), 1e-9)
		assertEquals(1.0, a.fade(a.FADE_FROM), 1e-9)
		var prev = 1.0
		for (i in 1..20) {
			val f = a.fade(a.FADE_FROM + (1 - a.FADE_FROM) * i / 20)
			assertTrue(f <= prev && f >= 0.0)
			prev = f
		}
		assertTrue(a.fade(0.8) in 0.1..0.9)
		assertEquals(0.0, a.fade(1.0), 1e-9)
		assertEquals(255, a.alpha(255, 1.0, 0.1))
		assertTrue(a.alpha(255, 1.0, 0.9) in 1..254)
		assertEquals(0, a.alpha(255, 1.0, 1.0))
		assertTrue(a.alpha(255, a.TRAIL_ALPHA, 0.1) < a.alpha(255, 1.0, 0.1))
	}

	@Test
	fun hoopFacesTheTravelDirection() {
		val a = ShadowAssassinFinder.PathAnim
		val c = pt(5.0, 71.0, 0.0)
		val hoop = a.hoop(c, pt(0.0), pt(10.0))
		assertEquals(a.HOOP_SEGMENTS + 1, hoop.size)
		assertEquals(hoop.first(), hoop.last())
		for (p in hoop) {
			assertEquals(5.0, p.x, 1e-9)
			assertEquals(a.HOOP_RADIUS, p.distanceTo(c), 1e-9)
		}

		for (h in listOf(a.hoop(c, pt(0.0, 60.0), pt(0.0, 80.0)), a.hoop(c, c, c)))
			for (p in h) assertEquals(a.HOOP_RADIUS, p.distanceTo(c), 1e-9)
	}

	@Test
	fun waveRingsSpreadAndFade() {
		val w = ShadowAssassinFinder.Wave
		val c = pt(0.0, 70.0, 0.0)
		val first = w.ring(c, 0.05, 0)!!
		assertEquals(w.SEGMENTS + 1, first.size)
		assertEquals(first.first(), first.last())
		val late = w.ring(c, 0.6, 0)!!
		fun radius(r: List<ShadowAssassinFinder.Point>) = Math.hypot(r[0].x - c.x, r[0].z - c.z)
		assertTrue(radius(late) > radius(first))
		assertTrue(first.all { it.y >= 70.0 + 0.05 - w.CREST - 1e-9 && it.y <= 70.0 + 0.05 + w.CREST + 1e-9 })
		assertNull(w.ring(c, 0.05, 2))
		assertTrue(w.ring(c, 0.5, 2) != null)
		assertNull(w.ring(c, 1.0, 0))
		assertTrue(w.alpha(200, 0.05, 0) > w.alpha(200, 0.6, 0))
		assertEquals(0, w.alpha(200, 1.0, 0))
	}

	@Test
	fun jumpEffectIsARiskyOptionOfTheFinder() {
		val c = ShadowAssassinFinder.TConfig
		c.explicitDefaultLoad()
		val opt = c.allOptions.getValue("jump-effect")
		assertEquals(RiskLevel.RISKY, opt.riskLevel)
		assertEquals(false, opt.default())
		assertFalse(c.jumpEffect)
		for (k in listOf("jump-colour", "jump-thickness", "jump-duration", "jump-trail"))
			assertFalse(c.allOptions.getValue(k).riskLevel.isRisky, k)
		DungeonExtrasMeta.assertRows(c, "jump-effect", "riskyToggle", risky = true)
		DungeonExtrasMeta.assertRows(c, "jump-colour", "colour", risky = false)
		DungeonExtrasMeta.assertRows(c, "jump-thickness", "integer", risky = false)
		DungeonExtrasMeta.assertRows(c, "jump-duration", "duration", risky = false)
		DungeonExtrasMeta.assertRows(c, "jump-trail", "toggle", risky = false)
	}

	@Test
	fun strongerDefaults() {
		val c = ShadowAssassinFinder.TConfig
		c.explicitDefaultLoad()
		val rgb = c.jumpColour.getEffectiveColourRGB()
		assertEquals(0xFF, rgb ushr 24)
		assertTrue(rgb != ShadowAssassinFinder.OLD_DEFAULT_JUMP_COLOUR)
		val hsb = java.awt.Color.RGBtoHSB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, null)
		assertTrue(hsb[1] > 0.95f && hsb[2] > 0.95f, "saturated and bright")
		assertEquals(6, c.jumpThickness)
		assertEquals(3000L, c.jumpDuration.inWholeMilliseconds)
		assertTrue(c.jumpTrail)
	}

	@Test
	fun oldDefaultColourIsUpgradedButAPickedColourStays() {
		val c = ShadowAssassinFinder.TConfig
		fun saved(colour: io.github.notenoughupdates.moulconfig.ChromaColour) = kotlinx.serialization.json.JsonObject(mapOf(
			"jump-colour" to kotlinx.serialization.json.Json.encodeToJsonElement(
				dev.ashtonite365.apex.gui.config.ColourHandler.ChromaSerializer, colour),
			"jump-effect" to kotlinx.serialization.json.JsonPrimitive(true)))
		val old = saved(io.github.notenoughupdates.moulconfig.ChromaColour.fromStaticRGB(0x40, 0xB0, 0xFF, 0xD0))
		val upgraded = ShadowAssassinFinder.dropOldDefaultJumpColour(old)
		assertFalse("jump-colour" in upgraded)
		assertTrue("jump-effect" in upgraded)
		val picked = saved(io.github.notenoughupdates.moulconfig.ChromaColour.fromStaticRGB(0xFF, 0x00, 0x00, 0xFF))
		assertEquals(picked, ShadowAssassinFinder.dropOldDefaultJumpColour(picked))
		val none = kotlinx.serialization.json.JsonObject(emptyMap())
		assertEquals(none, ShadowAssassinFinder.dropOldDefaultJumpColour(none))

		c.loadFrom(Unit, kotlinx.serialization.json.JsonObject(mapOf(c.name to old)))
		assertEquals(0xFF, c.jumpColour.getEffectiveColourRGB() ushr 24)
		c.explicitDefaultLoad()
	}
}
