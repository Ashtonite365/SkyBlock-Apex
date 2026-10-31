package dev.ashtonite365.apex.test.features.misc

import java.util.UUID
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.MiscRender

class HidePlayersNearNpcsTest {
	private fun v(version: Int, n: Long) = UUID(0x1234_0000_0000_0000L or (version.toLong() shl 12), 0x8000_0000_0000_0000UL.toLong() or n)

	private data class Thing(val name: String, val isPlayer: Boolean, val uuid: UUID, val inTab: Boolean, val tags: List<String>, val at: Vec3)

	@Test
	fun onlyThePlayerNextToTheNpcIsHidden() {
		val npc = Thing("npc", true, v(2, 1), false, listOf("Banker", "CLICK"), Vec3(0.0, 64.0, 0.0))
		val zombie = Thing("zombie", false, v(4, 2), false, listOf("[Lv1] Zombie 100/100❤"), Vec3(50.0, 64.0, 0.0))
		val playerModelMob = Thing("mob", true, v(2, 3), false, listOf("[Lv25] Lost Adventurer 9.5k/10k❤"), Vec3(100.0, 64.0, 0.0))
		val sheep = Thing("sheep", false, v(4, 4), false, emptyList(), Vec3(150.0, 64.0, 0.0))
		val npcs = listOf(npc, zombie, playerModelMob, sheep).filter { MiscRender.isRealNpc(it.isPlayer, it.uuid, it.inTab, it.tags) }
		assertEquals(listOf("npc"), npcs.map { it.name })
		val spots = npcs.map { it.at }
		val players = mapOf(
			"byNpc" to Vec3(1.0, 64.0, 1.0),
			"byZombie" to Vec3(51.0, 64.0, 0.0),
			"byMob" to Vec3(101.0, 64.0, 0.0),
			"bySheep" to Vec3(151.0, 64.0, 0.0),
		)
		val hidden = players.filter { (_, at) -> MiscRender.nearAny(spots, at, 3) }.keys
		assertEquals(setOf("byNpc"), hidden)
	}

	@Test
	fun realPlayersAndTabListedEntitiesAreNotNpcs() {
		assertFalse(MiscRender.isRealNpc(true, v(4, 5), true, emptyList()), "real player")
		assertFalse(MiscRender.isRealNpc(true, v(2, 6), true, emptyList()), "listed in tab")
		assertTrue(MiscRender.isRealNpc(true, v(2, 7), false, emptyList()))
	}

	@Test
	fun mobTags() {
		assertTrue(MiscRender.isMobTag("[Lv5] Zombie 100/100❤"))
		assertTrue(MiscRender.isMobTag("Crypt Ghoul 2k/2k❤"))
		assertFalse(MiscRender.isMobTag("Banker"))
		assertFalse(MiscRender.isMobTag("CLICK"))
	}
}
