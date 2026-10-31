// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.render.HidePlayersLogic
import dev.ashtonite365.apex.features.render.HidePlayersLogic.Action
import dev.ashtonite365.apex.features.render.HidePlayersLogic.DungeonMode
import dev.ashtonite365.apex.features.render.HidePlayersLogic.Settings
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap

class HidePlayersTest {
	private val L = HidePlayersLogic

	private fun uuid(version: Int, seed: Long = 1L): UUID {
		val msb = (seed shl 16) and 0xFFFF_FFFF_FFFF_0FFFUL.toLong() or (version.toLong() shl 12)
		return UUID(msb, (0x8000_0000_0000_0000UL.toLong()) or seed)
	}

	private val alice = uuid(4, 11)
	private val bob = uuid(4, 12)
	private val npc = uuid(2, 13)

	private fun classify(
		u: UUID, name: String = "Someone", local: Boolean = false, tab: Boolean = true,
		party: Set<UUID> = emptySet(), partyNames: Set<String> = emptySet(), list: Set<String> = emptySet(),
	) = L.classify(u, local, tab, { name.lowercase() }, party, partyNames, list)

	private val on = Settings(enabled = true, keepParty = true)

	@Test
	fun uuidHelperMakesTheRightVersions() {
		assertEquals(4, alice.version()); assertEquals(2, npc.version())
	}

	@Test
	fun npcUuidV2IsNeverHidden() {
		val f = classify(npc, tab = true)
		assertEquals(L.NEVER, f)
		assertFalse(L.shouldHide(f, 1e9, on, false))
		assertFalse(L.shouldHide(f, 1e9, Settings(enabled = true, keepParty = false), false))
		assertFalse(L.shouldHide(f, 1e9, Settings(screenshot = true), false), "screenshot mode keeps NPCs")
	}

	@Test
	fun fakePlayerNotInTabListIsNeverHidden() {
		val f = classify(alice, tab = false)
		assertEquals(L.NEVER, f)
		assertFalse(L.shouldHide(f, 1e9, Settings(screenshot = true), false))
	}

	@Test
	fun localPlayerIsNeverHiddenByTheNormalMode() {
		assertEquals(L.NEVER, classify(alice, local = true))
		assertFalse(L.hideLocal(on, thirdPerson = true))
		assertFalse(L.hideLocal(Settings(screenshot = true), thirdPerson = true), "hide-self is off by default")
		assertFalse(L.hideLocal(Settings(screenshot = true, screenshotHideSelf = true), thirdPerson = false))
		assertTrue(L.hideLocal(Settings(screenshot = true, screenshotHideSelf = true), thirdPerson = true))
	}

	@Test
	fun realPlayerHiddenOnlyWhenEnabled() {
		val f = classify(alice)
		assertEquals(L.REAL, f)
		assertTrue(L.shouldHide(f, 100.0, on, false))
		assertFalse(L.shouldHide(f, 100.0, Settings(), false), "off by default")
		assertFalse(Settings().active)
	}

	@Test
	fun partyMembersStayVisibleByUuidOrName() {
		val byUuid = classify(alice, party = setOf(alice))
		val byName = classify(bob, name = "Bob", partyNames = setOf("bob"))
		for (f in listOf(byUuid, byName)) {
			assertTrue(f and L.PARTY != 0)
			assertFalse(L.shouldHide(f, 100.0, on, false))
			assertTrue(L.shouldHide(f, 100.0, on.copy(keepParty = false), false))
		}
		assertTrue(Settings().keepParty, "keep party defaults on")
	}

	@Test
	fun whitelistKeepsNamesVisibleCaseInsensitive() {
		val list = L.parseWhitelist(" Alice, BOB |carol;  dave ")
		assertEquals(setOf("alice", "bob", "carol", "dave"), list)
		assertTrue(L.parseWhitelist("").isEmpty())
		val f = classify(alice, name = "ALICE", list = list)
		assertTrue(f and L.LISTED != 0)
		assertFalse(L.shouldHide(f, 100.0, on, false))
		assertTrue(L.shouldHide(classify(bob, name = "Eve", list = list), 100.0, on, false))

		val both = classify(alice, name = "alice", party = setOf(alice), list = list)
		assertFalse(L.shouldHide(both, 100.0, on.copy(keepParty = false), false))
	}

