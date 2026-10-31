// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import dev.ashtonite365.apex.util.render.WaypointType
import io.github.notenoughupdates.moulconfig.ChromaColour
import org.joml.Vector2i
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

private fun inTheRift() = PlusRiftParkour.inRift()
private fun RVec.asVec3() = Vec3(x, y, z)
private fun RVec.asBlockPos() = BlockPos.containing(x, y, z)
private fun BlockPos.asRVec() = RVec(x.toDouble(), y.toDouble(), z.toDouble())
private fun playerSpot(): RVec? {
	val me = Client.player ?: return null
	return RVec(me.x, me.y, me.z)
}

object PlusRiftDanceRoom {
	@Config
	object TConfig : ManagedConfig("rift-dance-room", Category.RIFT) {
		val enabled by toggle("enabled") { false }
		val linesToShow by integer("lines-to-show", 1, 10, ) { 4 }
		val hidePlayers by toggle("hide-players") { false }
		val hud by position("hud", 160, 70) { Vector2i(5, 200) }
	}

	private var cachedSteps: List<String>? = null
	var index = 0
		private set
	private var ticksLeft = 0
	var inRoom = false
		private set

	private fun restart() {
		index = 0
		ticksLeft = 0
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		inRoom = false
		restart()
	}

	private fun standingInRoom(): Boolean {
		val me = Client.player ?: return false
		return DanceRoom.inRoom(me.x, me.y, me.z) && PlusRiftParkour.inArea("Mirrorverse")
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!inTheRift()) {
			inRoom = false
			return
		}
		if (event.tickCount % 10 == 0) inRoom = standingInRoom()
		when {
			!inRoom -> restart()
			ticksLeft > 0 -> ticksLeft--
		}
	}

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		if (!(TConfig.enabled && inRoom)) return
		val path = event.sound.value().location().path
		if (DanceRoom.isSuccess(path, event.pitch, event.volume)) {
			index++
			ticksLeft = 20
		} else if (DanceRoom.isFailure(path, event.pitch, event.volume) && (index > 0 || ticksLeft > 0)) {
			restart()
		}
	}

	private fun steps(): List<String> {
		cachedSteps?.let { return it }
		val loaded = RiftRepo.loadDance()
		cachedSteps = loaded.ifEmpty { null }
		return loaded
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!(TConfig.enabled && inRoom)) return
		val list = steps()
		event.drawPlusLines(TConfig.hud) {
			for (line in DanceRoom.lines(list, index, ticksLeft, TConfig.linesToShow)) add(line)
		}
	}

	fun shouldHideEntity(entity: Entity): Boolean {
		if (!TConfig.hidePlayers || !inRoom) return false
		return entity is RemotePlayer && entity.uuid.version() == 4
	}
}

object PlusRiftDreadfarm {
	@Config
	object TConfig : ManagedConfig("rift-dreadfarm", Category.RIFT) {
		val buttons by toggle("wooden-buttons") { false }
		val buttonColour by colour("button-colour") { ChromaColour.fromStaticRGB(170, 0, 170, 160) }
		val berberis by toggle("wilted-berberis") { false }
		val berberisSequence by toggle("berberis-sequence") { true }
		val onlyOnFarmland by toggle("only-on-farmland") { false }
		val hideBerberisParticles by toggle("hide-berberis-particles") { false }
		val muteBerberis by toggle("mute-berberis") { false }
	}

	private val HIT_PLAIN = Regex("You have hit \\d+/56 of the wooden buttons!")
	private const val ALL_PLAIN = "You've hit all 56 wooden buttons!"
	private val BERBERIS_SOUNDS = setOf("entity.donkey.death", "entity.donkey.hurt")
	private const val PARTICLE_MERGE_SQ = 8.0
	private const val PARTICLE_MILLIS = 500L

	private var buttonSpots: List<RiftRepo.ButtonSpot>? = null
	private val pressed = HashSet<RVec>()
	private var lastPressed: RVec? = null
	var allHit = false
		private set

