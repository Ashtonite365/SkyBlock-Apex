// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object PlusRiftMisc {
	@Config
	object TConfig : ManagedConfig("rift-misc", Category.RIFT) {
		val horsezookaHider by toggle("horsezooka-hider") { false }
		val shyCruxWarning by toggle("shy-crux-warning") { false }
		val splatterHearts by toggle("splatter-hearts") { false }
		val killZoneWarning by toggle("kill-zone-warning") { false }
		val ubikQuickClose by toggle("ubik-quick-close") { false }
	}

	fun inRift() = SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT

	private fun holdingHorsezooka() = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId == "HORSEZOOKA"

	fun shouldHideEntity(entity: Entity): Boolean {
		if (!TConfig.horsezookaHider || entity !is Horse) return false
		return inRift() && holdingHorsezooka()
	}

	val shyNames = setOf("I'm ugly! :(", "Eek!", "Don't look at me!", "Look away!")

	private fun shyCruxNearby(): Boolean? {
		val me = Client.player ?: return null
		val found = Client.level?.getEntities(me, me.boundingBox.inflate(8.0)) { it.name.string in shyNames } ?: return false
		return found.isNotEmpty()
	}

	private val killZonePattern by PlusPatterns.group("rift.colosseum.bacte").pattern("chat.kill-zone", "⚠ Get back in the arena or you will DIE(?<exclamation>!+) ⚠")
	private var deathAtMillis: Long? = null
	private var warnedAt = TimeMark.farPast()

	fun killMillis(exclamations: Int): Long = 250L * (12 - exclamations)

	fun killExclamations(msg: String): Int? = killZonePattern.matchMatcher(msg) { group("exclamation").length }

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!(TConfig.killZoneWarning && inRift())) return
		val bangs = killExclamations(event.bareText.trim()) ?: return
		warnedAt = TimeMark.now()
		deathAtMillis = System.currentTimeMillis() + killMillis(bangs)
	}

	private fun showKillCountdown() {
		val deadline = deathAtMillis ?: return
		if (!TConfig.killZoneWarning || warnedAt.passedTime() >= 250.milliseconds) return
		val secondsLeft = (deadline - System.currentTimeMillis()) / 1000.0
		if (secondsLeft <= 0) return
		Alerts.show(Component.literal("§cGet back in the arena! §7%.2fs left".format(java.util.Locale.US, secondsLeft)), 100.milliseconds, sound = false)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!inRift()) return
		if (TConfig.shyCruxWarning) {
			val shy = shyCruxNearby() ?: return
			if (shy) Alerts.show(Component.literal("§eLook away!"), 150.milliseconds, sound = false)
		}
		showKillCountdown()
	}

	private var lastHeartAt = TimeMark.farPast()
	private var collecting = mutableSetOf<Vec3>()
	private var lastBurst: Set<Vec3> = emptySet()

	private fun isSplatterHeart(event: ParticleSpawnEvent) =
		event.particleEffect.type == ParticleTypes.HEART && event.count == 3 && event.speed == 0f

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!(TConfig.splatterHearts && inRift()) || !isSplatterHeart(event)) return
		if (lastHeartAt.passedTime() > 50.milliseconds) {
			lastBurst = collecting.toSet()
			collecting = mutableSetOf()
		}
		lastHeartAt = TimeMark.now()
		collecting.add(event.position)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.splatterHearts) return
		if (lastHeartAt.passedTime() > 300.milliseconds || lastBurst.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for (at in lastBurst) box(AABB(at.x - 0.5, at.y + 0.3, at.z - 0.5, at.x + 0.5, at.y + 1.3, at.z + 0.5), 0x64FF5555)
		}
	}

	@Subscribe
	fun onSlotClick(event: SlotClickEvent) {
		if (!TConfig.ubikQuickClose || !inRift()) return
		if (Client.screenTitle != "Split or Steal") return
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		val decided = screen.menu.slots.getOrNull(4)?.item?.`is`(Items.CLOCK) == true
		if (!decided) Client.game.execute { screen.onClose() }
	}
}
