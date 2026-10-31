package dev.ashtonite365.apex.test.features.dungeon.secrets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.secrets.DungeonSecrets
import dev.ashtonite365.apex.features.dungeon.secrets.SecretWayfinderLogic
import dev.ashtonite365.apex.features.dungeon.secrets.SecretWayfinderLogic.Kind
import dev.ashtonite365.apex.features.dungeon.secrets.SecretWayfinderLogic.Secret
import dev.ashtonite365.apex.test.features.dungeon.DungeonExtrasMeta
import dev.ashtonite365.apex.util.skyblock.DungeonSecretCounts

class SecretWayfinderTest {
	private fun s(kind: Kind, x: Double, z: Double, y: Double = 70.0) =
		Secret(kind, x, y, z, if (kind == Kind.ITEM || kind == Kind.BAT) "$kind#${x.toInt()}" else SecretWayfinderLogic.blockKey(kind, x.toInt(), y.toInt(), z.toInt()))

	@Test fun settingIsOffByDefaultWithRows() {
		val c = DungeonSecrets.TConfig
		c.explicitDefaultLoad()
		assertFalse(c.wayfinder)
		DungeonExtrasMeta.assertSecretsRows(c, "wayfinder", "toggle", false, "secrets.secret-wayfinder.enabled")
		DungeonExtrasMeta.assertSecretsRows(c, "wayfinder-colour", "colour", false, "secrets.secret-wayfinder.colour")
	}

	@Test fun detectsSecretKinds() {
		assertTrue(SecretWayfinderLogic.isWitherEssence("e0f3e929-869e-3dca-9504-54c666ee6f23"))
		assertTrue(SecretWayfinderLogic.isWitherEssence("E0F3E929-869E-3DCA-9504-54C666EE6F23"))
		assertFalse(SecretWayfinderLogic.isWitherEssence(null))
		assertFalse(SecretWayfinderLogic.isWitherEssence("fed95410-aba1-39df-9b95-1d4f361eb66e"))
		assertTrue(SecretWayfinderLogic.isSecretItem("DEFUSE_KIT", null))
		assertTrue(SecretWayfinderLogic.isSecretItem(null, " Spirit Leap "))
		assertFalse(SecretWayfinderLogic.isSecretItem("ROTTEN_FLESH", "Rotten Flesh"))
		assertFalse(SecretWayfinderLogic.isSecretItem(null, null))
	}

	@Test fun onlyTheCurrentRoomCounts() {
		val cells = setOf(0 to 0, 1 to 0)
		assertTrue(SecretWayfinderLogic.inCells(-190, -190, cells))
		assertTrue(SecretWayfinderLogic.inCells(-140, -170, cells))
		assertFalse(SecretWayfinderLogic.inCells(-130, -190, cells))
		assertFalse(SecretWayfinderLogic.inCells(-190, -160, cells))
	}

	@Test fun guidesToTheNearestUnfound() {
		val chest = s(Kind.CHEST, -190.0, -190.0); val lever = s(Kind.LEVER, -180.0, -190.0); val bat = s(Kind.BAT, -150.0, -190.0)
		val all = listOf(chest, lever, bat)
		assertEquals(lever, SecretWayfinderLogic.nearestUnfound(all, emptySet(), -178.0, 70.0, -190.0))
		val t = SecretWayfinderLogic.Tracker()
		t.markFound(lever.key)
		assertEquals(chest, SecretWayfinderLogic.nearestUnfound(all, t.foundKeys, -178.0, 70.0, -190.0))
		t.markFound(chest.key); t.markFound(bat.key)
		assertNull(SecretWayfinderLogic.nearestUnfound(all, t.foundKeys, -178.0, 70.0, -190.0))
	}

	@Test fun tabCountMarksTheSecretsYouJustGot() {
		val near = s(Kind.ESSENCE, -190.0, -190.0); val far = s(Kind.CHEST, -170.0, -190.0)
		val t = SecretWayfinderLogic.Tracker()
		assertEquals(emptyList<String>(), t.onTabCount(5, listOf(near, far), -189.0, 70.0, -190.0))
		assertEquals(emptyList<String>(), t.onTabCount(5, listOf(near, far), -189.0, 70.0, -190.0))
		assertEquals(listOf(near.key), t.onTabCount(7, listOf(near, far), -189.0, 70.0, -190.0))
		assertTrue(near.key in t.foundKeys); assertFalse(far.key in t.foundKeys)
		assertEquals(emptyList<String>(), t.onTabCount(null, listOf(far), -189.0, 70.0, -190.0))
		t.reset(); assertTrue(t.foundKeys.isEmpty())
	}