	@Test
	fun keepWithinDistanceUsesSquaredCompare() {
		val f = classify(alice)
		val s = on.copy(keepWithin = 10)
		assertFalse(L.shouldHide(f, 9.0 * 9.0, s, false))
		assertFalse(L.shouldHide(f, 10.0 * 10.0, s, false))
		assertTrue(L.shouldHide(f, 10.1 * 10.1, s, false))
		assertTrue(L.shouldHide(f, 0.0, on.copy(keepWithin = 0), false), "0 = off: hidden at any distance")
		assertEquals(100.0, s.keepWithinSq)
	}

	@Test
	fun dungeonModes() {
		val stranger = classify(alice)
		val mate = classify(bob, party = setOf(bob))
		val noParty = on.copy(keepParty = false)

		assertEquals(DungeonMode.EXCEPT_PARTY, Settings().dungeonMode)
		assertFalse(L.shouldHide(mate, 100.0, noParty, inDungeonArea = true))
		assertTrue(L.shouldHide(mate, 100.0, noParty, inDungeonArea = false))
		assertTrue(L.shouldHide(stranger, 100.0, noParty, inDungeonArea = true))

		val showAll = noParty.copy(dungeonMode = DungeonMode.SHOW_ALL)
		assertFalse(L.shouldHide(stranger, 100.0, showAll, true))
		assertTrue(L.shouldHide(stranger, 100.0, showAll, false))

		val same = noParty.copy(dungeonMode = DungeonMode.SAME)
		assertTrue(L.shouldHide(mate, 100.0, same, true))
	}

	@Test
	fun screenshotModeHidesEveryRealPlayerIncludingPartyAndWhitelist() {
		val shot = Settings(enabled = false, screenshot = true)
		assertTrue(shot.active)
		val list = setOf("alice")
		val listed = classify(alice, name = "alice", list = list)
		val mate = classify(bob, party = setOf(bob))
		assertTrue(L.shouldHide(listed, 1.0, shot, false))
		assertTrue(L.shouldHide(mate, 1.0, shot, false))
		assertTrue(L.shouldHide(mate, 1.0, shot.copy(dungeonMode = DungeonMode.SHOW_ALL), true))
		assertTrue(L.shouldHide(classify(alice), 1.0, shot.copy(keepWithin = 50), false))
		assertFalse(L.shouldHide(classify(npc), 1.0, shot, false))
	}

	@Test
	fun cacheComputesOncePerUuidUntilInvalidated() {
		val cache = HidePlayersLogic.DecisionCache()
		var list = emptySet<String>()
		fun flags() = cache.flags(alice) { classify(alice, name = "Alice", list = list) }
		assertEquals(L.REAL, flags())
		repeat(100) { flags() }
		assertEquals(1, cache.computes, "one classification per UUID, not per frame")

		list = setOf("alice")
		assertEquals(L.REAL, flags())
		val gen = cache.generation
		cache.invalidate()
		assertEquals(gen + 1, cache.generation)
		assertEquals(0, cache.size)
		assertEquals(L.REAL or L.LISTED, flags())
		assertEquals(2, cache.computes)
		assertNull(cache.get(bob))
	}

	@Test
	fun cacheInvalidationPicksUpPartyAndTabListChanges() {
		val cache = HidePlayersLogic.DecisionCache()
		var party = emptySet<UUID>()
		var tab = false
		fun flags() = cache.flags(bob) { classify(bob, tab = tab, party = party) }
		assertEquals(L.NEVER, flags())
		tab = true; cache.invalidate()
		assertEquals(L.REAL, flags())
		party = setOf(bob); cache.invalidate()
		assertEquals(L.REAL or L.PARTY, flags())
	}

	@Test
	fun commandParsing() {
		assertEquals(Action.TOGGLE, L.parseAction(null))
		assertEquals(Action.TOGGLE, L.parseAction(""))
		assertEquals(Action.TOGGLE, L.parseAction("toggle"))
		assertEquals(Action.ON, L.parseAction("on"))
		assertEquals(Action.ON, L.parseAction(" ON "))
		assertEquals(Action.OFF, L.parseAction("off"))
		assertEquals(Action.OFF, L.parseAction("Off"))
		assertEquals(Action.SCREENSHOT, L.parseAction("screenshot"))
		assertNull(L.parseAction("maybe"))
		assertEquals(listOf("on", "off", "toggle", "screenshot"), L.COMMAND_WORDS)
		for (w in L.COMMAND_WORDS) assertTrue(L.parseAction(w) != null, w)
	}

