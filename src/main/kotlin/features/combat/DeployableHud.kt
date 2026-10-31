
package dev.ashtonite365.apex.features.combat

import org.joml.Vector2i
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.render.drawHudLines
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import kotlin.time.Duration.Companion.seconds

object DeployableHud {
	val identifier: String
		get() = "deployable-hud"

	@Config
	object TConfig : ManagedConfig(category = Category.COMBAT, name = identifier) {
		val enabled by toggle("enabled") { true }
		val showEffects by toggle("show-effects") { false }
		val showPotions by toggle("show-potions") { true }
		val maxPotionLines by integer("max-potion-lines", 1, 20) { 6 }
		val expiryWarningMinutes by integer("expiry-warning-minutes", 0, 60) { 2 }
		val hud by position("hud", 140, 40) { Vector2i(4, 340) }
		val miningLanterns by toggle("mining-lanterns") { true }
		val lanternRange by integer("lantern-range", 5, 60) { 20 }
		val lanternOutOfRangeAlert by toggle("lantern-out-of-range-alert") { true }
		val lanternExpiryAlert by integer("lantern-expiry-alert", 0, 120) { 10 }
	}

	private val deployables = listOf("Radiant", "Mana Flux", "Overflux", "Plasmaflux", "Warning Flare", "Alert Flare", "SOS Flare")
	private val nameRegex = "(${deployables.joinToString("|")})(?: Power Orb)? (\\d+)s".toRegex()
	private var active: Pair<String, Int>? = null

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		active = null
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		val player = Client.player ?: return
		val level = Client.level ?: return
		val found = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(40.0)) { it.hasCustomName() }
			.mapNotNull { stand ->
				val m = nameRegex.find(stand.customName?.bareText ?: return@mapNotNull null) ?: return@mapNotNull null
				val range = if (m.groupValues[1].endsWith("Flare")) 40.0 else 18.0
				if (stand.distanceToSqr(player) > range * range) return@mapNotNull null
				m.groupValues[1] to m.groupValues[2].toInt()
			}
		active = found.maxWithOrNull(compareBy({ deployables.indexOf(it.first) }, { it.second }))
		if (TConfig.miningLanterns) scanLanterns(player, level)
	}

	private val lanternRegex = Regex("^(.*?(?:Lantern|Lamp|Campfire).*?)\\s+(?:(?:(\\d+)m\\s*)?(\\d+)s|(\\d+):(\\d{2}))$")

	private data class Lantern(val name: String, val pos: net.minecraft.world.phys.Vec3, val expiresAt: Long)

	private var lantern: Lantern? = null
	private var outOfRange = false
	private var expiryWarned = false

	private fun scanLanterns(player: net.minecraft.world.entity.player.Player, level: net.minecraft.world.level.Level) {
		val seen = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(64.0)) { it.hasCustomName() }
			.mapNotNull { stand ->
				val m = lanternRegex.matchEntire(stand.customName?.bareText?.trim() ?: return@mapNotNull null) ?: return@mapNotNull null
				val g = m.groupValues
				val secs = if (g[5].isNotEmpty()) g[4].toInt() * 60 + g[5].toInt() else (g[2].toIntOrNull() ?: 0) * 60 + g[3].toInt()
				Lantern(g[1].trim(), stand.position(), System.currentTimeMillis() + secs * 1000L)
			}.minByOrNull { it.pos.distanceToSqr(player.position()) }
		val now = System.currentTimeMillis()
		if (seen != null) {
			if (lantern.let { it == null || kotlin.math.abs(it.expiresAt - seen.expiresAt) > 30_000 }) expiryWarned = false
			lantern = seen
		}
		val l = lantern ?: return
		if (l.expiresAt < now - 3_000) { lantern = null; outOfRange = false; return }
		val far = player.position().distanceTo(l.pos) > TConfig.lanternRange
		if (far && !outOfRange && TConfig.lanternOutOfRangeAlert)
			dev.ashtonite365.apex.util.Alerts.show(Component.literal("§e§lOut of lantern range!"), kotlin.time.Duration.parse("2s"), sound = true)
		outOfRange = far
		val left = (l.expiresAt - now) / 1000
		if (TConfig.lanternExpiryAlert > 0 && left in 0..TConfig.lanternExpiryAlert && !expiryWarned) {
			expiryWarned = true
			dev.ashtonite365.apex.util.Alerts.show(Component.literal("§6§l${l.name} expiring!"), kotlin.time.Duration.parse("2s"), sound = true)
		}
	}

	private fun lanternLine(): Component? {
		if (!TConfig.miningLanterns) return null
		val l = lantern ?: return null
		val left = ((l.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
		val range = if (outOfRange) " §c(out of range)" else ""
		return Component.literal("§e${l.name} §f${left / 60}:${"%02d".format(left % 60)}$range")
	}

	private val remainingRegex = Regex("Remaining: (?:(\\d+):)?(\\d+):(\\d+)")

	private val potionExpiry = mutableMapOf<String, Long>()
	private val warned = mutableSetOf<String>()

	@Subscribe
	fun onEffectsMenu(event: dev.ashtonite365.apex.events.ChestInventoryUpdateEvent) {
		if (!TConfig.showPotions) return
		if (Client.screenTitle?.startsWith("Active Effects") != true) return
		val menu = Client.containerScreen?.menu ?: return
		val now = System.currentTimeMillis()
		for (slot in menu.slots) {
			if (slot.container is net.minecraft.world.entity.player.Inventory) continue
			val stack = slot.item
			if (stack.isEmpty) continue
			val lore = stack.view().loreAccordingToNbt.map { it.bareText }
			val m = lore.firstNotNullOfOrNull { remainingRegex.find(it) } ?: continue
			val h = m.groupValues[1].toLongOrNull() ?: 0
			val secs = h * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()
			val name = stack.hoverName.bareText.trim()
			potionExpiry[name] = now + secs * 1000
			warned.remove(name)
		}
	}

	private fun formatRemaining(ms: Long): String {
		val s = ms / 1000
		return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
	}

	private fun potionLines(): List<Component> {
		val now = System.currentTimeMillis()
		potionExpiry.entries.removeIf { it.value <= now }
		val warnMs = TConfig.expiryWarningMinutes * 60_000L
		return potionExpiry.entries.sortedBy { it.value }.take(TConfig.maxPotionLines).map { (name, expiry) ->
			val left = expiry - now
			if (warnMs > 0 && left <= warnMs && warned.add(name))
				dev.ashtonite365.apex.util.Alerts.show(Component.literal("§c$name expires in ${formatRemaining(left)}"), 2.seconds)
			val colour = if (warnMs > 0 && left <= warnMs) "§c" else "§b"
			Component.literal("$colour$name §f${formatRemaining(left)}")
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		val lines = mutableListOf<Component>()
		active?.let { (name, secs) -> lines.add(Component.literal("§d$name §f${secs}s")) }
		lanternLine()?.let { lines.add(it) }
		if (TConfig.showPotions && SkyBlockSession.inSkyBlock) lines.addAll(potionLines())
		if (TConfig.showEffects) Client.player?.activeEffects?.forEach { eff ->
			lines.add(Component.literal("§7" + eff.effect.value().displayName.string + " §f" + (eff.duration / 20) + "s"))
		}
		if (lines.isNotEmpty()) event.drawHudLines(TConfig.hud, lines)
	}
}