	private var sequences: Map<RVec, BerberisSequence>? = null
	private val sparkles = HashMap<RVec, Long>()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		pressed.clear()
		allHit = false
		lastPressed = null
		sparkles.clear()
		sequences?.values?.forEach(BerberisSequence::reset)
	}

	private fun holdingWand() = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId == "FARMING_WAND"

	private fun berberisFields(): Map<RVec, BerberisSequence> {
		sequences?.let { return it }
		val loaded = RiftRepo.loadBerberis().mapValues { BerberisSequence(it.value) }
		if (loaded.isNotEmpty()) sequences = loaded
		return loaded
	}

	private fun brokeBerberis(at: RVec) {
		for (seq in berberisFields().values) if (seq.broke(at)) return
	}

	private fun sequenceOn() = TConfig.berberis && TConfig.berberisSequence

	@Subscribe
	fun onAttack(event: AttackBlockEvent) {
		if (!inTheRift()) return
		val pos = event.blockPos
		val at = pos.asRVec()
		val state = Client.level?.getBlockState(pos) ?: return
		if (TConfig.buttons && !allHit && state.`is`(Blocks.OAK_BUTTON) && at !in pressed) lastPressed = at
		if (sequenceOn() && state.`is`(Blocks.DEAD_BUSH)) brokeBerberis(at)
	}

	@Subscribe
	fun onBlock(event: PlusBlockChangeEvent) {
		if (!inTheRift() || !sequenceOn()) return
		if (event.oldName == "dead_bush" && event.newName == "air") brokeBerberis(event.pos.asRVec())
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!inTheRift() || allHit) return
		val legacy = event.text.getLegacyFormatString()
		val plain = event.bareText.trim()
		if (ButtonRules.hit.matches(legacy) || HIT_PLAIN.matches(plain)) lastPressed?.let(pressed::add)
		if (legacy == ButtonRules.ALL || plain == ALL_PLAIN) {
			allHit = true
			buttonSpots?.forEach { pressed.addAll(it.buttons) }
		}
	}

	private fun learnRespawn(at: RVec) {
		val blockAt = at.block()
		if (Client.level?.getBlockState(blockAt.asBlockPos().below())?.`is`(Blocks.FARMLAND) != true) return
		val fields = berberisFields()
		val centre = fields.keys.filter { it.distanceIgnoreY(blockAt) < 50 }.minByOrNull { it.distanceIgnoreY(blockAt) } ?: return
		if (fields.getValue(centre).respawn(blockAt, System.currentTimeMillis()))
			Client.showChat(Component.literal("§aBerberis respawn sequence learned!"))
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!inTheRift() || !TConfig.berberis || !holdingWand()) return
		val at = RVec(event.position.x, event.position.y, event.position.z)
		val nearKnown = sparkles.keys.any { it.distanceSq(at) < PARTICLE_MERGE_SQ }
		val type = event.particleEffect.type
		if (type == ParticleTypes.FIREWORK) {
			sparkles.keys.removeIf { it.distanceSq(at) < PARTICLE_MERGE_SQ }
			sparkles[at] = System.currentTimeMillis()
			if (TConfig.hideBerberisParticles) event.cancel()
			return
		}
		if (TConfig.hideBerberisParticles && nearKnown) event.cancel()
		if (type == ParticleTypes.HAPPY_VILLAGER && TConfig.berberisSequence) learnRespawn(at)
	}

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		if (!TConfig.muteBerberis || !inTheRift()) return
		if (event.sound.value().location().path in BERBERIS_SOUNDS) event.cancel()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val now = System.currentTimeMillis()
		sparkles.values.removeIf { now - it > PARTICLE_MILLIS }
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!inTheRift()) return
		val here = playerSpot() ?: return
		WorldDraw.drawInWorld(event) {
			if (TConfig.buttons && !allHit && PlusRiftEnigma.trackingButtons) {
				val spots = buttonSpots ?: RiftRepo.loadButtonSpotsFromGraph().also { buttonSpots = it.ifEmpty { null } }
				val spot = ButtonRules.nearestSpot(spots, pressed, here)
				if (spot != null) {
					val distance = spot.position.distance(here)
					if (distance > 2.5) {
						text(spot.position.asVec3().add(0.5, 1.5, 0.5), Component.literal("§eHit Buttons Here!"))
						tracer(spot.position.asVec3().add(.5, .5, .5), 0xFFAA00AA.toInt())
					}
					if (distance <= 15) for (button in spot.buttons) if (button !in pressed) block(button.asBlockPos(), TConfig.buttonColour.getEffectiveColourRGB())
				}
			}
			if (TConfig.berberis && holdingWand()) {
				val standingOnFarmland = Client.player?.let { Client.level?.getBlockState(it.blockPosition().below())?.`is`(Blocks.FARMLAND) } == true
				if (TConfig.onlyOnFarmland && !standingOnFarmland) return@drawInWorld
				var guiding = false
				if (TConfig.berberisSequence) for (seq in berberisFields().values) {
					if (!seq.isRendering) continue
					val target = seq.currentTarget ?: continue
					if (target.distanceIgnoreY(here) > 20) continue
					guiding = true
					block(target.asBlockPos(), 0x8000FF00.toInt())
					val after = seq.nextTarget ?: continue
					block(after.asBlockPos(), 0x40FFFF00)
					line(target.asVec3().add(.5, .5, .5), after.asVec3().add(.5, .5, .5), color = 0xFFFFFF00.toInt(), lineWidth = 3f)
				}
				if (!guiding) for (spark in sparkles.keys) if (spark.distance(here) < 20) block(spark.block().asBlockPos().below(), 0x60FFFF00)
			}
		}
	}
}

