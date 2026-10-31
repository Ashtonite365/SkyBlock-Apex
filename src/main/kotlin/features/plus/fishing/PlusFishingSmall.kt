// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.findMatcher
import dev.ashtonite365.apex.util.render.WorldDraw
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items

object PlusFishingSmall {
	@Config
	object TConfig : ManagedConfig("fishing-extras", Category.FISHING) {
		val hideFlamingFlay by toggle("hide-flaming-flay") { false }
		val flayHideDistance by integer("flay-hide-distance", 0, 64) { 10 }
		val fishedItemName by toggle("fished-item-name") { false }
		val fishedItemNameBaits by toggle("fished-item-name-baits") { false }
		val cocoonWarning by toggle("cocoon-warning") { false }
		val cocoonMobs by string("cocoon-mobs") { "Lord Jawbus, Thunder, Plhlegblast, The Loch Emperor, Reindrake, Great White Shark, Grim Reaper, Phantom Fisher, Yeti" }
	}

	private val flayColours: Array<DoubleArray> = arrayOf(
		doubleArrayOf(1.0, 1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0),
		doubleArrayOf(0.23137255012989044, 0.05098039284348488, 0.0313725508749485),
		doubleArrayOf(0.003921568859368563, 0.003921568859368563, 0.003921568859368563),
	)
	private const val EPS = 1e-6

	private fun near(a: Double, b: Double) = kotlin.math.abs(a - b) < EPS

	fun isFlayParticle(dust: Boolean, ox: Double, oy: Double, oz: Double, count: Int, speed: Float): Boolean {
		if (!dust || count != 0 || speed != 1.0f) return false
		return flayColours.any { c -> near(ox, c[0]) && near(oy, c[1]) && near(oz, c[2]) }
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!TConfig.hideFlamingFlay || !SkyBlockSession.inSkyBlock) return
		val self = Client.player ?: return
		if (self.position().distanceTo(event.position) > TConfig.flayHideDistance) return
		val off = event.offset
		if (isFlayParticle(event.particleEffect is DustParticleOptions, off.x.toDouble(), off.y.toDouble(), off.z.toDouble(), event.count, event.speed))
			event.cancel()
	}

	fun isBaitName(name: String) = name.endsWith(" Bait")

	fun itemLabel(name: String, count: Int, bait: Boolean): String = buildString {
		append(if (bait) "§c§l- §r§7" else "§a§l+ §r")
		if (count != 1) append("§7x").append(count).append(" §r")
		append(name)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.fishedItemName || !SkyBlockSession.inSkyBlock || SkyBlockSession.skyblockIsland == SkyBlockIsland.END) return
		val self = Client.player ?: return
		if (self.mainHandItem.item != Items.FISHING_ROD) return
		val world = Client.level ?: return
		val nearby = world.entitiesForRendering().filterIsInstance<ItemEntity>().filter { it.distanceTo(self) < 15 }
		if (nearby.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			nearby.forEach { entity ->
				val stack = entity.item
				val name = stack.hoverName.string
				val bait = stack.count == 1 && isBaitName(name)
				if (!bait || TConfig.fishedItemNameBaits) text(entity.position().add(0.0, 0.8, 0.0), Component.literal(itemLabel(name, stack.count, bait)))
			}
		}
	}

	private val cocoonPattern by PlusPatterns.group("fishing.cocoon").pattern("spawn", "CAUGHT! You cocooned an? (?<name>[\\w ]+)!")

	fun cocoonedMob(message: String): String? = cocoonPattern.findMatcher(message) { group("name") }

	fun parseMobList(list: String): Set<String> {
		val out = LinkedHashSet<String>()
		for (part in list.split(',')) {
			val name = part.trim().lowercase()
			if (name.isNotEmpty()) out.add(name)
		}
		return out
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.cocoonWarning) return
		val mob = cocoonedMob(event.bareText) ?: return
		if (mob.lowercase() in parseMobList(TConfig.cocoonMobs)) Alerts.show(Component.literal("§c$mob Has Been Cocooned"))
	}
}