	@Test fun fullRoomCountEndsTheGuide() {
		assertFalse(SecretWayfinderLogic.roomDone(null))
		assertFalse(SecretWayfinderLogic.roomDone(DungeonSecretCounts.RoomCount(2, 5)))
		assertTrue(SecretWayfinderLogic.roomDone(DungeonSecretCounts.RoomCount(5, 5)))
		assertTrue(SecretWayfinderLogic.roomDone(DungeonSecretCounts.RoomCount(0, 0)))
	}

	@Test fun leverIsTheFirstTargetFromFarAwayEvenWhenTheChestIsNearer() {

		val chest = s(Kind.CHEST, -185.0, -105.0, 69.0); val lever = s(Kind.LEVER, -185.0, -146.0)
		val all = listOf(chest, lever)
		assertEquals(lever, SecretWayfinderLogic.pickTarget(all, emptySet(), emptySet(), -185.0, 70.0, -80.0))
		assertEquals(lever, SecretWayfinderLogic.pickTarget(all, emptySet(), emptySet(), -185.0, 70.0, -106.0), "even standing at the chest")

		assertEquals(chest, SecretWayfinderLogic.pickTarget(all, setOf(lever.key), emptySet(), -185.0, 70.0, -146.0))
	}

	@Test fun nonChestSecretsKeepNearestOrder() {
		val bat = s(Kind.BAT, -185.0, -100.0); val lever = s(Kind.LEVER, -185.0, -146.0)
		assertEquals(bat, SecretWayfinderLogic.pickTarget(listOf(bat, lever), emptySet(), emptySet(), -185.0, 70.0, -90.0))
	}

	@Test fun lockedMessageAfterAChestClickMarksItLockedAndALeverFlipUnlocks() {
		val chest = s(Kind.CHEST, -185.0, -105.0, 69.0); val lever = s(Kind.LEVER, -185.0, -146.0); val bat = s(Kind.BAT, -185.0, -130.0)
		val l = SecretWayfinderLogic.LockedChests()
		l.onLevers(mapOf(lever.key to false), setOf(chest.key))
		assertNull(l.onChat("That chest is locked!", 1000), "no chest clicked yet")
		l.onChestClick(chest.key, 1000)
		assertNull(l.onChat("That chest is locked!", 5000), "too long after the click")
		l.onChestClick(chest.key, 6000)
		assertEquals(chest.key, l.onChat("That chest is locked!", 6200))

		assertEquals(bat, SecretWayfinderLogic.pickTarget(listOf(chest, lever, bat), setOf(lever.key), l.keys, -185.0, 70.0, -106.0))

		assertEquals(chest, SecretWayfinderLogic.pickTarget(listOf(chest), emptySet(), l.keys, -185.0, 70.0, -106.0))
		l.onLevers(mapOf(lever.key to false), setOf(chest.key))
		assertTrue(chest.key in l.keys)
		l.onLevers(mapOf(lever.key to true), setOf(chest.key))
		assertFalse(chest.key in l.keys, "lever flipped to powered: chest unlocked")
		l.reset(); assertTrue(l.keys.isEmpty())
	}

	@Test fun chestScreenRightAfterAClickMarksThatChestOpened() {
		val o = dev.ashtonite365.apex.features.dungeon.secrets.ChestOpenings(windowMs = 1500)
		assertNull(o.onChestScreen(100), "no click: some other screen")
		o.onChestClick(42L, 1000)
		assertNull(o.onChestScreen(3000), "too late")
		o.onChestClick(42L, 4000)
		o.onLocked()
		assertNull(o.onChestScreen(4100), "locked chest did not open")
		assertFalse(o.isOpened(42L))
		o.onChestClick(42L, 5000)
		assertEquals(42L, o.onChestScreen(5200))
		assertTrue(o.isOpened(42L))
		assertNull(o.onChestScreen(5300), "one click = one opening")
		o.onLidOpen(7L); assertTrue(o.isOpened(7L))
		o.reset(); assertFalse(o.isOpened(42L))
	}

	@Test fun anOpenedChestNeverComesBackAsATargetEvenAfterTheLever() {
		val chest = s(Kind.CHEST, -185.0, -105.0, 69.0); val chest2 = s(Kind.CHEST, -185.0, -120.0); val lever = s(Kind.LEVER, -185.0, -146.0)

		val live = listOf(chest2, lever)
		assertEquals(lever, SecretWayfinderLogic.pickTarget(live, emptySet(), emptySet(), -185.0, 70.0, -106.0))
		assertEquals(chest2, SecretWayfinderLogic.pickTarget(live, setOf(lever.key), emptySet(), -185.0, 70.0, -106.0))
		assertNull(SecretWayfinderLogic.pickTarget(listOf(lever), setOf(lever.key), emptySet(), -185.0, 70.0, -106.0))
		assertTrue(chest !in live)
	}
}