object PlusRiftEffigies {
	@Config
	object TConfig : ManagedConfig("rift-effigies", Category.RIFT) {
		val enabled by toggle("enabled") { false }
		val respawningSoon by toggle("respawning-soon") { true }
		val respawningSoonMinutes by integer("respawning-soon-minutes", 1, 15) { 3 }
		val unknownTime by toggle("unknown-time") { false }
	}

	enum class State { UNKNOWN, NOT_BROKEN, BROKEN }

	class Effigy {
		var state = State.UNKNOWN
		var respawnAt = 0L
	}

	private const val RESPAWN_MILLIS = 20 * 60_000L
	private val effigies = List(6) { Effigy() }
	private var spots: List<RVec>? = null

	private fun active() = TConfig.enabled && inTheRift() && PlusRiftParkour.inArea("Stillgore")
	private fun spots(): List<RVec> = spots ?: RiftRepo.loadEffigies().also { spots = it.ifEmpty { null } }

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		for (e in effigies) {
			e.state = State.UNKNOWN
			e.respawnAt = 0
		}
	}

	private fun readScoreboard() {
		val line = ScoreboardUtil.scoreboardLines.map { it.getLegacyFormatString() }.firstOrNull { it.startsWith("Effigies:") } ?: return
		val hearts = Effigies.parseHearts(line) ?: return
		for ((i, standing) in hearts.withIndex()) {
			val effigy = effigies.getOrNull(i) ?: continue
			val before = effigy.state
			effigy.state = if (standing) State.NOT_BROKEN else State.BROKEN
			if (before == State.BROKEN && standing) {
				Client.showChat(Component.literal("§eEffigy #${i + 1} respawned!"))
			} else if (before == State.NOT_BROKEN && !standing) {
				Client.showChat(Component.literal("§eEffigy #${i + 1} broken!"))
				effigy.respawnAt = System.currentTimeMillis() + RESPAWN_MILLIS
			}
		}
	}

	private fun readNameTags(known: List<RVec>) {
		val me = Client.player ?: return
		val stands = Client.level?.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(15.0)) ?: emptyList()
		for (stand in stands) {
			val name = stand.customName?.getLegacyFormatString() ?: continue
			val at = RVec(stand.x, stand.y, stand.z)
			val idx = known.indices.minByOrNull { known[it].distanceSq(at) } ?: continue
			val timer = Effigies.timerPattern.matchEntire(name)
			if (timer != null) {
				val left = Effigies.parseDuration(timer.groups["time"]!!.value)
				if (left != null) {
					effigies[idx].state = State.BROKEN
					effigies[idx].respawnAt = System.currentTimeMillis() + left
				}
			}
			if (name == Effigies.BREAK) effigies[idx].state = State.NOT_BROKEN
		}
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!active()) return
		readScoreboard()
		if (event.tickCount % 20 != 0) return
		val known = spots()
		if (known.isEmpty()) return
		readNameTags(known)
	}

	private fun look(effigy: Effigy, name: String, now: Long): Pair<Int, String>? = when (effigy.state) {
		State.NOT_BROKEN -> 0x80FF5555.toInt() to "§cBreak $name!"
		State.UNKNOWN -> if (TConfig.unknownTime) 0x80AAAAAA.toInt() to "§7Unknown State ($name)" else null
		State.BROKEN -> when {
			effigy.respawnAt == 0L -> if (TConfig.unknownTime) 0x80AAAAAA.toInt() to "§7Unknown Time ($name)" else null
			TConfig.respawningSoon && effigy.respawnAt - now < TConfig.respawningSoonMinutes * 60_000L ->
				0x80FFFF55.toInt() to "§e$name respawning in §b${Effigies.format(effigy.respawnAt - now)}"
			else -> null
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!active()) return
		val known = spots()
		val here = playerSpot() ?: return
		val now = System.currentTimeMillis()
		WorldDraw.drawInWorld(event) {
			for ((i, spot) in known.withIndex()) {
				val name = "Effigy #${i + 1}"
				val labelAt = spot.asVec3().add(.5, 1.5, .5)
				val shown = look(effigies[i], name, now)
				if (shown != null) {
					block(spot.asBlockPos(), shown.first)
					text(labelAt, Component.literal(shown.second))
				} else if (spot.distance(here) <= 15) {
					text(labelAt, Component.literal("§7$name"))
				}
			}
		}
	}
}

