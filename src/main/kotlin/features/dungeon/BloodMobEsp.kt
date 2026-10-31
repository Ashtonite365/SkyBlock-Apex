
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object BloodMobEsp {
	@Config
	object TConfig : ManagedConfig("blood-mob-esp", Category.DUNGEONS) {
		val enabled by riskyToggle("enabled", RiskLevel.RISKY)
	}

	const val ORIGIN = -200
	const val ROOM = 32

	fun roomCell(x: Double, z: Double): Pair<Int, Int> =
		Math.floorDiv(x.toInt() - ORIGIN, ROOM) to Math.floorDiv(z.toInt() - ORIGIN, ROOM)

	fun sameRoom(px: Double, pz: Double, ex: Double, ez: Double) =
		roomCell(Math.floor(px), Math.floor(pz)) == roomCell(Math.floor(ex), Math.floor(ez))

	val BLOOD_MOBS = setOf(
		"Revoker", "Psycho", "Reaper", "Cannibal", "Mute", "Ooze", "Putrid", "Freak", "Leech", "Tear", "Parasite",
		"Flamer", "Skull", "Mr. Dead", "Vader", "Frost", "Walker", "Wandering Soul", "Bonzo", "Scarf", "Livid",
		"Spirit Bear", "Shadow Assassin", "Professor", "The Professor", "Lost Adventurer", "Angry Archaeologist", "King Midas",
	)

	private val LEVEL_PREFIX = "^\\[Lv\\d+] ".toRegex()
	private val STAR_PREFIX = "^✯\\s*".toRegex()

	private val HEALTH_SUFFIX = "\\s+[0-9][0-9.,]*[kKmMbB]?(?:/[0-9][0-9.,]*[kKmMbB]?)?❤$".toRegex()

	fun tagName(tag: String): String? {
		var t = tag.trim().replace(STAR_PREFIX, "").replace(LEVEL_PREFIX, "")
		if (!HEALTH_SUFFIX.containsMatchIn(t)) return null
		t = t.replace(HEALTH_SUFFIX, "").trim()
		return t
	}

	fun isBloodMob(tag: String): Boolean = tagName(tag)?.let { it in BLOOD_MOBS } == true

	fun inBloodContext(watcher: Pair<Double, Double>?, px: Double, pz: Double, ex: Double, ez: Double) =
		watcher != null && sameRoom(watcher.first, watcher.second, ex, ez) && sameRoom(px, pz, ex, ez)

	private var watcherPos: Pair<Double, Double>? = null
	private var bloodDone = false
	private var pairs: Map<Int, Int> = emptyMap()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { watcherPos = null; bloodDone = false; pairs = emptyMap() }

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (event.bareText.trim().startsWith("[BOSS] The Watcher: You have proven yourself")) { bloodDone = true; watcherPos = null }
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.enabled || !DungeonUtil.isInDungeonIsland) { pairs = emptyMap(); return }
		val level = Client.game.level ?: return
		val player = Client.player ?: return

		val all = level.entitiesForRendering().filterIsInstance<LivingEntity>().filter { it.isAlive }
		if (!bloodDone) all.firstOrNull { (it.customName ?: it.name).bareText.contains("The Watcher") }?.let { watcherPos = it.x to it.z }
		val watcher = watcherPos
		val tags = mutableListOf<ArmorStand>()
		val named = mutableListOf<LivingEntity>()
		val candidates = mutableListOf<LivingEntity>()
		for (entity in all) {
			val tag = entity.customName?.bareText
			val isBlood = tag != null && isBloodMob(tag) && inBloodContext(watcher, player.x, player.z, entity.x, entity.z)
			if (entity is ArmorStand) { if (isBlood) tags += entity; continue }
			candidates += entity
			if (isBlood && TagBodyPairing.eligible(TagBodyPairing.bodyOf(entity, player))) named += entity
		}
		val paired = TagBodyPairing.pairEntities(tags, candidates, player, pairs)
		pairs = paired.mapValues { it.value.id }
		val bodies = (named + paired.values).distinct()
		if (bodies.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for (b in bodies) box(b.boundingBox, 0x60FF2020)
		}
	}
}