	@Test
	fun commandApply() {
		assertTrue(L.applyAction(false, Action.ON)); assertTrue(L.applyAction(true, Action.ON))
		assertFalse(L.applyAction(true, Action.OFF)); assertFalse(L.applyAction(false, Action.OFF))
		assertTrue(L.applyAction(false, Action.TOGGLE)); assertFalse(L.applyAction(true, Action.TOGGLE))
		assertTrue(L.applyAction(true, Action.SCREENSHOT)); assertFalse(L.applyAction(false, Action.SCREENSHOT))
		assertTrue(L.feedback(true).contains("Hide Players") && L.feedback(true).contains("ON"))
		assertTrue(L.feedback(false).contains("OFF"))
	}

	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

	@Test
	fun oldHideFarPlayersFoldsIntoKeepWithin() {
		val all = json("""{"misc-render":{"hide-far-players":true,"far-player-distance":30}}""")
		val out = L.foldOldFarPlayers(JsonObject(emptyMap()), all)
		assertEquals(JsonPrimitive(true), out["enabled"])
		assertEquals(JsonPrimitive(30), out["keep-within"])
		assertEquals(JsonPrimitive(false), out["keep-party"])

		val d = L.foldOldFarPlayers(JsonObject(emptyMap()), json("""{"misc-render":{"hide-far-players":true}}"""))
		assertEquals(JsonPrimitive(24), d["keep-within"])
	}

	@Test
	fun oldHideFarPlayersOnlyOnceAndOnlyWhenOn() {
		val saved = json("""{"enabled":false}""")
		val all = json("""{"misc-render":{"hide-far-players":true,"far-player-distance":30}}""")
		assertSame(saved, L.foldOldFarPlayers(saved, all), "never overrides an already saved Hide Players")
		val empty = JsonObject(emptyMap())
		assertSame(empty, L.foldOldFarPlayers(empty, json("""{"misc-render":{"hide-far-players":false}}""")))
		assertSame(empty, L.foldOldFarPlayers(empty, json("""{}""")))
	}

	@Test
	fun configIsAllowedTierOffByDefaultKeysUnbound() {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		val c = dev.ashtonite365.apex.features.render.HidePlayers.TConfig
		assertEquals("hide-players", c.name)
		assertEquals(dev.ashtonite365.apex.util.data.ManagedConfig.Category.RENDER, c.category)
		for (o in c.sortedOptions) {
			assertEquals(dev.ashtonite365.apex.gui.config.RiskLevel.NONE, o.riskLevel, o.propertyName)
			assertFalse(o.labelText.string.startsWith("⚠"), o.propertyName)
		}
		assertEquals(false, c.allOptions["enabled"]!!.default())
		assertEquals(true, c.allOptions["keep-party"]!!.default())
		assertEquals(0, c.allOptions["keep-within"]!!.default())
		assertEquals("", c.allOptions["whitelist"]!!.default())
		assertEquals(DungeonMode.EXCEPT_PARTY, c.allOptions["dungeon-mode"]!!.default())
		assertEquals(false, c.allOptions["screenshot-hide-self"]!!.default())
		for (k in listOf("toggle-key", "screenshot-key"))
			assertFalse((c.allOptions[k]!!.default() as dev.ashtonite365.apex.keybindings.SavedKeyBinding).isBound, k)

		assertTrue(c.allOptions.keys.none { it == "screenshot" || it == "screenshot-mode" })
	}

	@Test
	fun shownUnderRenderNotDuplicated() {
		val map = MigrationMap.bundled()
		for (k in listOf("enabled", "toggle-key", "keep-party", "whitelist", "keep-within", "dungeon-mode", "screenshot-key",
				"screenshot-hide-hud", "screenshot-hide-apex-hud", "screenshot-hide-self")) {
			val e = map["hide-players.$k"]!!
			assertEquals(MainSection.MISC, e.newPath.main, k)
			assertEquals("visibility", e.newPath.subsection, k)
			assertEquals("render", e.group, k)
		}
		assertEquals(map["hide-weather.enabled"]!!.group, map["hide-players.enabled"]!!.group)

		assertNull(map["misc-render.hide-far-players"])
		assertNull(map["misc-render.far-player-distance"])
	}
}