object PlusRiftEnigma {
	@Config
	object TConfig : ManagedConfig("rift-enigma-souls", Category.RIFT) {
		val enabled by toggle("enabled") { false }
		val colour by colour("colour") { ChromaColour.fromStaticRGB(170, 0, 170, 160) }
		val tracer by toggle("tracer") { true }
	}

	private var soulData: Map<String, Map<String, RVec>>? = null
	private val trackedByArea = HashMap<String, MutableSet<String>>()
	var trackingButtons = false
		private set

	private fun souls(): Map<String, Map<String, RVec>> = soulData ?: RiftRepo.loadEnigma().also { soulData = it.ifEmpty { null } }
	private fun active() = TConfig.enabled && inTheRift()
	private fun inSoulMenu() = Client.screenTitle?.let(EnigmaRules.inventory::matches) == true

	private fun menuArea(): String? {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return null
		val first = screen.menu.slots.getOrNull(39)?.item?.view()?.loreAccordingToNbt?.firstOrNull()?.bareText ?: return null
		return EnigmaRules.guideArea.matchEntire(first)?.groups?.get("area")?.value
	}

	private fun soulName(title: String) = EnigmaRules.title.matchEntire(title)?.groups?.get("name")?.value

	@Subscribe
	fun onClick(event: SlotClickEvent) {
		if (!active() || !inSoulMenu()) return
		val area = menuArea() ?: return
		val name = soulName(event.stack.hoverName.bareText) ?: return
		if (souls()[area]?.contains(name) != true) return
		if (name == "Buttons") trackingButtons = !trackingButtons
		val set = trackedByArea.getOrPut(area) { mutableSetOf() }
		if (set.add(name)) {
			Client.showChat(Component.literal("§5Tracking the $name Enigma Soul!"))
		} else {
			set.remove(name)
			Client.showChat(Component.literal("§5No longer tracking the $name Enigma Soul!"))
		}
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.Before) {
		if (!active() || !inSoulMenu()) return
		val area = menuArea() ?: return
		val name = soulName(event.slot.item.hoverName.bareText) ?: return
		if (trackedByArea[area]?.contains(name) != true) return
		val slot = event.slot
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80AA00AA.toInt())
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!active()) return
		if (!EnigmaRules.found.matches(event.bareText.trim())) return
		val here = playerSpot() ?: return
		val (area, name) = EnigmaRules.closest(souls(), here) ?: return
		if (trackedByArea[area]?.remove(name) != true) return
		Client.showChat(Component.literal("§5Found the $name Enigma Soul!"))
		if (name == "Buttons") trackingButtons = false
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!active() || trackedByArea.isEmpty()) return
		val colour = TConfig.colour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event, WaypointType.ENIGMA_SOULS) {
			for ((area, names) in trackedByArea) for (name in names) {
				val at = souls()[area]?.get(name) ?: continue
				block(at.asBlockPos(), colour)
				text(at.asVec3().add(.5, 1.5, .5), Component.literal("§5${name.removeSuffix(" Soul")} Soul"))
				if (TConfig.tracer) tracer(at.asVec3().add(.5, .5, .5), colour or 0xFF000000.toInt())
			}
		}
	}
}
